package com.gazapps.mcp;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gazapps.llm.Llm;
import com.gazapps.mcp.domain.DomainDefinition;
import com.gazapps.mcp.domain.Server;
import com.gazapps.mcp.domain.Tool;
import com.gazapps.mcp.matching.MultiStepDetector;

/**
 * Facade principal do sistema MCP que encapsula toda a complexidade de interação
 * com servidores MCP, coordenando operações entre componentes internos de matching,
 * execução e gerenciamento de domínios.
 */
public class MCPManager implements AutoCloseable {
    
 	private static final Logger logger = LoggerFactory.getLogger(MCPManager.class);
    
    private final MCPConfig config;
    private final MCPService mcpService;
    private final ToolMatcher toolMatcher;
    private final DomainRegistry domainRegistry;
    private final MultiStepDetector multiStepDetector;
    private final ScheduledExecutorService scheduler;
    private boolean initialized;
    private final Llm llm;
    public Llm getLlm() { return llm; }
    
    public MCPManager(String configDirectory, Llm llm) {
        this.config = new MCPConfig(configDirectory);
        this.mcpService = new MCPService(config);
        this.toolMatcher = new ToolMatcher();
        this.multiStepDetector = new MultiStepDetector();
        this.llm = Objects.requireNonNull(llm, "LLM cannot be null");
        
        String domainConfigPath = configDirectory + "/mcp/domains.json";
        this.domainRegistry = new DomainRegistry(llm, domainConfigPath);
        
        this.scheduler = Executors.newScheduledThreadPool(2);
        
        initialize();
    }
    
    
    /**
     * Busca ferramentas relevantes para uma query usando matching básico.
     */
    public List<Tool> findTools(String query) {
        return findTools(query, MatchingOptions.defaultOptions());
    }
    
    /**
     * Busca ferramentas com parâmetros extraídos para uma query.
     */
    public Map<Tool, Map<String, Object>> findToolsWithParameters(String query) {
        return findToolsWithParameters(query, MatchingOptions.defaultOptions());
    }
    
    /**
     * Busca ferramentas com parâmetros extraídos para uma query com opções específicas.
     */
    public Map<Tool, Map<String, Object>> findToolsWithParameters(String query, MatchingOptions options) {
        Objects.requireNonNull(query, "Query cannot be null");
        Objects.requireNonNull(options, "Matching options cannot be null");

        if (query.trim().isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            // 1. Domain filtering primeiro
            String bestDomain = findBestDomain(query);
            
            // 2. Ferramentas do domínio
            List<Tool> domainTools = getToolsByDomain(bestDomain);
            if (domainTools.isEmpty()) {
                logger.warn("Nenhuma ferramenta disponível no domínio: {}", bestDomain);
                return Collections.emptyMap();
            }

            // 3. LLM selection + parameter extraction
            Map<Tool, Map<String, Object>> matches = toolMatcher.findRelevantToolsWithParams(query, llm, domainTools, options);

            logger.debug("Encontradas {} ferramentas com parâmetros para query: '{}'", matches.size(), query);
            return matches;

        } catch (Exception e) {
            logger.error("Erro ao buscar ferramentas com parâmetros para query: '{}'", query, e);
            return Collections.emptyMap();
        }
    }
    
    /**
     * Busca ferramentas relevantes para uma query com opções específicas de matching.
     */
    public List<Tool> findTools(String query, MatchingOptions options) {
        Objects.requireNonNull(query, "Query cannot be null");
        Objects.requireNonNull(options, "Matching options cannot be null");

        if (query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            List<Tool> availableTools = mcpService.getAllAvailableTools();
            if (availableTools.isEmpty()) {
                logger.warn("Nenhuma ferramenta disponível para matching");
                return Collections.emptyList();
            }

            List<Tool> matches = toolMatcher.findRelevantTools(query, llm, availableTools, options);

            if (options.maxResults > 0 && matches.size() > options.maxResults) {
                matches = matches.subList(0, options.maxResults);
            }

            logger.debug("Encontradas {} ferramentas para query: '{}'", matches.size(), query);
            return matches;

        } catch (Exception e) {
            logger.error("Erro ao buscar ferramentas para query: '{}'", query, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Executa uma ferramenta pelo nome com argumentos.
     */
    public MCPService.ToolExecutionResult executeTool(String toolName, Map<String, Object> args) {
        Objects.requireNonNull(toolName, "Tool name cannot be null");
        
        if (args == null) {
            args = Collections.emptyMap();
        }
        
        try {
            // Encontrar a ferramenta em todos os servidores
            Tool tool = findToolByName(toolName);
            if (tool == null) {
                return MCPService.ToolExecutionResult.error("Ferramenta não encontrada: " + toolName);
            }
            
            return mcpService.callTool(tool.getServerId(), tool.getName(), args);
            
        } catch (Exception e) {
            logger.error("Erro ao executar ferramenta '{}'", toolName, e);
            return MCPService.ToolExecutionResult.error("Erro na execução: " + e.getMessage());
        }
    }
    
    /**
     * Executa uma ferramenta específica com argumentos.
     */
    public MCPService.ToolExecutionResult executeTool(Tool tool, Map<String, Object> args) {
        Objects.requireNonNull(tool, "Tool cannot be null");
        
        if (args == null) {
            args = Collections.emptyMap();
        }
        
        try {
            return mcpService.callTool(tool.getServerId(), tool.getName(), args);
            
        } catch (Exception e) {
            logger.error("Erro ao executar ferramenta '{}'", tool.getName(), e);
            return MCPService.ToolExecutionResult.error("Erro na execução: " + e.getMessage());
        }
    }
    
    /**
     * Busca ferramentas que podem ser executadas sequencialmente.
     */
    public List<Tool> findSequentialTools(String query) {
        if (llm == null) {
            logger.warn("LLM não está disponível, retornando resultados de matching básico para query sequencial.");
            return findTools(query);
        }
        List<Tool> availableTools = mcpService.getAllAvailableTools();
        return multiStepDetector.detectSequentialTools(query, availableTools, llm);
    }
    
    /**
     * Retorna conjunto de todos os domínios disponíveis.
     */
    public Set<String> getAvailableDomains() {
        if (domainRegistry != null) {
            return domainRegistry.getAllDomainNames();
        }
        
        // Fallback para implementação antiga
        Set<String> domains = new HashSet<>();
        
        // Coletar domínios das ferramentas
        List<Tool> allTools = mcpService.getAllAvailableTools();
        for (Tool tool : allTools) {
            domains.add(tool.getDomain());
        }
        
        // Adicionar domínios da configuração
        domains.addAll(config.loadDomains().keySet());
        
        return domains;
    }
    
    /**
     * Retorna ferramentas de um domínio específico.
     */
    public List<Tool> getToolsByDomain(String domain) {
        List<Tool> allTools = mcpService.getAllAvailableTools();
        
        if (domain == null) {
            return allTools; // Todas as ferramentas se sem domínio
        }
        
        List<Tool> domainTools = new ArrayList<>();
        for (Tool tool : allTools) {
            if (domain.equals(tool.getDomain())) {
                domainTools.add(tool);
            }
        }
        
        return domainTools;
    }
    
    /**
     * Adiciona um novo servidor dinamicamente.
     */
    public boolean addServer(MCPConfig.ServerConfig config) {
        Objects.requireNonNull(config, "Server config cannot be null");
        
        try {
            boolean connected = mcpService.connectServer(config);
            if (connected) {
                this.config.updateServerConfig(config.id, config);
                logger.info("Servidor '{}' adicionado e conectado", config.id);
            }
            return connected;
            
        } catch (Exception e) {
            logger.error("Erro ao adicionar servidor '{}'", config.id, e);
            return false;
        }
    }
    
    /**
     * Remove um servidor.
     */
    public boolean removeServer(String serverId) {
        Objects.requireNonNull(serverId, "Server ID cannot be null");
        
        try {
            mcpService.disconnectServer(serverId);
            config.removeServer(serverId);
            logger.info("Servidor '{}' removido", serverId);
            return true;
            
        } catch (Exception e) {
            logger.error("Erro ao remover servidor '{}'", serverId, e);
            return false;
        }
    }
    
    /**
     * Retorna lista de servidores conectados.
     */
    public List<Server> getConnectedServers() {
        return new ArrayList<>(mcpService.getConnectedServers().values());
    }
    
    /**
     * Verifica se o sistema está saudável.
     */
    public boolean isHealthy() {
        try {
            Map<String, Server> connectedServers = mcpService.getConnectedServers();
            if (connectedServers.isEmpty()) {
                return false;
            }
            
            // Verificar se pelo menos um servidor está saudável
            for (Server server : connectedServers.values()) {
                if (server.isHealthy()) {
                    return true;
                }
            }
            
            return false;
            
        } catch (Exception e) {
            logger.error("Erro ao verificar saúde do sistema", e);
            return false;
        }
    }
    
    /**
     * Atualiza domínios descobrindo automaticamente novos domínios.
     */
    public void refreshDomains() {
        if (domainRegistry != null && llm != null) {
            try {
                // Obter todas as ferramentas disponíveis
                List<Tool> allTools = mcpService.getAllAvailableTools();
                
                // Agrupar ferramentas por servidor para descoberta de domínios
                Map<String, List<Tool>> toolsByServer = new HashMap<>();
                for (Tool tool : allTools) {
                    toolsByServer.computeIfAbsent(tool.getServerId(), k -> new ArrayList<>()).add(tool);
                }
                
                // Auto-descobrir domínios para ferramentas que não estão em domínios conhecidos
                for (Map.Entry<String, List<Tool>> entry : toolsByServer.entrySet()) {
                    List<Tool> serverTools = entry.getValue();
                    String discoveredDomain = domainRegistry.autoDiscoverDomain(serverTools, llm);
                    
                    if (discoveredDomain != null) {
                        logger.info("Domínio '{}' descoberto para servidor '{}'", discoveredDomain, entry.getKey());
                        
                        // Atualizar ferramentas com o domínio descoberto
                        for (Tool tool : serverTools) {
                            if (tool.getDomain() == null || tool.getDomain().isEmpty()) {
                                domainRegistry.addToolToDomain(discoveredDomain, tool);
                            }
                        }
                    }
                }
                
                logger.info("Domínios atualizados com auto-descoberta");
            } catch (Exception e) {
                logger.error("Erro durante refresh de domínios", e);
            }
        }
        
        // Atualizar conexões dos servidores
        mcpService.refreshServerConnections();
    }
    
    /**
     * Fecha o sistema e limpa recursos.
     */
    @Override
    public void close() {
        logger.info("Fechando MCPManager...");
        
        try {
            // Parar scheduler
            scheduler.shutdown();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            
            // Fechar serviços
            mcpService.close();
            
            // Salvar configuração
            config.saveConfiguration();
            
            initialized = false;
            logger.info("MCPManager fechado com sucesso");
            
        } catch (Exception e) {
            logger.error("Erro ao fechar MCPManager", e);
        }
    }
    
    /**
     * Acesso ao serviço MCP interno (para testes).
     * @return MCPService instance
     */
    public MCPService getMcpService() {
        return mcpService;
    }
    
    // Métodos privados auxiliares
    
    private String findBestDomain(String query) {
        if (domainRegistry != null) {
            Map<String, Double> scores = domainRegistry.calculateDomainMatches(query, llm);
            return scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        }
        return null; // Sem filtering se não há domainRegistry
    }
    
    private void initialize() {
        try {
            // Validar configuração
            config.validateConfiguration();
            
            // Agendar refresh periódico
            long refreshInterval = config.getRefreshIntervalMs();
            scheduler.scheduleAtFixedRate(
                this::refreshDomains,
                refreshInterval,
                refreshInterval,
                TimeUnit.MILLISECONDS
            );
            
            initialized = true;
            logger.info("MCPManager inicializado com sucesso");
            
        } catch (Exception e) {
            logger.error("Erro ao inicializar MCPManager", e);
            throw new MCPManagerException("Falha na inicialização", e);
        }
    }
    
    private Tool findToolByName(String toolName) {
        List<Tool> allTools = mcpService.getAllAvailableTools();
        
        for (Tool tool : allTools) {
            if (toolName.equals(tool.getName())) {
                return tool;
            }
        }
        
        return null;
    }
    
    /**
     * Opções para configurar o comportamento de matching.
     */
    public static class MatchingOptions {
        public final boolean useSemanticMatching;
        public final double confidenceThreshold;
        public final int maxResults;
        public final Set<String> includeDomains;
        public final Set<String> excludeDomains;
        
        public MatchingOptions(boolean useSemanticMatching, double confidenceThreshold,
                             int maxResults, Set<String> includeDomains, Set<String> excludeDomains) {
            this.useSemanticMatching = useSemanticMatching;
            this.confidenceThreshold = Math.max(0.0, Math.min(1.0, confidenceThreshold));
            this.maxResults = Math.max(0, maxResults);
            this.includeDomains = includeDomains != null ? Set.copyOf(includeDomains) : Collections.emptySet();
            this.excludeDomains = excludeDomains != null ? Set.copyOf(excludeDomains) : Collections.emptySet();
        }
        
        public static MatchingOptions defaultOptions() {
            return new MatchingOptions(true, 0.5, 10, Collections.emptySet(), Collections.emptySet());
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private boolean useSemanticMatching = false;
            private double confidenceThreshold = 0.5;
            private int maxResults = 10;
            private Set<String> includeDomains = Collections.emptySet();
            private Set<String> excludeDomains = Collections.emptySet();
            
            public Builder useSemanticMatching(boolean use) {
                this.useSemanticMatching = use;
                return this;
            }
            
            public Builder confidenceThreshold(double threshold) {
                this.confidenceThreshold = threshold;
                return this;
            }
            
            public Builder maxResults(int max) {
                this.maxResults = max;
                return this;
            }
            
            public Builder includeDomains(Set<String> domains) {
                this.includeDomains = domains != null ? Set.copyOf(domains) : Collections.emptySet();
                return this;
            }
            
            public Builder excludeDomains(Set<String> domains) {
                this.excludeDomains = domains != null ? Set.copyOf(domains) : Collections.emptySet();
                return this;
            }
            
            public MatchingOptions build() {
                return new MatchingOptions(useSemanticMatching, confidenceThreshold, maxResults, includeDomains, excludeDomains);
            }
        }
    }
    
    /**
     * Resultado da execução de uma ferramenta.
     * @deprecated Use MCPService.ToolExecutionResult instead
     */
    @Deprecated
    public static class ToolExecutionResult {
        public final boolean success;
        public final Tool tool;
        public final String message;
        public final Exception error;
        
        private ToolExecutionResult(boolean success, Tool tool, String message, Exception error) {
            this.success = success;
            this.tool = tool;
            this.message = message;
            this.error = error;
        }
        
        public static ToolExecutionResult success(Tool tool, String message) {
            return new ToolExecutionResult(true, tool, message, null);
        }
        
        public static ToolExecutionResult error(String message) {
            return new ToolExecutionResult(false, null, message, null);
        }
        
        public static ToolExecutionResult error(String message, Exception error) {
            return new ToolExecutionResult(false, null, message, error);
        }
    }
    
    
    
    /**
     * Exceção específica para erros do MCPManager.
     */
    public static class MCPManagerException extends RuntimeException {
        public MCPManagerException(String message) {
            super(message);
        }
        
        public MCPManagerException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}