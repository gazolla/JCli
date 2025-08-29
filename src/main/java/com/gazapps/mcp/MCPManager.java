package com.gazapps.mcp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gazapps.llm.Llm;
import com.gazapps.mcp.domain.Server;
import com.gazapps.mcp.domain.Tool;
import com.gazapps.mcp.rules.RuleEngine;


public class MCPManager implements AutoCloseable {
    
 	private static final Logger logger = LoggerFactory.getLogger(MCPManager.class);
    private static final double DOMAIN_RELEVANCE_THRESHOLD = 0.3;
    
    private final MCPConfig config;
    private final MCPService mcpService;
    private final RuleEngine ruleEngine;
    private final ToolMatcher toolMatcher;
    private final DomainRegistry domainRegistry;
    private final ScheduledExecutorService scheduler;
    private boolean initialized;
    private final Llm llm;
    
    // Cache para otimização de performance
    private final Map<String, Map<Tool, Map<String, Object>>> toolSelectionCache = new ConcurrentHashMap<>();
    private final Map<String, Boolean> observationUtilityCache = new ConcurrentHashMap<>();
    
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
    
        public Optional<Map<Tool, Map<String, Object>>> findSingleStepTools(String query){
    	return findSingleStepTools(query, MatchingOptions.defaultOptions());
    }
    
    private Optional<Map<Tool, Map<String, Object>>> findSingleStepTools(String query, MatchingOptions options) {
        String cacheKey = query + "|" + options.hashCode();
        Map<Tool, Map<String, Object>> cached = toolSelectionCache.get(cacheKey);
        if (cached != null) {
            logger.debug("Cache hit para findSingleStepTools: {}", query);
            return Optional.of(cached);
        }
        
        DomainSelectionResult domainResult = findBestDomainWithScore(query);
        if (domainResult.maxScore < DOMAIN_RELEVANCE_THRESHOLD) {
            logger.info("Domain threshold not met - max score {} < threshold {}", 
                        domainResult.maxScore, DOMAIN_RELEVANCE_THRESHOLD);
            return Optional.empty();
        }
        String bestDomain = domainResult.domainName;
        
        List<Tool> domainTools = getToolsByDomain(bestDomain);
        if (domainTools.isEmpty()) {
            logger.warn("Nenhuma ferramenta disponível no domínio: {}", bestDomain);
            return Optional.empty();
        }

        Map<Tool, Map<String, Object>> matches = toolMatcher.findRelevantToolsWithParams(query, llm, domainTools, options);
        toolSelectionCache.put(cacheKey, matches);
        
        logger.debug("Encontradas {} ferramentas single-step para query: '{}'", matches.size(), query);
        return Optional.of(matches);
    }
    
    public Optional<Map<Tool, Map<String, Object>>> findMultiStepTools(String query){
    	return findMultiStepTools(query, MatchingOptions.defaultOptions());
    }
    
    private Optional<Map<Tool, Map<String, Object>>> findMultiStepTools(String query, MatchingOptions options) {
        Map<String, Double> domainScores = domainRegistry.calculateDomainMatches(query, llm, true); // isMultiStep = true
        
        double maxScore = domainScores.values().stream()
            .mapToDouble(Double::doubleValue)
            .max()
            .orElse(0.0);
        
        if (maxScore < DOMAIN_RELEVANCE_THRESHOLD) {
            logger.info("Multi-step domain threshold not met - max score {} < threshold {}", 
                        maxScore, DOMAIN_RELEVANCE_THRESHOLD);
            return Optional.empty();
        }
        
        List<Tool> relevantTools = new ArrayList<>();
        
        for (Map.Entry<String, Double> entry : domainScores.entrySet()) {
            if (entry.getValue() > 0.6) {
                relevantTools.addAll(getToolsByDomain(entry.getKey()));
            }
        }
        
        if (relevantTools.isEmpty()) {
            logger.warn("Nenhuma ferramenta disponível para multi-step: {}", query);
            return Optional.empty();
        }
        
        Map<Tool, Map<String, Object>> matches = toolMatcher.findMultipleToolsWithParams(query, llm, relevantTools, options);
        logger.debug("Encontradas {} ferramentas multi-step para query: '{}'", matches.size(), query);
        return Optional.of(matches);
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
    
    public MCPService.ToolExecutionResult executeTool(String toolName, Map<String, Object> args) {
        Objects.requireNonNull(toolName, "Tool name cannot be null");
        
        if (args == null) {
            args = Collections.emptyMap();
        }
        
        try {
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
    
    
    
    public Set<String> getAvailableDomains() {
        if (domainRegistry != null) {
            return domainRegistry.getAllDomainNames();
        }
        Set<String> domains = new HashSet<>();
        
        List<Tool> allTools = mcpService.getAllAvailableTools();
        for (Tool tool : allTools) {
            domains.add(tool.getDomain());
        }
        
        domains.addAll(config.loadDomains().keySet());
        
        return domains;
    }
    
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
    
    public List<Server> getConnectedServers() {
        return new ArrayList<>(mcpService.getConnectedServers().values());
    }
    
    public boolean isObservationUseful(String observation, String originalQuery) {
        Objects.requireNonNull(observation, "Observation cannot be null");
        Objects.requireNonNull(originalQuery, "Original query cannot be null");
        
        if (observation.trim().isEmpty()) {
            return false;
        }
         String cacheKey = observation.hashCode() + "|" + originalQuery.hashCode();
        Boolean cached = observationUtilityCache.get(cacheKey);
        if (cached != null) {
            logger.debug("Cache hit para isObservationUseful");
            return cached;
        }
        
        try {
            boolean result = toolMatcher.evaluateObservationUtility(observation, originalQuery, llm);
            observationUtilityCache.put(cacheKey, result);
            
            return result;
        } catch (Exception e) {
            logger.error("Erro ao avaliar utilidade da observação", e);
            return false;
        }
    }
    
    public boolean isHealthy() {
        try {
            Map<String, Server> connectedServers = mcpService.getConnectedServers();
            if (connectedServers.isEmpty()) {
                return false;
            }
            
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
    
    public void refreshDomains() {
        if (domainRegistry != null && llm != null) {
            try {
                List<Tool> allTools = mcpService.getAllAvailableTools();
                Map<String, List<Tool>> toolsByServer = new HashMap<>();
                for (Tool tool : allTools) {
                    toolsByServer.computeIfAbsent(tool.getServerId(), k -> new ArrayList<>()).add(tool);
                }
                 for (Map.Entry<String, List<Tool>> entry : toolsByServer.entrySet()) {
                    List<Tool> serverTools = entry.getValue();
                    String discoveredDomain = domainRegistry.autoDiscoverDomain(serverTools, llm);
                    
                    if (discoveredDomain != null) {
                        logger.info("Domínio '{}' descoberto para servidor '{}'", discoveredDomain, entry.getKey());
                        
                    }
                }
                
                logger.info("Domínios atualizados com auto-descoberta");
            } catch (Exception e) {
                logger.error("Erro durante refresh de domínios", e);
            }
        }
        mcpService.refreshServerConnections();
    }
    
     @Override
    public void close() {
        logger.info("Fechando MCPManager...");
        
        try {
             scheduler.shutdown();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            
            mcpService.close();
            
             config.saveConfiguration();
            
            initialized = false;
            logger.info("MCPManager fechado com sucesso");
            
        } catch (Exception e) {
            logger.error("Erro ao fechar MCPManager", e);
        }
    }
    
    public MCPService getMcpService() {
        return mcpService;
    }
    
    
    private String findBestDomain(String query) {
        if (domainRegistry != null) {
            Map<String, Double> scores = domainRegistry.calculateDomainMatches(query, llm);
            return scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        }
        return null; 
    }
    
    private DomainSelectionResult findBestDomainWithScore(String query) {
        if (domainRegistry != null) {
            Map<String, Double> scores = domainRegistry.calculateDomainMatches(query, llm);
            
            var bestEntry = scores.entrySet().stream()
                .max(Map.Entry.comparingByValue());
            
            if (bestEntry.isPresent()) {
                return new DomainSelectionResult(bestEntry.get().getKey(), bestEntry.get().getValue());
            }
        }
        return new DomainSelectionResult(null, 0.0);
    }
    
    private void initialize() {
        try {
            config.validateConfiguration();
            
            // Agendar refresh periódico
            long refreshInterval = config.getRefreshIntervalMs();
            /*scheduler.scheduleAtFixedRate(
                this::refreshDomains,
                refreshInterval,
                refreshInterval,
                TimeUnit.MILLISECONDS
            );*/
            
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
    
    public void reloadRules() {
        if (ruleEngine != null) {
            ruleEngine.reload();
        }
    }
    
    public boolean isRulesEnabled() {
        return ruleEngine != null && ruleEngine.isEnabled();
    }
    
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
    
     private static class DomainSelectionResult {
        public final String domainName;
        public final double maxScore;
        
        public DomainSelectionResult(String domainName, double maxScore) {
            this.domainName = domainName;
            this.maxScore = maxScore;
        }
    }
    
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