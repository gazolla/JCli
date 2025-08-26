package com.gazapps.mcp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gazapps.llm.Llm;
import com.gazapps.mcp.domain.Server;
import com.gazapps.mcp.domain.Tool;
import com.gazapps.mcp.rules.RuleEngine;


/**
 * Facade principal do sistema MCP que encapsula toda a complexidade de interação
 * com servidores MCP, coordenando operações entre componentes internos de matching,
 * execução e gerenciamento de domínios.
 */
public class MCPManager implements AutoCloseable {
    
 	private static final Logger logger = LoggerFactory.getLogger(MCPManager.class);
    
    private final MCPConfig config;
    private final MCPService mcpService;
    private final RuleEngine ruleEngine;
    private final ToolMatcher toolMatcher;
    private final DomainRegistry domainRegistry;
    private final ScheduledExecutorService scheduler;
    private boolean initialized;
    private final Llm llm;
    public Llm getLlm() { return llm; }
    
    public MCPManager(String configDirectory, Llm llm) {
        this.config = new MCPConfig(configDirectory);
        this.mcpService = new MCPService(config);
        this.ruleEngine = new RuleEngine(config.getRulesPath(), config.isRulesEnabled());
        this.toolMatcher = new ToolMatcher(ruleEngine);
        this.llm = Objects.requireNonNull(llm, "LLM cannot be null");
        
        String domainConfigPath = configDirectory + "/mcp/domains.json";
        this.domainRegistry = new DomainRegistry(llm, domainConfigPath);
        
        this.scheduler = Executors.newScheduledThreadPool(2);
        
        initialize();
    }
    
    
     public List<Tool> findTools(String query) {
        return findTools(query, MatchingOptions.defaultOptions());
    }
    
        public Map<Tool, Map<String, Object>> findSingleStepTools(String query){
    	return findSingleStepTools(query, MatchingOptions.defaultOptions());
    }
    
    private Map<Tool, Map<String, Object>> findSingleStepTools(String query, MatchingOptions options) {
        String bestDomain = findBestDomain(query);
        
        List<Tool> domainTools = getToolsByDomain(bestDomain);
        if (domainTools.isEmpty()) {
            logger.warn("Nenhuma ferramenta disponível no domínio: {}", bestDomain);
            return Collections.emptyMap();
        }

        Map<Tool, Map<String, Object>> matches = toolMatcher.findRelevantToolsWithParams(query, llm, domainTools, options);
        logger.debug("Encontradas {} ferramentas single-step para query: '{}'", matches.size(), query);
        return matches;
    }
    
    public Map<Tool, Map<String, Object>> findMultiStepTools(String query){
    	return findMultiStepTools(query, MatchingOptions.defaultOptions());
    }
    
    private Map<Tool, Map<String, Object>> findMultiStepTools(String query, MatchingOptions options) {
        Map<String, Double> domainScores = domainRegistry.calculateDomainMatches(query, llm, true); // isMultiStep = true
        List<Tool> relevantTools = new ArrayList<>();
        
        // Coletar ferramentas de TODOS os domínios relevantes
        for (Map.Entry<String, Double> entry : domainScores.entrySet()) {
            if (entry.getValue() > 0.6) {
                relevantTools.addAll(getToolsByDomain(entry.getKey()));
            }
        }
        
        if (relevantTools.isEmpty()) {
            logger.warn("Nenhuma ferramenta disponível para multi-step: {}", query);
            return Collections.emptyMap();
        }
        
        Map<Tool, Map<String, Object>> matches = toolMatcher.findMultipleToolsWithParams(query, llm, relevantTools, options);
        logger.debug("Encontradas {} ferramentas multi-step para query: '{}'", matches.size(), query);
        return matches;
    }
    
    public boolean isMultiStep(String query, Llm llm) {
        if (llm == null) return false;
        
        String prompt = "Analise a query e determine se a sua execução exige uma ou mais ferramentas.\n\n" +
                "Para fazer essa avaliação, procure por:\n" +
                "1. **Verbos ou Ações Múltiplas:** Identifique se a query contém múltiplos verbos que implicam ações distintas (ex: \"criar\" e \"mover\", \"pesquisar\" e \"enviar\").\n" +
                "2. **Conjunções e Conectores:** Procure por palavras como \"e\", \"ou\", \"então\", \"depois\" ou \"além disso\", que conectam diferentes partes da solicitação.\n" +
                "3. **Dependências:** Verifique se uma tarefa depende da conclusão de outra (ex: primeiro encontrar um dado e só então usá-lo em outra ação).\n\n" +
                "Com base nessa análise, responda de forma clara e objetiva se a query requer uma única ferramenta ou múltiplas.\n" +
                "Responda apenas com `true` ou `false`.\n\n" +
                "Query: " + query;
        
        var response = llm.generateResponse(prompt);
        
        return response.isSuccess() && response.getContent().toLowerCase().contains("true");
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
            return allTools; 
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
                       // for (Tool tool : serverTools) {
                        //    if (tool.getDomain() == null || tool.getDomain().isEmpty()) {
                        //        domainRegistry.addToolToDomain(discoveredDomain, tool);
                         //   }
                      // }
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
            logger.info("MCPManager inicializado com sucesso - logs em JavaCLI/log/");
            
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
     * Recarrega as regras do sistema.
     */
    public void reloadRules() {
        if (ruleEngine != null) {
            ruleEngine.reload();
        }
    }
    
    /**
     * Verifica se o sistema de regras está habilitado.
     */
    public boolean isRulesEnabled() {
        return ruleEngine != null && ruleEngine.isEnabled();
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
     * Exceção específica para erros do MCPManager.
     */
    public static class MCPManagerException extends RuntimeException {
        private static final long serialVersionUID = 1L;

		public MCPManagerException(String message) {
            super(message);
        }
        
        public MCPManagerException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}