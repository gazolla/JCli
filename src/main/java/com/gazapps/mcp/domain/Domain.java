package com.gazapps.mcp.domain;

import java.util.*;

public class Domain {
    
    private final String name;
    private final String description;
    private final List<String> patterns;
    private final List<String> semanticKeywords;
    
    public Domain(String name, String description, List<String> patterns, List<String> semanticKeywords) {
        this.name = Objects.requireNonNull(name, "Domain name cannot be null");
        this.description = description != null ? description : "";
        this.patterns = patterns != null ? new ArrayList<>(patterns) : new ArrayList<>();
        this.semanticKeywords = semanticKeywords != null ? new ArrayList<>(semanticKeywords) : new ArrayList<>();
    }
    
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
        

        
        public Domain build() {
            return new Domain(name, description, patterns, semanticKeywords);
        }
    }
    
     
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
    
    public boolean containsPattern(String pattern) {
        if (pattern == null) return false;
        return patterns.contains(pattern.trim().toLowerCase());
    }
    
    public double calculatePatternMatch(String query) {
        if (query == null || query.trim().isEmpty()) return 0.0;
        
        String normalizedQuery = query.trim().toLowerCase();
        double maxScore = 0.0;
        
        for (String pattern : patterns) {
            if (normalizedQuery.contains(pattern)) {
                double score = (double) pattern.length() / normalizedQuery.length();
                maxScore = Math.max(maxScore, score);
            }
        }
        
        for (String keyword : semanticKeywords) {
            if (normalizedQuery.contains(keyword)) {
                double score = (double) keyword.length() / normalizedQuery.length();
                maxScore = Math.max(maxScore, score * 0.8); 
            }
        }
        
        return Math.min(1.0, maxScore);
    }
    
    public Map<String, Object> toConfigMap() {
        Map<String, Object> config = new HashMap<>();
        config.put("name", name);
        config.put("description", description);
        config.put("patterns", new ArrayList<>(patterns));
        config.put("semanticKeywords", new ArrayList<>(semanticKeywords));
        return config;
    }
    
    @SuppressWarnings("unchecked")
    public static Domain fromConfigMap(Map<String, Object> config) {
        Objects.requireNonNull(config, "Config map cannot be null");
        
        String name = (String) config.get("name");
        String description = (String) config.get("description");
        List<String> patterns = (List<String>) config.getOrDefault("patterns", Collections.emptyList());
        List<String> semanticKeywords = (List<String>) config.getOrDefault("semanticKeywords", Collections.emptyList());
        
        return new Domain(name, description, patterns, semanticKeywords);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Domain that = (Domain) obj;
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
