package com.gazapps.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gazapps.llm.Llm;
import com.gazapps.mcp.domain.DomainDefinition;
import com.gazapps.mcp.domain.Tool;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gerencia dinamicamente domínios de ferramentas MCP.
 * Responsável por descoberta automática, matching semântico e persistência de configurações.
 */
public class DomainRegistry {
    
    private static final Logger logger = LoggerFactory.getLogger(DomainRegistry.class);
    
    private final Map<String, DomainDefinition> domains = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Llm llm;
    private String configPath;
    
    public DomainRegistry(Llm llm) {
        this.llm = llm; // Permitir null para testes
    }
    
    public DomainRegistry(Llm llm, String configPath) {
        this.llm = llm; // Permitir null, mas validar antes de usar
        this.configPath = configPath;
        if (configPath != null) {
            loadFromConfig(configPath);
        }
    }
    
    /**
     * Carrega definições de domínio de um arquivo JSON.
     */
    public void loadFromConfig(String configPath) {
        this.configPath = configPath;
        File configFile = new File(configPath);
        
        if (!configFile.exists()) {
            // Criar configuração padrão se arquivo não existir
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
                    DomainDefinition domain = DomainDefinition.fromConfigMap(entry.getValue());
                    domains.put(entry.getKey(), domain);
                } catch (Exception e) {
                    System.err.println("Erro ao carregar domínio " + entry.getKey() + ": " + e.getMessage());
                }
            }
            
        } catch (IOException e) {
            throw new DomainRegistryException("Erro ao carregar configuração de domínios: " + e.getMessage(), e);
        }
    }
    
    /**
     * Salva configurações atuais para arquivo JSON.
     */
    public void saveToConfig(String configPath) {
        this.configPath = configPath;
        
        try {
            Map<String, Map<String, Object>> configData = new HashMap<>();
            for (Map.Entry<String, DomainDefinition> entry : domains.entrySet()) {
                configData.put(entry.getKey(), entry.getValue().toConfigMap());
            }
            
            File configFile = new File(configPath);
            configFile.getParentFile().mkdirs(); // Criar diretórios se necessário
            
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFile, configData);
            
        } catch (IOException e) {
            throw new DomainRegistryException("Erro ao salvar configuração de domínios: " + e.getMessage(), e);
        }
    }
    
    /**
     * Obtém definição de um domínio específico.
     */
    public DomainDefinition getDomain(String domainName) {
        return domains.get(domainName);
    }
    
    /**
     * Obtém todos os nomes de domínios registrados.
     */
    public Set<String> getAllDomainNames() {
        return Set.copyOf(domains.keySet());
    }
    
    /**
     * Obtém todas as definições de domínio.
     */
    public List<DomainDefinition> getAllDomains() {
        return List.copyOf(domains.values());
    }
    
    /**
     * Descobre automaticamente o domínio mais apropriado para uma lista de ferramentas.
     * Usa LLM para análise semântica quando necessário.
     */
    public String autoDiscoverDomain(List<Tool> tools, Llm llmInstance) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }
        
        Llm activeLlm = llmInstance != null ? llmInstance : this.llm;
        
        // Primeiro, tentar mapear para domínios existentes
        Map<String, Double> domainScores = new HashMap<>();
        for (DomainDefinition domain : domains.values()) {
            double score = calculateToolsMatchScore(tools, domain);
            if (score > 0.5) { // Threshold para considerar match
                domainScores.put(domain.getName(), score);
            }
        }
        
        // Se encontrou match em domínio existente
        if (!domainScores.isEmpty()) {
            return domainScores.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
        }
        
        // Criar novo domínio usando LLM
        return createNewDomainFromTools(tools, activeLlm);
    }
    
    /**
     * Cria um novo domínio baseado na análise de ferramentas.
     */
    public void createDomain(String name, DomainDefinition definition) {
        Objects.requireNonNull(name, "Domain name cannot be null");
        Objects.requireNonNull(definition, "Domain definition cannot be null");
        
        domains.put(name, definition);
        
        // Auto-save se tiver path configurado
        if (configPath != null) {
            saveToConfig(configPath);
        }
    }
    
    /**
     * Atualiza definição de domínio existente.
     */
    public void updateDomain(String name, DomainDefinition definition) {
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
    
    /**
     * Remove um domínio do registro.
     */
    public boolean removeDomain(String name) {
        boolean removed = domains.remove(name) != null;
        
        if (removed && configPath != null) {
            saveToConfig(configPath);
        }
        
        return removed;
    }
    
    /**
     * Encontra domínios que fazem match com a query usando LLM.
     */
    public List<String> findMatchingDomains(String query, Llm llmInstance) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        Llm activeLlm = llmInstance != null ? llmInstance : this.llm;
        
        Map<String, Double> matches = calculateDomainMatches(query, activeLlm);
        
        return matches.entrySet().stream()
                .filter(entry -> entry.getValue() > 0.3) // Threshold mínimo
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
    
    /**
     * Adiciona uma ferramenta a um domínio específico.
     */
    public void addToolToDomain(String domainName, Tool tool) {
        DomainDefinition domain = domains.get(domainName);
        if (domain == null) {
            throw new DomainRegistryException("Domain not found: " + domainName);
        }
        
        domain.addTool(tool.getName());
        
        if (configPath != null) {
            saveToConfig(configPath);
        }
    }
    
    /**
     * Adiciona um padrão a um domínio específico.
     */
    public void addPatternToDomain(String domainName, String pattern) {
        DomainDefinition domain = domains.get(domainName);
        if (domain == null) {
            throw new DomainRegistryException("Domain not found: " + domainName);
        }
        
        domain.addPattern(pattern);
        
        if (configPath != null) {
            saveToConfig(configPath);
        }
    }
    
    /**
     * Calcula scores de match para todos os domínios em relação a uma query.
     * Utiliza matching unificado com LLM para melhor performance.
     */
    public Map<String, Double> calculateDomainMatches(String query, Llm llmInstance) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        
        Llm activeLlm = llmInstance != null ? llmInstance : this.llm;
        if (activeLlm == null) {
            return fallbackPatternMatching(query);
        }
        
        try {
            String prompt = createUnifiedDomainPrompt(query);
            logger.debug("Domain filtering prompt: {}", prompt);
            
            var response = activeLlm.generateResponse(prompt);
            
            if (response.isSuccess()) {
                logger.debug("Domain filtering response: {}", response.getContent());
                Map<String, Double> scores = parseUnifiedDomainScores(response.getContent());
                logger.info("Domain scores for '{}': {}", query, scores);
                return scores;
            }
            
        } catch (Exception e) {
            logger.error("Erro no domain filtering unificado", e);
        }
        
        // Fallback para pattern matching se LLM falhar
        return fallbackPatternMatching(query);
    }
    
    // Métodos privados auxiliares
    
    private String createUnifiedDomainPrompt(String query) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analise a query e determine os scores de relevância:\n\n");
        prompt.append("Query: \"").append(query).append("\"\n\n");
        prompt.append("Domínios:\n");
        
        int index = 1;
        for (DomainDefinition domain : domains.values()) {
            prompt.append(index++).append(". ")
                  .append(domain.getName())
                  .append(" - ")
                  .append(domain.getDescription())
                  .append("\n");
        }
        
        prompt.append("\nResponda em JSON com scores 0.0-1.0:\n");
        prompt.append("{");
        
        boolean first = true;
        for (DomainDefinition domain : domains.values()) {
            if (!first) prompt.append(", ");
            prompt.append("\"").append(domain.getName()).append("\": 0.0");
            first = false;
        }
        
        prompt.append("}");
        
        return prompt.toString();
    }
    
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
            logger.error("Erro ao fazer parse dos scores de domínio", e);
        }
        
        return scores;
    }
    
    private Map<String, Double> fallbackPatternMatching(String query) {
        Map<String, Double> matches = new HashMap<>();
        
        for (DomainDefinition domain : domains.values()) {
            double patternScore = domain.calculatePatternMatch(query);
            if (patternScore > 0.0) {
                matches.put(domain.getName(), patternScore);
            }
        }
        
        return matches;
    }
    
    private void createDefaultDomains() {
        // Domínio filesystem
        DomainDefinition filesystem = DomainDefinition.builder()
                .name("filesystem")
                .description("Operações com sistema de arquivos")
                .addPattern("arquivo")
                .addPattern("file")
                .addPattern("diretório")
                .addPattern("pasta")
                .addSemanticKeyword("read")
                .addSemanticKeyword("write")
                .addSemanticKeyword("create")
                .addTool("read_file")
                .addTool("write_file")
                .multiStepCapable(true)
                .build();
        
        // Domínio time
        DomainDefinition time = DomainDefinition.builder()
                .name("time")
                .description("Informações de tempo, data e fuso horário")
                .addPattern("time")
                .addPattern("tempo")
                .addPattern("data")
                .addPattern("hora")
                .addSemanticKeyword("timezone")
                .addSemanticKeyword("calendar")
                .addTool("get_current_time")
                .multiStepCapable(false)
                .build();
        
        // Domínio weather
        DomainDefinition weather = DomainDefinition.builder()
                .name("weather")
                .description("Informações meteorológicas e previsões do tempo")
                .addPattern("weather")
                .addPattern("clima")
                .addPattern("previsão")
                .addPattern("temperatura")
                .addSemanticKeyword("meteorologia")
                .addSemanticKeyword("forecast")
                .addTool("get_weather")
                .multiStepCapable(false)
                .build();
        
        domains.put("filesystem", filesystem);
        domains.put("time", time);
        domains.put("weather", weather);
    }
    
    private double calculateToolsMatchScore(List<Tool> tools, DomainDefinition domain) {
        if (tools.isEmpty()) return 0.0;
        
        int matches = 0;
        for (Tool tool : tools) {
            if (domain.supportsTool(tool.getName())) {
                matches++;
            }
        }
        
        return (double) matches / tools.size();
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
            System.err.println("Erro na auto-descoberta de domínio: " + e.getMessage());
        }
        
        // Fallback: criar domínio baseado no primeiro tool
        return createFallbackDomain(tools);
    }
    
    private String createDomainDiscoveryPrompt(List<Tool> tools) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analise as seguintes ferramentas e sugira um nome de domínio apropriado:\n\n");
        
        for (Tool tool : tools) {
            prompt.append("- ").append(tool.getName()).append(": ").append(tool.getDescription()).append("\n");
        }
        
        prompt.append("\nDomínios existentes para evitar duplicação: ");
        prompt.append(String.join(", ", domains.keySet()));
        
        prompt.append("\n\nResponda apenas com o nome do domínio (uma palavra, minúscula, sem espaços).");
        
        return prompt.toString();
    }
    
    private String processDomainCreationResponse(String llmResponse, List<Tool> tools) {
        if (llmResponse == null) return null;
        
        // Extrair primeira palavra válida da resposta
        String domainName = llmResponse.trim().toLowerCase().split("\\s+")[0];
        domainName = domainName.replaceAll("[^a-z0-9_]", "");
        
        if (domainName.isEmpty() || domains.containsKey(domainName)) {
            return createFallbackDomain(tools);
        }
        
        // Criar domínio com base nas ferramentas
        DomainDefinition.Builder builder = DomainDefinition.builder()
                .name(domainName)
                .description("Domínio auto-descoberto para " + domainName);
        
        for (Tool tool : tools) {
            builder.addTool(tool.getName());
            
            // Adicionar padrões baseados no nome da ferramenta
            String[] nameParts = tool.getName().split("_");
            for (String part : nameParts) {
                if (part.length() > 2) {
                    builder.addPattern(part);
                }
            }
        }
        
        DomainDefinition newDomain = builder.build();
        createDomain(domainName, newDomain);
        
        return domainName;
    }
    
    private String createFallbackDomain(List<Tool> tools) {
        if (tools.isEmpty()) return null;
        
        Tool firstTool = tools.get(0);
        String domainName = firstTool.getName().split("_")[0];
        
        // Garantir que o nome é único
        String originalName = domainName;
        int counter = 1;
        while (domains.containsKey(domainName)) {
            domainName = originalName + "_" + counter++;
        }
        
        DomainDefinition.Builder builder = DomainDefinition.builder()
                .name(domainName)
                .description("Domínio auto-criado baseado em " + firstTool.getName());
        
        for (Tool tool : tools) {
            builder.addTool(tool.getName());
        }
        
        DomainDefinition newDomain = builder.build();
        createDomain(domainName, newDomain);
        
        return domainName;
    }
    
    /**
     * Exception específica para operações do DomainRegistry.
     */
    public static class DomainRegistryException extends RuntimeException {
        public DomainRegistryException(String message) {
            super(message);
        }
        
        public DomainRegistryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
