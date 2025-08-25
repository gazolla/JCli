package com.gazapps.mcp;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gazapps.mcp.domain.Server;
import com.gazapps.mcp.domain.Tool;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpSchema.*;

/**
 * Serviço central para operações MCP que implementa as operações fundamentais
 * do protocolo incluindo conexão com servidores, execução de ferramentas,
 * monitoramento de saúde e gerenciamento de estados de conexão.
 * 
 * VERSÃO RESILIENTE: Continua funcionando mesmo se alguns servidores falharem.
 */
public class MCPService {
    
    private static final Logger logger = LoggerFactory.getLogger(MCPService.class);
    
    private final Map<String, Server> servers;
    private final Map<String, McpSyncClient> clients;
    private final MCPConfig config;
    
    // Configurações de timeout
    private int requestTimeoutSeconds = 15; // Timeout do JavaCLI que funcionava
    private int connectionTimeoutSeconds = 15; // Novo timeout separado para inicialização
    private static final int MAX_RETRY_ATTEMPTS = 2;
    
    public MCPService(MCPConfig config) {
        this.config = Objects.requireNonNull(config, "MCP config cannot be null");
        this.servers = new ConcurrentHashMap<>();
        this.clients = new ConcurrentHashMap<>();
        
        initializeServers();
    }
    
    /**
     * Conecta um servidor usando sua configuração.
     * RESILIENTE: Não falha se um servidor não conseguir conectar.
     */
    public boolean connectServer(MCPConfig.ServerConfig serverConfig) {
        Objects.requireNonNull(serverConfig, "Server config cannot be null");
        
        if (!serverConfig.enabled) {
            logger.info("Servidor '{}' está desabilitado, ignorando", serverConfig.id);
            return false;
        }
        
        // Verificar se o comando está disponível antes de tentar conectar
        if (!isCommandAvailable(serverConfig)) {
            logger.warn("Comando '{}' não disponível para servidor '{}' - pulando", 
                       getMainCommand(serverConfig.command), serverConfig.id);
            return false;
        }
        
        try {
            logger.info("Conectando ao servidor MCP '{}'...", serverConfig.id);
            
            Server server = serverConfig.toServer();
            McpSyncClient client = createMcpClient(serverConfig);
            
            // Inicializar conexão com timeout reduzido
            client.initialize();
            
            // Carregar ferramentas disponíveis
            loadServerTools(server, client);
            
            // Registrar servidor e cliente
            servers.put(server.getId(), server);
            clients.put(server.getId(), client);
            
            server.connect();
            logger.info("✅ Servidor '{}' conectado com sucesso! Ferramentas: {}", 
                       server.getId(), server.getTools().size());
            
            return true;
            
        } catch (Exception e) {
            logger.warn("❌ Falha ao conectar servidor '{}': {} - continuando sem este servidor", 
                       serverConfig.id, e.getMessage());
            return false;
        }
    }
    
    /**
     * Verifica se um comando está disponível no sistema.
     * USA A MESMA ABORDAGEM DO JavaCLI que funcionava.
     */
    private boolean isCommandAvailable(MCPConfig.ServerConfig serverConfig) {
        String command = serverConfig.command;
        
        try {
            // Tentar executar o comando com --version ou --help para verificar se existe
            ProcessBuilder pb = new ProcessBuilder();
            
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                // No Windows, usar cmd.exe /c igual na conexão real
                String[] parts = command.split("\\s+", 2);
                String mainCmd = parts[0];
                
                if ("npx".equals(mainCmd)) {
                    pb.command("cmd.exe", "/c", "npx", "--version");
                } else if ("uvx".equals(mainCmd)) {
                    pb.command("cmd.exe", "/c", "uvx", "--version");
                } else {
                    pb.command("cmd.exe", "/c", mainCmd, "--version");
                }
            } else {
                String[] parts = command.split("\\s+");
                pb.command(parts[0], "--version");
            }
            
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // Timeout curto para verificação
            boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                logger.debug("Comando '{}' não respondeu em tempo hábil", command);
                return true; // Assumir que existe e tentar conectar
            }
            
            boolean available = process.exitValue() == 0;
            logger.debug("Verificação do comando '{}': {}", command, available ? "disponível" : "não encontrado");
            
            return available;
            
        } catch (Exception e) {
            logger.debug("Erro ao verificar comando '{}': {} - assumindo disponível", command, e.getMessage());
            return true; // Em caso de erro, tentar conectar mesmo assim
        }
    }
    
    /**
     * Extrai o comando principal de uma linha de comando.
     */
    private String getMainCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return "";
        }
        return command.trim().split("\\s+")[0];
    }
    
    /**
     * Desconecta um servidor.
     */
    public void disconnectServer(String serverId) {
        Objects.requireNonNull(serverId, "Server ID cannot be null");
        
        try {
            McpSyncClient client = clients.remove(serverId);
            if (client != null) {
                client.close();
            }
            
            Server server = servers.get(serverId);
            if (server != null) {
                server.disconnect();
                logger.info("Servidor '{}' desconectado", serverId);
            }
            
        } catch (Exception e) {
            logger.error("Erro ao desconectar servidor '{}'", serverId, e);
        }
    }
    
    /**
     * Executa uma ferramenta em um servidor específico.
     */
    public ToolExecutionResult callTool(String serverId, String toolName, Map<String, Object> args) {
        Objects.requireNonNull(serverId, "Server ID cannot be null");
        Objects.requireNonNull(toolName, "Tool name cannot be null");
        
        if (args == null) {
            args = Collections.emptyMap();
        }
        
        Server server = servers.get(serverId);
        if (server == null) {
            return ToolExecutionResult.error("Servidor não encontrado: " + serverId);
        }
        
        if (!server.isConnected()) {
            return ToolExecutionResult.error("Servidor não está conectado: " + serverId);
        }
        
        Tool tool = server.getTool(toolName);
        if (tool == null) {
            return ToolExecutionResult.error("Ferramenta não encontrada: " + toolName + " no servidor " + serverId);
        }
        
        // Validar argumentos
        if (!tool.validateArgs(args)) {
            List<String> missingParams = tool.getMissingRequiredParams(args);
            return ToolExecutionResult.error("Argumentos inválidos para ferramenta " + toolName + 
                                 ". Parâmetros obrigatórios faltando: " + missingParams);
        }
        
        // Normalizar argumentos
        Map<String, Object> normalizedArgs = tool.normalizeArgs(args);
        
        return executeToolWithRetry(server, tool, normalizedArgs);
    }
    
    /**
     * Retorna todas as ferramentas disponíveis em um servidor.
     */
    public List<Tool> getAvailableTools(String serverId) {
        Objects.requireNonNull(serverId, "Server ID cannot be null");
        
        Server server = servers.get(serverId);
        if (server == null) {
            return Collections.emptyList();
        }
        
        return server.getTools();
    }
    
    /**
     * Retorna todas as ferramentas disponíveis em todos os servidores conectados.
     */
    public List<Tool> getAllAvailableTools() {
        List<Tool> allTools = new ArrayList<>();
        
        for (Server server : servers.values()) {
            if (server.isConnected()) {
                allTools.addAll(server.getTools());
            }
        }
        
        return allTools;
    }
    
    /**
     * Verifica se um servidor está conectado.
     */
    public boolean isServerConnected(String serverId) {
        Server server = servers.get(serverId);
        return server != null && server.isConnected();
    }
    
    /**
     * Retorna informações sobre um servidor.
     */
    public Server getServerInfo(String serverId) {
        return servers.get(serverId);
    }
    
    /**
     * Retorna mapa com todos os servidores conectados.
     */
    public Map<String, Server> getConnectedServers() {
        Map<String, Server> connected = new HashMap<>();
        
        for (Map.Entry<String, Server> entry : servers.entrySet()) {
            if (entry.getValue().isConnected()) {
                connected.put(entry.getKey(), entry.getValue());
            }
        }
        
        return connected;
    }
    
    /**
     * Retorna mapa com todos os servidores (conectados e desconectados).
     */
    public Map<String, Server> getAllServers() {
        return new HashMap<>(servers);
    }
    
    /**
     * Valida se uma chamada de ferramenta é válida sem executá-la.
     */
    public boolean validateToolCall(String toolName, Map<String, Object> args) {
        if (toolName == null || toolName.trim().isEmpty()) {
            return false;
        }
        
        // Procurar a ferramenta em todos os servidores
        for (Server server : servers.values()) {
            if (server.isConnected() && server.hasTool(toolName)) {
                Tool tool = server.getTool(toolName);
                return tool.validateArgs(args);
            }
        }
        
        return false;
    }
    
    /**
     * Atualiza conexões com todos os servidores.
     */
    public void refreshServerConnections() {
        logger.info("Atualizando conexões de servidores...");
        
        Map<String, MCPConfig.ServerConfig> serverConfigs = config.loadServers();
        
        // Conectar novos servidores
        for (MCPConfig.ServerConfig serverConfig : serverConfigs.values()) {
            if (!servers.containsKey(serverConfig.id)) {
                connectServer(serverConfig);
            }
        }
        
        // Verificar saúde dos servidores existentes
        List<String> unhealthyServers = new ArrayList<>();
        for (Map.Entry<String, Server> entry : servers.entrySet()) {
            String serverId = entry.getKey();
            Server server = entry.getValue();
            
            if (!server.isHealthy()) {
                unhealthyServers.add(serverId);
            } else {
                // Atualizar heartbeat
                server.updateHeartbeat();
            }
        }
        
        // Tentar reconectar servidores não saudáveis (opcional, pode ser desabilitado)
        int reconnectAttempts = 0;
        for (String serverId : unhealthyServers) {
            if (reconnectAttempts >= 2) { // Limitar tentativas de reconexão
                logger.debug("Limite de tentativas de reconexão atingido");
                break;
            }
            
            MCPConfig.ServerConfig serverConfig = serverConfigs.get(serverId);
            if (serverConfig != null) {
                logger.debug("Tentando reconectar servidor não saudável: {}", serverId);
                disconnectServer(serverId);
                if (connectServer(serverConfig)) {
                    reconnectAttempts++;
                }
            }
        }
        
        int connectedCount = getConnectedServers().size();
        int totalCount = serverConfigs.size();
        logger.info("Atualização concluída. Servidores: {}/{} conectados", connectedCount, totalCount);
    }
    
    /**
     * Fecha todas as conexões e limpa recursos.
     */
    public void close() {
        logger.info("Fechando MCPService...");
        
        for (String serverId : new ArrayList<>(servers.keySet())) {
            disconnectServer(serverId);
        }
        
        servers.clear();
        clients.clear();
        
        logger.info("MCPService fechado");
    }
    
    // Métodos privados auxiliares
    
    private void initializeServers() {
        Map<String, MCPConfig.ServerConfig> serverConfigs = config.loadServers();
        
        logger.info("Inicializando {} servidores MCP...", serverConfigs.size());
        
        int successCount = 0;
        for (MCPConfig.ServerConfig serverConfig : serverConfigs.values()) {
            if (serverConfig.enabled) {
                if (connectServer(serverConfig)) {
                    successCount++;
                }
            } else {
                logger.info("Servidor '{}' desabilitado na configuração", serverConfig.id);
            }
        }
        
        logger.info("MCPService inicializado: {}/{} servidores conectados com sucesso", 
                   successCount, serverConfigs.size());
        
        if (successCount == 0) {
            logger.warn("⚠️  AVISO: Nenhum servidor MCP conectado!");
            logger.warn("   Isso pode acontecer se:");
            logger.warn("   - Node.js não estiver instalado (para servidores weather/filesystem)");
            logger.warn("   - Python/uvx não estiver instalado (para servidor time)");
            logger.warn("   - Os comandos não estiverem no PATH do sistema");
            logger.warn("   O sistema continuará funcionando com funcionalidade limitada.");
        }
    }
    
    private McpSyncClient createMcpClient(MCPConfig.ServerConfig serverConfig) throws Exception {
        try {
            // USAR A MESMA ABORDAGEM QUE FUNCIONAVA NO JavaCLI
            String[] command;
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                // No Windows, usar cmd.exe /c para executar o comando completo
                command = new String[]{"cmd.exe", "/c", serverConfig.command};
            } else {
                // No Unix/Linux, fazer split do comando
                command = serverConfig.command.split(" ");
            }
            
            logger.debug("Executando comando: {}", Arrays.toString(command));
            
            // Criar parâmetros do servidor
            ServerParameters serverParams = ServerParameters.builder(command[0])
                .args(Arrays.copyOfRange(command, 1, command.length))
                .env(serverConfig.env)
                .build();
            
            // Criar transport STDIO
            StdioClientTransport stdioTransport = new StdioClientTransport(serverParams);
            
            // Criar cliente com timeout adequado
            return McpClient.sync(stdioTransport)
                .requestTimeout(Duration.ofSeconds(requestTimeoutSeconds))
                .build();
                
        } catch (Exception e) {
            logger.error("Erro ao criar cliente MCP para servidor '{}'", serverConfig.id, e);
            throw new Exception("Erro ao criar cliente MCP: " + e.getMessage(), e);
        }
    }
    
    private String[] parseCommand(String command) {
        // Parse simples do comando - pode ser melhorado para suportar aspas, etc.
        return command.split("\\s+");
    }
    
    private void loadServerTools(Server server, McpSyncClient client) {
        try {
            // Solicitar lista de ferramentas do servidor
            ListToolsResult toolsResult = client.listTools();
            
            // Converter e adicionar ferramentas ao servidor
            for (io.modelcontextprotocol.spec.McpSchema.Tool mcpTool : toolsResult.tools()) {
                Tool tool = Tool.fromMcp(mcpTool, server.getId());
                server.addTool(tool);
            }
            
            logger.debug("Carregadas {} ferramentas para servidor '{}'", 
                        toolsResult.tools().size(), server.getId());
            
        } catch (Exception e) {
            logger.error("Erro ao carregar ferramentas do servidor '{}'", server.getId(), e);
        }
    }
    
    private ToolExecutionResult executeToolWithRetry(Server server, Tool tool, Map<String, Object> args) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return executeToolDirect(server, tool, args);
                
            } catch (Exception e) {
                lastException = e;
                logger.warn("Tentativa {} de {} falhou para ferramenta '{}': {}", 
                           attempt, MAX_RETRY_ATTEMPTS, tool.getName(), e.getMessage());
                
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(1000 * attempt); // Backoff exponencial simples
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        return ToolExecutionResult.error("Falha ao executar ferramenta '" + tool.getName() + 
                             "' após " + MAX_RETRY_ATTEMPTS + " tentativas", lastException);
    }
    
    private ToolExecutionResult executeToolDirect(Server server, Tool tool, Map<String, Object> args) throws Exception {
        McpSyncClient client = clients.get(server.getId());
        if (client == null) {
            throw new Exception("Cliente não encontrado para servidor: " + server.getId());
        }
        
        // Criar requisição de chamada de ferramenta
        CallToolRequest request = new CallToolRequest(tool.getName(), args);
        
        // Executar chamada
        CallToolResult result = client.callTool(request);
        
        // Verificar se houve erro
        Boolean isError = result.isError();
        if (isError != null && isError) {
            throw new Exception("Erro na execução da ferramenta: " + result.toString());
        }
        
        // Extrair conteúdo da resposta
        String content = extractContentAsString(result.content());
        
        // Atualizar métricas do servidor
        server.updateHeartbeat();
        
        return ToolExecutionResult.success(tool, content);
    }
    
    private String extractContentAsString(List<Content> contentList) {
        if (contentList == null || contentList.isEmpty()) {
            return "Sem mensagem de retorno";
        }

        for (Content content : contentList) {
            if (content instanceof TextContent textContent) {
                if (textContent.text() != null && !textContent.text().trim().isEmpty()) {
                    return textContent.text();
                }
            }
        }

        return "Nenhuma mensagem encontrada";
    }
    
    /**
     * Retorna estatísticas do sistema MCP.
     */
    public MCPStats getStats() {
        int totalServers = servers.size();
        int connectedServers = (int) servers.values().stream().filter(Server::isConnected).count();
        int totalTools = getAllAvailableTools().size();
        
        Map<String, Integer> toolsByDomain = new HashMap<>();
        for (Tool tool : getAllAvailableTools()) {
            toolsByDomain.merge(tool.getDomain(), 1, Integer::sum);
        }
        
        return new MCPStats(totalServers, connectedServers, totalTools, toolsByDomain);
    }
    
    /**
     * Estatísticas do sistema MCP.
     */
    public static class MCPStats {
        public final int totalServers;
        public final int connectedServers;
        public final int totalTools;
        public final Map<String, Integer> toolsByDomain;
        
        public MCPStats(int totalServers, int connectedServers, int totalTools, Map<String, Integer> toolsByDomain) {
            this.totalServers = totalServers;
            this.connectedServers = connectedServers;
            this.totalTools = totalTools;
            this.toolsByDomain = Map.copyOf(toolsByDomain);
        }
        
        @Override
        public String toString() {
            return String.format("MCPStats{servers: %d/%d, tools: %d, domains: %d}", 
                               connectedServers, totalServers, totalTools, toolsByDomain.size());
        }
    }
    
    /**
     * Resultado da execução de uma ferramenta.
     */
    public static class ToolExecutionResult {
        public final boolean success;
        public final Tool tool;
        public final String content;
        public final String message;
        public final Exception error;
        
        private ToolExecutionResult(boolean success, Tool tool, String content, String message, Exception error) {
            this.success = success;
            this.tool = tool;
            this.content = content;
            this.message = message;
            this.error = error;
        }
        
        public static ToolExecutionResult success(Tool tool, String content) {
            return new ToolExecutionResult(true, tool, content, "Ferramenta executada com sucesso", null);
        }
        
        public static ToolExecutionResult error(String message) {
            return new ToolExecutionResult(false, null, null, message, null);
        }
        
        public static ToolExecutionResult error(String message, Exception error) {
            return new ToolExecutionResult(false, null, null, message, error);
        }
        
        @Override
        public String toString() {
            if (success) {
                return String.format("ToolExecutionResult{success=true, tool='%s', content='%s'}", 
                                   tool != null ? tool.getName() : "unknown", 
                                   content != null ? (content.length() > 100 ? content.substring(0, 100) + "..." : content) : "empty");
            } else {
                return String.format("ToolExecutionResult{success=false, message='%s'}", message);
            }
        }
    }
}
