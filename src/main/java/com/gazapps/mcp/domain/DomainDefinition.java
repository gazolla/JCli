package com.gazapps.mcp.domain;

import java.util.*;

import com.gazapps.llm.Llm;

/**
 * Definição de domínio que encapsula padrões de texto, palavras-chave semânticas
 * e capacidades de matching inteligente usando LLM.
 */
public class DomainDefinition {
    
    private final String name;
    private final String description;
    private final List<String> patterns;
    private final List<String> semanticKeywords;
    
    public DomainDefinition(String name, String description, List<String> patterns, List<String> semanticKeywords) {
        this.name = Objects.requireNonNull(name, "Domain name cannot be null");
        this.description = description != null ? description : "";
        this.patterns = patterns != null ? new ArrayList<>(patterns) : new ArrayList<>();
        this.semanticKeywords = semanticKeywords != null ? new ArrayList<>(semanticKeywords) : new ArrayList<>();
    }
    
    /**
     * Builder para criar instâncias DomainDefinition de forma fluida.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String name;
        private String description;
        private List<String> patterns = new ArrayList<>();
        private List<String> semanticKeywords = new ArrayList<>();
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder patterns(List<String> patterns) {
            this.patterns = new ArrayList<>(patterns);
            return this;
        }
        
        public Builder addPattern(String pattern) {
            this.patterns.add(pattern);
            return this;
        }
        

        
        public Builder semanticKeywords(List<String> semanticKeywords) {
            this.semanticKeywords = new ArrayList<>(semanticKeywords);
            return this;
        }
        
        public Builder addSemanticKeyword(String keyword) {
            this.semanticKeywords.add(keyword);
            return this;
        }
        

        
        public DomainDefinition build() {
            return new DomainDefinition(name, description, patterns, semanticKeywords);
        }
    }
    
    // Getters básicos
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public List<String> getPatterns() {
        return List.copyOf(patterns);
    }
    

    
    public List<String> getSemanticKeywords() {
        return List.copyOf(semanticKeywords);
    }
    

    
    // Métodos de modificação
    
    public void addPattern(String pattern) {
        if (pattern != null && !pattern.trim().isEmpty() && !patterns.contains(pattern.trim())) {
            patterns.add(pattern.trim().toLowerCase());
        }
    }
    

    
    public void addSemanticKeyword(String keyword) {
        if (keyword != null && !keyword.trim().isEmpty() && !semanticKeywords.contains(keyword.trim())) {
            semanticKeywords.add(keyword.trim().toLowerCase());
        }
    }
    
    // Métodos de matching
    
    public boolean containsPattern(String pattern) {
        if (pattern == null) return false;
        return patterns.contains(pattern.trim().toLowerCase());
    }
    

    
    /**
     * Calcula similaridade baseada em padrões de texto usando matching simples.
     */
    public double calculatePatternMatch(String query) {
        if (query == null || query.trim().isEmpty()) return 0.0;
        
        String normalizedQuery = query.trim().toLowerCase();
        double maxScore = 0.0;
        
        // Verificar padrões exatos
        for (String pattern : patterns) {
            if (normalizedQuery.contains(pattern)) {
                double score = (double) pattern.length() / normalizedQuery.length();
                maxScore = Math.max(maxScore, score);
            }
        }
        
        // Verificar palavras-chave semânticas
        for (String keyword : semanticKeywords) {
            if (normalizedQuery.contains(keyword)) {
                double score = (double) keyword.length() / normalizedQuery.length();
                maxScore = Math.max(maxScore, score * 0.8); // Peso menor para keywords
            }
        }
        
        return Math.min(1.0, maxScore);
    }
    
    /**
     * Calcula similaridade semântica usando LLM.
     * Este método deve ser usado quando matching simples não for suficiente.
     */
    public double calculateSemanticMatch(String query, Llm llm) {
        if (query == null || query.trim().isEmpty() || llm == null) {
            return calculatePatternMatch(query);
        }
        
        try {
            String prompt = createSemanticMatchingPrompt(query);
            var response = llm.generateResponse(prompt);
            
            if (response.isSuccess()) {
                return parseSemanticScore(response.getContent());
            }
        } catch (Exception e) {
            // Fallback para matching de padrões
        }
        
        return calculatePatternMatch(query);
    }
    
    // Métodos de serialização/deserialização
    
    public Map<String, Object> toConfigMap() {
        Map<String, Object> config = new HashMap<>();
        config.put("name", name);
        config.put("description", description);
        config.put("patterns", new ArrayList<>(patterns));
        config.put("semanticKeywords", new ArrayList<>(semanticKeywords));
        return config;
    }
    
    @SuppressWarnings("unchecked")
    public static DomainDefinition fromConfigMap(Map<String, Object> config) {
        Objects.requireNonNull(config, "Config map cannot be null");
        
        String name = (String) config.get("name");
        String description = (String) config.get("description");
        List<String> patterns = (List<String>) config.getOrDefault("patterns", Collections.emptyList());
        List<String> semanticKeywords = (List<String>) config.getOrDefault("semanticKeywords", Collections.emptyList());
        
        return new DomainDefinition(name, description, patterns, semanticKeywords);
    }
    
    // Métodos privados auxiliares
    
    private String createSemanticMatchingPrompt(String query) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analise se a seguinte query está relacionada ao domínio '").append(name).append("'.\n\n");
        prompt.append("Descrição do domínio: ").append(description).append("\n");
        prompt.append("Palavras-chave: ").append(String.join(", ", semanticKeywords)).append("\n");
        prompt.append("\n");
        prompt.append("Query: \"").append(query).append("\"\n\n");
        prompt.append("Responda apenas com um número entre 0.0 e 1.0 indicando o nível de relevância.");
        return prompt.toString();
    }
    
    private double parseSemanticScore(String llmResponse) {
        if (llmResponse == null) return 0.0;
        
        try {
            // Tentar extrair o primeiro número decimal da resposta
            String cleaned = llmResponse.trim().replaceAll("[^0-9.]", "");
            if (!cleaned.isEmpty()) {
                double score = Double.parseDouble(cleaned);
                return Math.max(0.0, Math.min(1.0, score)); // Garantir que está entre 0 e 1
            }
        } catch (NumberFormatException e) {
            // Fallback: procurar por palavras indicativas de alta/baixa relevância
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
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        DomainDefinition that = (DomainDefinition) obj;
        return Objects.equals(name, that.name);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
    
    @Override
    public String toString() {
        return String.format("DomainDefinition{name='%s', patterns=%d, keywords=%d}",
                           name, patterns.size(), semanticKeywords.size());
    }
}
