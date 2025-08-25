package com.gazapps;

import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gazapps.mcp.MCPManager;
import com.gazapps.mcp.MCPService;
import com.gazapps.mcp.domain.Server;
import com.gazapps.mcp.domain.Tool;

/**
 * Aplicação principal que demonstra o uso do sistema MCP.
 * Esta versão implementa a Etapa 1 - Core Foundation.
 */
public class App {
    
    private static final Logger logger = LoggerFactory.getLogger(App.class);
    
    public static void main(String[] args) {
        logger.info("=== Iniciando Sistema MCP - Etapa 1: Core Foundation ===");
        
        // Usar diretório de configuração local
        String configDirectory = "./config";
        
        try (MCPManager mcpManager = new MCPManager(configDirectory)) {
            
            // Verificar saúde do sistema
            if (!mcpManager.isHealthy()) {
                logger.warn("Sistema MCP não está completamente saudável");
            }
            
            // Exibir informações dos servidores conectados
            displayServerInfo(mcpManager);
            
            // Exibir domínios disponíveis
            displayDomainInfo(mcpManager);
            
            // Demonstrar funcionalidades básicas
            demonstrateBasicFunctionality(mcpManager);
            
            // Modo interativo (opcional)
            if (args.length > 0 && "interactive".equals(args[0])) {
                runInteractiveMode(mcpManager);
            }
            
        } catch (Exception e) {
            logger.error("Erro na aplicação principal", e);
            System.exit(1);
        }
        
        logger.info("=== Sistema MCP finalizado ===");
    }
    
    private static void displayServerInfo(MCPManager mcpManager) {
        logger.info("--- Informações dos Servidores MCP ---");
        
        // Obter estatísticas
        MCPService.MCPStats stats = mcpManager.getMcpService().getStats();
        logger.info("📊 Estatísticas: {}", stats);
        
        // Listar todos os servidores (conectados e não conectados)
        Map<String, Server> allServers = mcpManager.getMcpService().getAllServers();
        Map<String, Server> connectedServers = mcpManager.getConnectedServers().stream()
            .collect(java.util.stream.Collectors.toMap(Server::getId, s -> s));
        
        if (allServers.isEmpty()) {
            logger.info("ℹ️  Nenhum servidor configurado");
            return;
        }
        
        for (Server server : allServers.values()) {
            String status = server.isConnected() ? "✅ CONECTADO" : "❌ DESCONECTADO";
            logger.info("{} - {} ({})", status, server.getName(), server.getId());
            logger.info("   Descrição: {}", server.getDescription());
            logger.info("   Comando: {}", server.getCommand());
            
            if (server.isConnected()) {
                logger.info("   Saudável: {}", server.isHealthy());
                logger.info("   Ferramentas: {}", server.getTools().size());
                logger.info("   Domínio: {}", server.getDomain());
                
                // Listar ferramentas do servidor
                for (Tool tool : server.getTools()) {
                    logger.info("     - {} ({}): {}", 
                               tool.getName(), tool.getDomain(), tool.getDescription());
                }
            } else {
                logger.info("   ⚠️  Servidor não conectado - possíveis causas:");
                String cmd = server.getCommand().split("\\s+")[0];
                if ("npx".equals(cmd)) {
                    logger.info("     • Node.js/npm não instalado ou não está no PATH");
                } else if ("uvx".equals(cmd)) {
                    logger.info("     • Python/uvx não instalado ou não está no PATH");
                } else {
                    logger.info("     • Comando '{}' não encontrado no sistema", cmd);
                }
            }
            logger.info("");
        }
        
        if (connectedServers.isEmpty()) {
            logger.warn("⚠️  NENHUM SERVIDOR CONECTADO!");
            logger.warn("   Para conectar servidores MCP, instale as dependências:");
            logger.warn("   • Node.js: https://nodejs.org/");
            logger.warn("   • Python + uvx: pip install uvx");
            logger.warn("   O sistema continuará funcionando com funcionalidade limitada.");
        }
    }
    
    private static void displayDomainInfo(MCPManager mcpManager) {
        logger.info("--- Informações dos Domínios ---");
        
        var domains = mcpManager.getAvailableDomains();
        
        for (String domain : domains) {
            List<Tool> domainTools = mcpManager.getToolsByDomain(domain);
            logger.info("Domínio '{}': {} ferramentas", domain, domainTools.size());
            
            for (Tool tool : domainTools) {
                logger.info("  - {}: {}", tool.getName(), tool.getDescription());
            }
        }
        
        if (domains.isEmpty()) {
            logger.info("Nenhum domínio descoberto");
        }
    }
    
    private static void demonstrateBasicFunctionality(MCPManager mcpManager) {
        logger.info("--- Demonstração de Funcionalidades Básicas ---");
        
        // Teste 1: Buscar ferramentas por query
        testToolSearch(mcpManager, "weather");
        testToolSearch(mcpManager, "file");
        testToolSearch(mcpManager, "time");
        
        // Teste 2: Matching com opções customizadas
        testCustomMatching(mcpManager);
        
        // Teste 3: Validação de chamadas de ferramentas
        testToolValidation(mcpManager);
    }
    
    private static void testToolSearch(MCPManager mcpManager, String query) {
        logger.info("Buscando ferramentas para: '{}'", query);
        
        List<Tool> tools = mcpManager.findTools(query);
        
        if (tools.isEmpty()) {
            logger.info("  Nenhuma ferramenta encontrada");
        } else {
            logger.info("  Encontradas {} ferramentas:", tools.size());
            for (Tool tool : tools) {
                logger.info("    - {} (servidor: {}, domínio: {})", 
                           tool.getName(), tool.getServerId(), tool.getDomain());
            }
        }
        logger.info("");
    }
    
    private static void testCustomMatching(MCPManager mcpManager) {
        logger.info("Testando matching customizado...");
        
        MCPManager.MatchingOptions options = MCPManager.MatchingOptions.builder()
                .maxResults(3)
                .confidenceThreshold(0.1)
                .build();
        
        List<Tool> tools = mcpManager.findTools("weather forecast", options);
        logger.info("Matching customizado encontrou {} ferramentas (máx: 3)", tools.size());
        
        for (Tool tool : tools) {
            logger.info("  - {}: {}", tool.getName(), tool.getDescription());
        }
        logger.info("");
    }
    
    private static void testToolValidation(MCPManager mcpManager) {
        logger.info("Testando validação de ferramentas...");
        
        // Acessar o serviço MCP para validação
        MCPService mcpService = mcpManager.getMcpService();
        
        // Tentar validar uma ferramenta inexistente
        boolean valid1 = mcpService.validateToolCall("nonexistent_tool", Map.of());
        logger.info("Ferramenta inexistente é válida: {}", valid1);
        
        // Tentar encontrar uma ferramenta real para validar
        List<Tool> allTools = mcpService.getAllAvailableTools();
        if (!allTools.isEmpty()) {
            Tool firstTool = allTools.get(0);
            logger.info("Testando validação da ferramenta: {}", firstTool.getName());
            
            // Testar com argumentos vazios
            boolean valid2 = mcpService.validateToolCall(firstTool.getName(), Map.of());
            logger.info("Ferramenta '{}' com args vazios é válida: {}", firstTool.getName(), valid2);
            
            // Mostrar parâmetros da ferramenta
            logger.info("Parâmetros obrigatórios: {}", firstTool.getRequiredParams());
            logger.info("Parâmetros opcionais: {}", firstTool.getOptionalParams());
        }
        logger.info("");
    }
    
    private static void runInteractiveMode(MCPManager mcpManager) {
        logger.info("--- Modo Interativo ---");
        logger.info("Digite 'help' para ver comandos disponíveis, 'quit' para sair");
        
        Scanner scanner = new Scanner(System.in);
        
        while (true) {
            System.out.print("MCP> ");
            String input = scanner.nextLine().trim();
            
            if (input.isEmpty()) continue;
            
            String[] parts = input.split("\\s+", 2);
            String command = parts[0].toLowerCase();
            
            try {
                switch (command) {
                    case "quit":
                    case "exit":
                        return;
                        
                    case "help":
                        printHelp();
                        break;
                        
                    case "debug":
                        if (parts.length > 1) {
                            String serverId = parts[1];
                            debugServer(mcpManager, serverId);
                        } else {
                            System.out.println("🔍 Debug do Sistema MCP:");
                            System.out.println("OS: " + System.getProperty("os.name"));
                            System.out.println("Java: " + System.getProperty("java.version"));
                            System.out.println("PATH: " + System.getenv("PATH"));
                            System.out.println("");
                            System.out.println("Para debug de servidor específico: debug <server_id>");
                        }
                        break;
                        
                    case "servers":
                        displayServerInfo(mcpManager);
                        break;
                        
                    case "domains":
                        displayDomainInfo(mcpManager);
                        break;
                        
                    case "search":
                        if (parts.length > 1) {
                            testToolSearch(mcpManager, parts[1]);
                        } else {
                            System.out.println("Uso: search <query>");
                        }
                        break;
                        
                    case "tools":
                        if (parts.length > 1) {
                            List<Tool> tools = mcpManager.getToolsByDomain(parts[1]);
                            System.out.println("Ferramentas no domínio '" + parts[1] + "':");
                            for (Tool tool : tools) {
                                System.out.println("  - " + tool.getName() + ": " + tool.getDescription());
                            }
                        } else {
                            // Listar todas as ferramentas usando o serviço MCP
                            List<Tool> allTools = mcpManager.getMcpService().getAllAvailableTools();
                            System.out.println("Todas as ferramentas (" + allTools.size() + "):");
                            for (Tool tool : allTools) {
                                System.out.println("  - " + tool.getName() + " (" + tool.getDomain() + ")");
                            }
                        }
                        break;
                        
                    case "stats":
                        MCPService.MCPStats stats = mcpManager.getMcpService().getStats();
                        System.out.println("📊 Estatísticas do Sistema MCP:");
                        System.out.println("  Servidores: " + stats.connectedServers + "/" + stats.totalServers + " conectados");
                        System.out.println("  Ferramentas: " + stats.totalTools + " disponíveis");
                        System.out.println("  Domínios: " + stats.toolsByDomain.size());
                        if (!stats.toolsByDomain.isEmpty()) {
                            System.out.println("  Ferramentas por domínio:");
                            stats.toolsByDomain.entrySet().stream()
                                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                                .forEach(entry -> System.out.println("    - " + entry.getKey() + ": " + entry.getValue()));
                        }
                        break;
                        
                    case "install":
                        System.out.println("🛠️  Guia de Instalação de Dependências:");
                        System.out.println("");
                        System.out.println("Para weather e filesystem (requer Node.js):");
                        System.out.println("  1. Baixar Node.js: https://nodejs.org/");
                        System.out.println("  2. Verificar: node --version && npm --version");
                        System.out.println("");
                        System.out.println("Para time (requer Python/uvx):");
                        System.out.println("  1. Instalar Python: https://python.org/");
                        System.out.println("  2. Instalar uvx: pip install uvx");
                        System.out.println("  3. Verificar: uvx --version");
                        System.out.println("");
                        System.out.println("Após instalar, execute 'refresh' para reconectar.");
                        break;
                        
                    case "test":
                        if (parts.length > 1) {
                            String toolName = parts[1];
                            System.out.println("Testando ferramenta: " + toolName);
                            var result = mcpManager.executeTool(toolName, Map.of());
                            if (result.success) {
                                System.out.println("✅ Sucesso: " + result.content);
                            } else {
                                System.out.println("❌ Erro: " + result.message);
                            }
                        } else {
                            System.out.println("Uso: test <nome_ferramenta>");
                        }
                        break;
                        
                    case "params":
                        if (parts.length > 1) {
                            String toolName = parts[1];
                            showToolParams(mcpManager, toolName);
                        } else {
                            System.out.println("Uso: params <nome_ferramenta>");
                        }
                        break;
                        
                    case "health":
                        System.out.println("Sistema saudável: " + mcpManager.isHealthy());
                        break;
                        
                    case "refresh":
                        mcpManager.refreshDomains();
                        System.out.println("Domínios atualizados");
                        break;
                        
                    default:
                        System.out.println("Comando desconhecido: " + command + ". Digite 'help' para ajuda.");
                }
                
            } catch (Exception e) {
                System.out.println("Erro ao executar comando: " + e.getMessage());
                logger.debug("Erro detalhado", e);
            }
        }
    }
    
    private static void printHelp() {
        System.out.println("🚀 Comandos disponíveis:");
        System.out.println("  help          - Mostra esta ajuda");
        System.out.println("  servers       - Lista servidores MCP (conectados e desconectados)");
        System.out.println("  stats         - Mostra estatísticas detalhadas do sistema");
        System.out.println("  domains       - Lista domínios disponíveis");
        System.out.println("  search <query> - Busca ferramentas por nome/descrição");
        System.out.println("  tools [domain] - Lista todas as ferramentas (ou de um domínio)");
        System.out.println("  params <tool> - Mostra parâmetros de uma ferramenta");
        System.out.println("  test <tool>   - Testa execução de uma ferramenta");
        System.out.println("  debug [server] - Diagnóstico detalhado do sistema/servidor");
        System.out.println("  health        - Verifica saúde do sistema");
        System.out.println("  refresh       - Atualiza conexões e domínios");
        System.out.println("  install       - Guia de instalação de dependências");
        System.out.println("  quit/exit     - Sai do programa");
        System.out.println("");
        System.out.println("💡 Dicas:");
        System.out.println("  - Execute 'stats' para ver o status geral");
        System.out.println("  - Use 'install' se nenhum servidor estiver conectado");
        System.out.println("  - Teste ferramentas com 'test <nome_ferramenta>'");
        System.out.println("  - Use 'debug' para diagnóstico detalhado");
    }
    
    private static void debugServer(MCPManager mcpManager, String serverId) {
        System.out.println("🔍 Debug do Servidor: " + serverId);
        
        MCPService mcpService = mcpManager.getMcpService();
        Server server = mcpService.getServerInfo(serverId);
        
        if (server == null) {
            System.out.println("❌ Servidor não encontrado: " + serverId);
            return;
        }
        
        System.out.println("Nome: " + server.getName());
        System.out.println("Comando: " + server.getCommand());
        System.out.println("Conectado: " + server.isConnected());
        System.out.println("Saudável: " + server.isHealthy());
        System.out.println("Argumentos: " + server.getArgs());
        System.out.println("Variáveis de ambiente: " + server.getEnv());
        
        // Tentar verificar se o comando existe
        String[] cmdParts = server.getCommand().split("\\s+");
        String mainCmd = cmdParts[0];
        
        System.out.println("");
        System.out.println("Diagnóstico do comando '" + mainCmd + "':");
        
        try {
            ProcessBuilder pb = new ProcessBuilder();
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                pb.command("where", mainCmd);
            } else {
                pb.command("which", mainCmd);
            }
            
            Process process = pb.start();
            boolean found = process.waitFor() == 0;
            
            if (found) {
                System.out.println("✅ Comando encontrado no PATH");
            } else {
                System.out.println("❌ Comando NÃO encontrado no PATH");
                
                if ("npx".equals(mainCmd)) {
                    System.out.println("Dicas para npx:");
                    System.out.println("  - Instalar Node.js: https://nodejs.org/");
                    System.out.println("  - Verificar: node --version && npm --version");
                    System.out.println("  - Reiniciar terminal após instalar");
                } else if ("uvx".equals(mainCmd)) {
                    System.out.println("Dicas para uvx:");
                    System.out.println("  - Instalar: pip install uvx");
                    System.out.println("  - Verificar: uvx --version");
                }
            }
            
        } catch (Exception e) {
            System.out.println("⚠️ Erro ao verificar comando: " + e.getMessage());
        }
        
        // Tentar executar o comando manualmente
        System.out.println("");
        System.out.println("Para testar manualmente:");
        System.out.println("  " + server.getCommand());
    }
    
    private static void showToolParams(MCPManager mcpManager, String toolName) {
        System.out.println("🛠️ Parâmetros da Ferramenta: " + toolName);
        
        // Encontrar a ferramenta
        Tool tool = null;
        MCPService mcpService = mcpManager.getMcpService();
        
        for (Tool t : mcpService.getAllAvailableTools()) {
            if (toolName.equals(t.getName())) {
                tool = t;
                break;
            }
        }
        
        if (tool == null) {
            System.out.println("❌ Ferramenta não encontrada: " + toolName);
            return;
        }
        
        System.out.println("Nome: " + tool.getName());
        System.out.println("Descrição: " + tool.getDescription());
        System.out.println("Servidor: " + tool.getServerId());
        System.out.println("Domínio: " + tool.getDomain());
        System.out.println("");
        
        System.out.println("Parâmetros Obrigatórios (" + tool.getRequiredParams().size() + "):");
        if (tool.getRequiredParams().isEmpty()) {
            System.out.println("  Nenhum parâmetro obrigatório");
        } else {
            for (String param : tool.getRequiredParams()) {
                System.out.println("  • " + param + " (" + tool.getParamType(param) + "): " + tool.getParamDescription(param));
            }
        }
        
        System.out.println("");
        System.out.println("Parâmetros Opcionais (" + tool.getOptionalParams().size() + "):");
        if (tool.getOptionalParams().isEmpty()) {
            System.out.println("  Nenhum parâmetro opcional");
        } else {
            for (String param : tool.getOptionalParams()) {
                Object defaultValue = tool.getParamDefault(param);
                String defaultStr = defaultValue != null ? " [padrão: " + defaultValue + "]" : "";
                System.out.println("  • " + param + " (" + tool.getParamType(param) + "): " + tool.getParamDescription(param) + defaultStr);
            }
        }
        
        System.out.println("");
        System.out.println("Schema completo:");
        System.out.println(tool.getSchema());
        
        System.out.println("");
        System.out.println("Todos os parâmetros: " + tool.getAllParamNames());
    }
}
