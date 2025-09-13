package com.gazapps.mcp.domain;

import java.time.Instant;
import java.util.*;

public class Server {
    
    private final String id;
    private final String name;
    private final String description;
    private final String command;
    private final List<String> args;
    private final Map<String, String> env;
    private final int priority;
    private final boolean enabled;
    
    private boolean connected;
    private String domain;
    private final Map<String, Tool> tools;
    private Instant lastHeartbeat;
    private Map<String, Object> metrics;
    
    public Server(String id, String name, String description, String command,
                 List<String> args, Map<String, String> env, int priority, boolean enabled) {
        this.id = Objects.requireNonNull(id, "Server ID cannot be null");
        this.name = Objects.requireNonNull(name, "Server name cannot be null");
        this.description = description != null ? description : "";
        this.command = Objects.requireNonNull(command, "Server command cannot be null");
        this.args = args != null ? List.copyOf(args) : Collections.emptyList();
        this.env = env != null ? Map.copyOf(env) : Collections.emptyMap();
        this.priority = priority;
        this.enabled = enabled;
        
        this.connected = false;
        this.tools = new HashMap<>();
        this.lastHeartbeat = null;
        this.metrics = new HashMap<>();
        this.domain = null; 
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private String name;
        private String description;
        private String command;
        private List<String> args = Collections.emptyList();
        private Map<String, String> env = Collections.emptyMap();
        private int priority = 1;
        private boolean enabled = true;
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder command(String command) {
            this.command = command;
            return this;
        }
        
        public Builder args(List<String> args) {
            this.args = args;
            return this;
        }
        
        public Builder env(Map<String, String> env) {
            this.env = env;
            return this;
        }
        
        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }
        
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
        
        public Server build() {
            return new Server(id, name, description, command, args, env, priority, enabled);
        }
    }
    

    public String getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getCommand() {
        return command;
    }
    
    public List<String> getArgs() {
        return args;
    }
    
    public Map<String, String> getEnv() {
        return env;
    }
    
    public int getPriority() {
        return priority;
    }
    
    public boolean isEnabled() {
        return enabled;
    }

    public boolean isConnected() {
        return connected;
    }
    
    public void setConnected(boolean connected) {
        this.connected = connected;
        if (connected) {
            this.lastHeartbeat = Instant.now();
        }
    }
    
    public List<Tool> getTools() {
        return new ArrayList<>(tools.values());
    }
    
    public void addTool(Tool tool) {
        Objects.requireNonNull(tool, "Tool cannot be null");
        tools.put(tool.getName(), tool);
    }
    
    public void removeTool(String toolName) {
        tools.remove(toolName);
    }
    
    public Tool getTool(String toolName) {
        return tools.get(toolName);
    }
    
    public boolean hasTool(String toolName) {
        return tools.containsKey(toolName);
    }
    
    public boolean isHealthy() {
        if (!connected) return false;
        if (lastHeartbeat == null) return false;
        
        return Instant.now().minusSeconds(60).isBefore(lastHeartbeat);
    }
    
    public void connect() {
        setConnected(true);
    }
    
    public void disconnect() {
        setConnected(false);
        tools.clear();
    }
    
    public Map<String, Object> getMetrics() {
        return Map.copyOf(metrics);
    }
    
    public void updateMetrics(Map<String, Object> newMetrics) {
        if (newMetrics != null) {
            this.metrics = new HashMap<>(newMetrics);
        }
    }
    
    public long getLastHeartbeat() {
        return lastHeartbeat != null ? lastHeartbeat.getEpochSecond() : 0;
    }
    
    public void updateHeartbeat() {
        this.lastHeartbeat = Instant.now();
    }
    
    public String getDomain() {
        return domain;
    }
    
    public void setDomain(String domain) {
        this.domain = domain;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Server server = (Server) obj;
        return Objects.equals(id, server.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return String.format("Server{id='%s', name='%s', connected=%s, tools=%d, healthy=%s}",
                           id, name, connected, tools.size(), isHealthy());
    }
}
