package com.gazapps.session;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gazapps.inference.InferenceStrategy;
import com.gazapps.llm.LlmProvider;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Encapsulates all mutable configuration for a session.
 * Following KISS principle: simple container with change tracking.
 */
public final class SessionConfig {
    
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    
    private final LlmProvider llmProvider;
    private final InferenceStrategy inferenceStrategy;
    private final Set<String> enabledMcpServers;
    private final List<ConfigChange> changeHistory;
    
    @JsonCreator
    public SessionConfig(
            @JsonProperty("llmProvider") LlmProvider llmProvider,
            @JsonProperty("inferenceStrategy") InferenceStrategy inferenceStrategy,
            @JsonProperty("enabledMcpServers") Set<String> enabledMcpServers,
            @JsonProperty("changeHistory") List<ConfigChange> changeHistory) {
        this.llmProvider = Objects.requireNonNull(llmProvider, "LLM provider cannot be null");
        this.inferenceStrategy = Objects.requireNonNull(inferenceStrategy, "Inference strategy cannot be null");
        this.enabledMcpServers = enabledMcpServers != null ? Set.copyOf(enabledMcpServers) : Set.of();
        this.changeHistory = changeHistory != null ? new ArrayList<>(changeHistory) : new ArrayList<>();
    }
    
    // Convenience constructor
    public SessionConfig(LlmProvider llmProvider, InferenceStrategy inferenceStrategy, Set<String> enabledMcpServers) {
        this(llmProvider, inferenceStrategy, enabledMcpServers, new ArrayList<>());
    }
    
    // Getters
    public LlmProvider getLlmProvider() { return llmProvider; }
    public InferenceStrategy getInferenceStrategy() { return inferenceStrategy; }
    public Set<String> getEnabledMcpServers() { return enabledMcpServers; }
    public List<ConfigChange> getChangeHistory() { return List.copyOf(changeHistory); }
    
    /**
     * Creates a new SessionConfig with updated LLM provider.
     */
    public SessionConfig withLlmProvider(LlmProvider newProvider) {
        if (this.llmProvider == newProvider) {
            return this; // No change needed
        }
        
        List<ConfigChange> newHistory = new ArrayList<>(changeHistory);
        newHistory.add(new ConfigChange("llm_provider", 
                                      llmProvider.name(), 
                                      newProvider.name(), 
                                      LocalDateTime.now()));
        
        return new SessionConfig(newProvider, inferenceStrategy, enabledMcpServers, newHistory);
    }
    
    /**
     * Creates a new SessionConfig with updated inference strategy.
     */
    public SessionConfig withInferenceStrategy(InferenceStrategy newStrategy) {
        if (this.inferenceStrategy == newStrategy) {
            return this; // No change needed
        }
        
        List<ConfigChange> newHistory = new ArrayList<>(changeHistory);
        newHistory.add(new ConfigChange("inference_strategy", 
                                      inferenceStrategy.name(), 
                                      newStrategy.name(), 
                                      LocalDateTime.now()));
        
        return new SessionConfig(llmProvider, newStrategy, enabledMcpServers, newHistory);
    }
    
    /**
     * Creates a new SessionConfig with updated MCP servers.
     */
    public SessionConfig withMcpServers(Set<String> newServers) {
        if (this.enabledMcpServers.equals(newServers)) {
            return this; // No change needed
        }
        
        List<ConfigChange> newHistory = new ArrayList<>(changeHistory);
        newHistory.add(new ConfigChange("mcp_servers", 
                                      enabledMcpServers.toString(), 
                                      newServers.toString(), 
                                      LocalDateTime.now()));
        
        return new SessionConfig(llmProvider, inferenceStrategy, newServers, newHistory);
    }
    
    /**
     * Creates a new SessionConfig with an added MCP server.
     */
    public SessionConfig withAddedMcpServer(String serverId) {
        Set<String> newServers = new java.util.HashSet<>(enabledMcpServers);
        newServers.add(serverId);
        return withMcpServers(newServers);
    }
    
    /**
     * Creates a new SessionConfig with a removed MCP server.
     */
    public SessionConfig withRemovedMcpServer(String serverId) {
        Set<String> newServers = new java.util.HashSet<>(enabledMcpServers);
        newServers.remove(serverId);
        return withMcpServers(newServers);
    }
    
    /**
     * Checks if configuration has changed compared to another SessionConfig.
     */
    public boolean hasChangedFrom(SessionConfig other) {
        if (other == null) return true;
        
        return !this.llmProvider.equals(other.llmProvider) ||
               !this.inferenceStrategy.equals(other.inferenceStrategy) ||
               !this.enabledMcpServers.equals(other.enabledMcpServers);
    }
    
    /**
     * Serializes configuration to JSON for persistence.
     */
    public String toJson() {
        try {
            return JSON_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize SessionConfig to JSON", e);
        }
    }
    
    /**
     * Deserializes configuration from JSON.
     */
    public static SessionConfig fromJson(String json) {
        try {
            return JSON_MAPPER.readValue(json, SessionConfig.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize SessionConfig from JSON", e);
        }
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        SessionConfig that = (SessionConfig) obj;
        return Objects.equals(llmProvider, that.llmProvider) &&
               Objects.equals(inferenceStrategy, that.inferenceStrategy) &&
               Objects.equals(enabledMcpServers, that.enabledMcpServers);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(llmProvider, inferenceStrategy, enabledMcpServers);
    }
    
    @Override
    public String toString() {
        return String.format("SessionConfig{llm=%s, strategy=%s, mcpServers=%s, changes=%d}", 
                           llmProvider, inferenceStrategy, enabledMcpServers, changeHistory.size());
    }
    
    /**
     * Represents a configuration change for audit purposes.
     */
    public static final class ConfigChange {
        private final String field;
        private final String oldValue;
        private final String newValue;
        private final LocalDateTime timestamp;
        
        @JsonCreator
        public ConfigChange(
                @JsonProperty("field") String field,
                @JsonProperty("oldValue") String oldValue,
                @JsonProperty("newValue") String newValue,
                @JsonProperty("timestamp") LocalDateTime timestamp) {
            this.field = field;
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.timestamp = timestamp;
        }
        
        public String getField() { return field; }
        public String getOldValue() { return oldValue; }
        public String getNewValue() { return newValue; }
        public LocalDateTime getTimestamp() { return timestamp; }
        
        @Override
        public String toString() {
            return String.format("%s: %s → %s (%s)", field, oldValue, newValue, timestamp);
        }
    }
}
