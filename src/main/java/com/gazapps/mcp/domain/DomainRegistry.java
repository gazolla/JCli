package com.gazapps.mcp.domain;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gazapps.llm.Llm;

public class DomainRegistry {
    
    private static final Logger logger = LoggerFactory.getLogger(DomainRegistry.class);
    
    private final Map<String, Domain> domains = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Llm llm;
    private Path configPath;
    
    public DomainRegistry(Llm llm) {
        this.llm = llm; 
    }
    
    public DomainRegistry(Llm llm, Path configPath) {
        this.llm = llm; 
        this.configPath = configPath;
        if (configPath != null) {
            loadFromConfig(configPath);
        }
    }
    
     public void loadFromConfig(Path configPath) {
        this.configPath = configPath;
        File configFile = configPath.toFile();
        
        if (!configFile.exists()) {
            createDefaultDomains();
            saveToConfig(configPath);
            return;
        }
        
        try {
            TypeReference<Map<String, Map<String, Object>>> typeRef = new TypeReference<>() {};
            Map<String, Map<String, Object>> configData = objectMapper.readValue(configFile, typeRef);
            
            domains.clear();
            for (Map.Entry<String, Map<String, Object>> entry : configData.entrySet()) {
                try {
                    Domain domain = Domain.fromConfigMap(entry.getValue());
                    domains.put(entry.getKey(), domain);
                } catch (Exception e) {
                    System.err.println("Error loading domain " + entry.getKey() + ": " + e.getMessage());
                }
            }
            
        } catch (IOException e) {
            throw new DomainRegistryException("Error loading domain configuration: " + e.getMessage(), e);
        }
    }
    
    public void saveToConfig(Path configPath) {
        this.configPath = configPath;
        
        try {
            Map<String, Map<String, Object>> configData = new HashMap<>();
            for (Map.Entry<String, Domain> entry : domains.entrySet()) {
                configData.put(entry.getKey(), entry.getValue().toConfigMap());
            }
            
            File configFile = configPath.toFile();
            configFile.getParentFile().mkdirs(); // Create directories if needed
            
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFile, configData);
            
        } catch (IOException e) {
            throw new DomainRegistryException("Error saving domain configuration: " + e.getMessage(), e);
        }
    }
    
     public Domain getDomain(String domainName) {
        return domains.get(domainName);
    }
    
     public Set<String> getAllDomainNames() {
        return Set.copyOf(domains.keySet());
    }
    
    public List<Domain> getAllDomains() {
        return List.copyOf(domains.values());
    }
    
    public String autoDiscoverDomain(List<Tool> tools, Llm llmInstance) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }
        
        Llm activeLlm = llmInstance != null ? llmInstance : this.llm;
        
        // Create new domain using LLM
        return createNewDomainFromTools(tools, activeLlm);
    }
    
    public void createDomain(String name, Domain definition) {
        Objects.requireNonNull(name, "Domain name cannot be null");
        Objects.requireNonNull(definition, "Domain definition cannot be null");
        
        domains.put(name, definition);
        
        // Auto-save if path is configured
        if (configPath != null) {
            saveToConfig(configPath);
        }
    }
    
     public void updateDomain(String name, Domain definition) {
        Objects.requireNonNull(name, "Domain name cannot be null");
        Objects.requireNonNull(definition, "Domain definition cannot be null");
        
        if (!domains.containsKey(name)) {
            throw new DomainRegistryException("Domain not found: " + name);
        }
        
        domains.put(name, definition);
        
        if (configPath != null) {
            saveToConfig(configPath);
        }
    }
    
    public boolean removeDomain(String name) {
        boolean removed = domains.remove(name) != null;
        
        if (removed && configPath != null) {
            saveToConfig(configPath);
        }
        
        return removed;
    }
    
    public List<String> findMatchingDomains(String query, Llm llmInstance) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        Llm activeLlm = llmInstance != null ? llmInstance : this.llm;
        
        Map<String, Double> matches = calculateDomainMatches(query, activeLlm);
        
        return matches.entrySet().stream()
                .filter(entry -> entry.getValue() > 0.3) // Minimum threshold
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
    
    
     public void addPatternToDomain(String domainName, String pattern) {
        Domain domain = domains.get(domainName);
        if (domain == null) {
            throw new DomainRegistryException("Domain not found: " + domainName);
        }
        
        domain.addPattern(pattern);
        
        if (configPath != null) {
            saveToConfig(configPath);
        }
    }
    
     public Map<String, Double> calculateDomainMatches(String query, Llm llmInstance) {
        return calculateDomainMatches(query, llmInstance, false);
    }
    
    public Map<String, Double> calculateDomainMatches(String query, Llm llmInstance, boolean isMultiStep) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        
        Llm activeLlm = llmInstance != null ? llmInstance : this.llm;
        if (activeLlm == null) {
            return fallbackPatternMatching(query);
        }
        
        try {
            String prompt = isMultiStep ? createMultiDomainPrompt(query) : createUnifiedDomainPrompt(query);
            logger.debug("Domain filtering prompt: {}", prompt);
            
            var response = activeLlm.generateResponse(prompt);
            
            if (response.isSuccess()) {
                logger.debug("Domain filtering response: {}", response.getContent());
                Map<String, Double> scores = parseUnifiedDomainScores(response.getContent());
                logger.info("Domain scores for '{}': {}", query, scores);
                return scores;
            }
            
        } catch (Exception e) {
            logger.error("Error in unified domain filtering", e);
        }
        
        // Fallback to pattern matching if LLM fails
        return fallbackPatternMatching(query);
    }
    
    private String createUnifiedDomainPrompt(String query) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analyze the query and determine relevance scores:\n\n");
        prompt.append("Query: \"").append(query).append("\"\n\n");
        prompt.append("Domains:\n");
        
        int index = 1;
        for (Domain domain : domains.values()) {
            prompt.append(index++).append(". ")
                  .append(domain.getName())
                  .append(" - ")
                  .append(domain.getDescription())
                  .append("\n");
        }
        
        prompt.append("DO NOT EXPLAIN ANYTHING\n");
        prompt.append("Answer in JSON with scores 0.0-1.0:\n");
        prompt.append("{");
        
        boolean first = true;
        for (Domain domain : domains.values()) {
            if (!first) prompt.append(", ");
            prompt.append("\"").append(domain.getName()).append("\": 0.0");
            first = false;
        }
        
        prompt.append("}");
        
        return prompt.toString();
    }
    
    private String createMultiDomainPrompt(String query) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analyze the MULTI-STEP query and determine relevance scores for ALL necessary domains:\n\n");
        prompt.append("Query: \"").append(query).append("\"\n\n");
        prompt.append("Domains:\n");
        
        int index = 1;
        for (Domain domain : domains.values()) {
            prompt.append(index++).append(". ")
                  .append(domain.getName())
                  .append(" - ")
                  .append(domain.getDescription())
                  .append("\n");
        }
        
        prompt.append("IMPORTANT: Query requires multiple operations, identify ALL relevant domains.\n");
        prompt.append("DO NOT EXPLAIN ANYTHING\n");
        prompt.append("Answer in JSON with scores 0.0-1.0:\n");
        prompt.append("{");
        
        boolean first = true;
        for (Domain domain : domains.values()) {
            if (!first) prompt.append(", ");
            prompt.append("\"").append(domain.getName()).append("\": 0.0");
            first = false;
        }
        
        prompt.append("}");
        
        return prompt.toString();
    }
    
    // Private helper methods
    
    private Map<String, Double> parseUnifiedDomainScores(String llmResponse) {
        Map<String, Double> scores = new HashMap<>();
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            String jsonString = llmResponse.replaceAll("```json|```", "").trim();
            JsonNode json = mapper.readTree(jsonString);
            
            for (String domainName : domains.keySet()) {
                JsonNode scoreNode = json.get(domainName);
                if (scoreNode != null && scoreNode.isNumber()) {
                    double score = Math.max(0.0, Math.min(1.0, scoreNode.asDouble()));
                    if (score > 0.0) {
                        scores.put(domainName, score);
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Error parsing domain scores", e);
        }
        
        return scores;
    }
    
    private Map<String, Double> fallbackPatternMatching(String query) {
        Map<String, Double> matches = new HashMap<>();
        
        for (Domain domain : domains.values()) {
            double patternScore = domain.calculatePatternMatch(query);
            if (patternScore > 0.0) {
                matches.put(domain.getName(), patternScore);
            }
        }
        
        return matches;
    }
    
    private void createDefaultDomains() {
        // Filesystem domain
        Domain filesystem = Domain.builder()
                .name("filesystem")
                .description("File system operations")
                .addPattern("file")
                .addPattern("directory")
                .addPattern("folder")
                .addSemanticKeyword("read")
                .addSemanticKeyword("write")
                .addSemanticKeyword("create")
                .build();
        
        // Time domain
        Domain time = Domain.builder()
                .name("time")
                .description("Time, date and timezone information")
                .addPattern("time")
                .addPattern("date")
                .addPattern("hour")
                .addSemanticKeyword("timezone")
                .addSemanticKeyword("calendar")
                .build();
        
        // Weather domain
        Domain weather = Domain.builder()
                .name("weather")
                .description("Weather information and forecasts")
                .addPattern("weather")
                .addPattern("climate")
                .addPattern("forecast")
                .addPattern("temperature")
                .addSemanticKeyword("meteorology")
                .addSemanticKeyword("forecast")
                .build();
        
        domains.put("filesystem", filesystem);
        domains.put("time", time);
        domains.put("weather", weather);
    }
    
    
    private String createNewDomainFromTools(List<Tool> tools, Llm llm) {
        if (tools.isEmpty()) return null;
        
        try {
            String prompt = createDomainDiscoveryPrompt(tools);
            var response = llm.generateResponse(prompt);
            
            if (response.isSuccess()) {
                return processDomainCreationResponse(response.getContent(), tools);
            }
            
        } catch (Exception e) {
            System.err.println("Error in domain auto-discovery: " + e.getMessage());
        }
        
        // Fallback: create domain based on first tool
        return createFallbackDomain(tools);
    }
    
    private String createDomainDiscoveryPrompt(List<Tool> tools) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analyze the following tools and suggest an appropriate domain name:\n\n");
        
        for (Tool tool : tools) {
            prompt.append("- ").append(tool.getName()).append(": ").append(tool.getDescription()).append("\n");
        }
        
        prompt.append("\nExisting domains to avoid duplication: ");
        prompt.append(String.join(", ", domains.keySet()));
        
        prompt.append("\n\nAnswer only with the domain name (one word, lowercase, no spaces).");
        
        return prompt.toString();
    }
    
    private String processDomainCreationResponse(String llmResponse, List<Tool> tools) {
        if (llmResponse == null) return null;
        
        // Extract first valid word from response
        String domainName = llmResponse.trim().toLowerCase().split("\\s+")[0];
        domainName = domainName.replaceAll("[^a-z0-9_]", "");
        
        if (domainName.isEmpty() || domains.containsKey(domainName)) {
            return createFallbackDomain(tools);
        }
        
        return domainName;
    }
    
    private String createFallbackDomain(List<Tool> tools) {
        if (tools.isEmpty()) return null;
        
        Tool firstTool = tools.get(0);
        String domainName = firstTool.getName().split("_")[0];
        
        // Ensure the name is unique
        String originalName = domainName;
        int counter = 1;
        while (domains.containsKey(domainName)) {
            domainName = originalName + "_" + counter++;
        }
        
        Domain.Builder builder = Domain.builder()
                .name(domainName)
                .description("Auto-created domain based on " + firstTool.getName());
        
        return domainName;
    }
    
    public double calculateSemanticMatchForDomain(Domain domain, String query, Llm llmInstance) {
        if (query == null || query.trim().isEmpty() || domain == null) {
            return domain != null ? domain.calculatePatternMatch(query) : 0.0;
        }
        
        Llm activeLlm = llmInstance != null ? llmInstance : this.llm;
        if (activeLlm == null) {
            return domain.calculatePatternMatch(query);
        }
        
        try {
            String prompt = createDomainMatchingPrompt(domain, query);
            var response = activeLlm.generateResponse(prompt);
            
            if (response.isSuccess()) {
                return parseDomainScore(response.getContent());
            }
        } catch (Exception e) {
            logger.debug("Erro no semantic matching para domínio {}, usando pattern matching", domain.getName(), e);
        }
        
        return domain.calculatePatternMatch(query);
    }
    
    private String createDomainMatchingPrompt(Domain domain, String query) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analise se a seguinte query está relacionada ao domínio '").append(domain.getName()).append("'.\n\n");
        prompt.append("Descrição do domínio: ").append(domain.getDescription()).append("\n");
        prompt.append("Palavras-chave: ").append(String.join(", ", domain.getSemanticKeywords())).append("\n");
        prompt.append("\n");
        prompt.append("Query: \"").append(query).append("\"\n\n");
        prompt.append("Responda apenas com um número entre 0.0 e 1.0 indicando o nível de relevância.");
        return prompt.toString();
    }
    
    private double parseDomainScore(String llmResponse) {
        if (llmResponse == null) return 0.0;
        
        try {
            String cleaned = llmResponse.trim().replaceAll("[^0-9.]", "");
            if (!cleaned.isEmpty()) {
                double score = Double.parseDouble(cleaned);
                return Math.max(0.0, Math.min(1.0, score)); 
            }
        } catch (NumberFormatException e) {
            String response = llmResponse.toLowerCase();
            if (response.contains("alta") || response.contains("muito") || response.contains("sim")) {
                return 0.8;
            } else if (response.contains("média") || response.contains("parcial")) {
                return 0.5;
            } else if (response.contains("baixa") || response.contains("pouco") || response.contains("não")) {
                return 0.2;
            }
        }
        
        return 0.0;
    }
    
     public static class DomainRegistryException extends RuntimeException {
        private static final long serialVersionUID = 1L;

		public DomainRegistryException(String message) {
            super(message);
        }
        
        public DomainRegistryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
