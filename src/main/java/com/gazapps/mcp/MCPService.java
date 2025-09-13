package com.gazapps.mcp;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gazapps.mcp.domain.Server;
import com.gazapps.mcp.domain.Tool;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpSchema.*;

public class MCPService {
    
    private static final Logger logger = LoggerFactory.getLogger(MCPService.class);
    
    private final Map<String, Server> servers;
    private final Map<String, McpSyncClient> clients;
    private final MCPConfig config;
    
    private int requestTimeoutSeconds = 15; 
    private int connectionTimeoutSeconds = 15; 
    private static final int MAX_RETRY_ATTEMPTS = 2;
    
    public MCPService(MCPConfig config) {
        this.config = Objects.requireNonNull(config, "MCP config cannot be null");
        this.servers = new ConcurrentHashMap<>();
        this.clients = new ConcurrentHashMap<>();
        
        initializeServers();
    }
    
    public boolean connectServer(MCPConfig.ServerConfig serverConfig) {
        Objects.requireNonNull(serverConfig, "Server config cannot be null");
        
        if (!serverConfig.enabled) {
            logger.info("Server '{}' is disabled, ignoring", serverConfig.id);
            return false;
        }
        
        if (!isCommandAvailable(serverConfig)) {
            logger.warn("Command '{}' not available for server '{}' - skipping", 
                       getMainCommand(serverConfig.command), serverConfig.id);
            return false;
        }
        
        try {
            logger.info("Connecting to MCP server '{}'...", serverConfig.id);
            
            Server server = serverConfig.toServer();
            McpSyncClient client = createMcpClient(serverConfig);
            
            client.initialize();
            loadServerTools(server, client);
            servers.put(server.getId(), server);
            clients.put(server.getId(), client);
            
            server.connect();
            logger.info("✅ Server '{}' connected successfully! Tools: {}", 
                       server.getId(), server.getTools().size());
            
            return true;
            
        } catch (Exception e) {
            logger.warn("❌ Failed to connect server '{}': {} - continuing without this server", 
                       serverConfig.id, e.getMessage());
            return false;
        }
    }
    
    private boolean isCommandAvailable(MCPConfig.ServerConfig serverConfig) {
        String command = serverConfig.command;
        String mainCommand = getMainCommand(command);
        
        logger.debug("Checking command '{}': verifying", mainCommand);
        
        try {
            ProcessBuilder pb = new ProcessBuilder();
            
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                pb.command("cmd.exe", "/c", "where", mainCommand);
            } else {
                pb.command("which", mainCommand);
            }
            
            Process process = pb.start();
            boolean finished = process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            
            if (!finished) {
                process.destroyForcibly();
                logger.debug("Checking command '{}': timeout", mainCommand);
                return false;
            }
            
            boolean available = process.exitValue() == 0;
            logger.debug("Checking command '{}': {}", mainCommand, available ? "available" : "unavailable");
            return available;
            
        } catch (Exception e) {
            logger.error("Error checking command '{}': {}", mainCommand, e.getMessage());
            return false;
        }
    }
    
    private String getMainCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return "";
        }
        return command.trim().split("\\s+")[0];
    }
    
    public void disconnectServer(String serverId) {
        Objects.requireNonNull(serverId, "Server ID cannot be null");
        
        try {
            McpSyncClient client = clients.remove(serverId);
            if (client != null) {
                client.close();
            }
            
            Server server = servers.get(serverId);
            if (server != null) {
                server.disconnect();
                logger.info("Server '{}' disconnected", serverId);
            }
            
        } catch (Exception e) {
            logger.error("Error disconnecting server '{}'", serverId, e);
        }
    }
    
    /**
     * Executes a tool on a specific server.
     */
    public ToolExecutionResult callTool(String serverId, String toolName, Map<String, Object> args) {
        Objects.requireNonNull(serverId, "Server ID cannot be null");
        Objects.requireNonNull(toolName, "Tool name cannot be null");
        
        if (args == null) {
            args = Collections.emptyMap();
        }
        
        Server server = servers.get(serverId);
        if (server == null) {
            return ToolExecutionResult.error("Server not found: " + serverId);
        }
        
        if (!server.isConnected()) {
            return ToolExecutionResult.error("Server is not connected: " + serverId);
        }
        
        Tool tool = server.getTool(toolName);
        if (tool == null) {
            return ToolExecutionResult.error("Tool not found: " + toolName + " on server " + serverId);
        }
        
        if (!tool.validateArgs(args)) {
            List<String> missingParams = tool.getMissingRequiredParams(args);
            return ToolExecutionResult.error("Invalid arguments for tool " + toolName + 
                                 ". Missing required parameters: " + missingParams);
        }
        
        Map<String, Object> normalizedArgs = tool.normalizeArgs(args);
        
        return executeToolWithRetry(server, tool, normalizedArgs);
    }
    
    public List<Tool> getAvailableTools(String serverId) {
        Objects.requireNonNull(serverId, "Server ID cannot be null");
        
        Server server = servers.get(serverId);
        if (server == null) {
            return Collections.emptyList();
        }
        
        return server.getTools();
    }
    
    public List<Tool> getAllAvailableTools() {
        List<Tool> allTools = new ArrayList<>();
        
        for (Server server : servers.values()) {
            if (server.isConnected()) {
                allTools.addAll(server.getTools());
            }
        }
        
        return allTools;
    }
    
    public boolean isServerConnected(String serverId) {
        Server server = servers.get(serverId);
        return server != null && server.isConnected();
    }
    
    public Server getServerInfo(String serverId) {
        return servers.get(serverId);
    }
    
    public Map<String, Server> getConnectedServers() {
        Map<String, Server> connected = new HashMap<>();
        
        for (Map.Entry<String, Server> entry : servers.entrySet()) {
            if (entry.getValue().isConnected()) {
                connected.put(entry.getKey(), entry.getValue());
            }
        }
        
        return connected;
    }
    
    public Map<String, Server> getAllServers() {
        return new HashMap<>(servers);
    }
    
    public boolean validateToolCall(String toolName, Map<String, Object> args) {
        if (toolName == null || toolName.trim().isEmpty()) {
            return false;
        }
        
        for (Server server : servers.values()) {
            if (server.isConnected() && server.hasTool(toolName)) {
                Tool tool = server.getTool(toolName);
                return tool.validateArgs(args);
            }
        }
        
        return false;
    }
    
    public void refreshServerConnections() {
        logger.info("Updating server connections...");
        
        Map<String, MCPConfig.ServerConfig> serverConfigs = config.loadServers();
        
        for (MCPConfig.ServerConfig serverConfig : serverConfigs.values()) {
            if (!servers.containsKey(serverConfig.id)) {
                connectServer(serverConfig);
            }
        }
        
        List<String> unhealthyServers = new ArrayList<>();
        for (Map.Entry<String, Server> entry : servers.entrySet()) {
            String serverId = entry.getKey();
            Server server = entry.getValue();
            
            if (!server.isHealthy()) {
                unhealthyServers.add(serverId);
            } else {
                server.updateHeartbeat();
            }
        }
        
        int reconnectAttempts = 0;
        for (String serverId : unhealthyServers) {
            if (reconnectAttempts >= 2) {
                logger.debug("Reconnection attempt limit reached");
                break;
            }
            
            MCPConfig.ServerConfig serverConfig = serverConfigs.get(serverId);
            if (serverConfig != null) {
                logger.debug("Attempting to reconnect unhealthy server: {}", serverId);
                disconnectServer(serverId);
                if (connectServer(serverConfig)) {
                    reconnectAttempts++;
                }
            }
        }
        
        int connectedCount = getConnectedServers().size();
        int totalCount = serverConfigs.size();
        logger.info("Update completed. Servers: {}/{} connected", connectedCount, totalCount);
    }
    
    public boolean removeServerFromMemory(String serverId) {
        try {
            Server removed = servers.remove(serverId);
            return removed != null;
        } catch (Exception e) {
            logger.error("Error removing server from memory: '{}'", serverId, e);
            return false;
        }
    }
    
    public void close() {
        logger.info("Closing MCPService...");
        
        for (String serverId : new ArrayList<>(servers.keySet())) {
            disconnectServer(serverId);
        }
        
        servers.clear();
        clients.clear();
        
        logger.info("MCPService closed");
    }
    
    private void initializeServers() {
        Map<String, MCPConfig.ServerConfig> serverConfigs = config.loadServers();
        
        logger.info("Initializing {} MCP servers...", serverConfigs.size());
        System.out.printf("Initializing %d MCP servers...\n", serverConfigs.size());
               
        int successCount = 0;
        for (MCPConfig.ServerConfig serverConfig : serverConfigs.values()) {
            if (serverConfig.enabled) {
                if (connectServer(serverConfig)) {
                    successCount++;
                } else {
                    System.out.println("[DEBUG] ❌ Failed to connect server " + serverConfig.id);
                }
            } else {
                logger.info("Server '{}' disabled in configuration", serverConfig.id);
            }
        }
        logger.info("MCPService initialized: {}/{} servers connected - logs in JavaCLI/log/mcp-operations.log", 
                   successCount, serverConfigs.size());
        
        if (successCount == 0) {
            String message = """
                [DEBUG] ⚠️ WARNING: No MCP server connected!
                   This may happen if:
                   - Node.js is not installed (for weather/filesystem servers)
                   - Python/uvx is not installed (for time server)
                   - The commands are not in the system's PATH
                   The system will continue operating with limited functionality.
                """;
            System.out.println(message);
            logger.warn(message);
        }
    }
    
    private McpSyncClient createMcpClient(MCPConfig.ServerConfig serverConfig) throws Exception {
        try {
            String[] command;
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                command = new String[]{"cmd.exe", "/c", serverConfig.command};
            } else {
                command = serverConfig.command.split(" ");
            }
            
            logger.debug("Executing command: {}", Arrays.toString(command));
            
            ServerParameters serverParams = ServerParameters.builder(command[0])
                .args(Arrays.copyOfRange(command, 1, command.length))
                .env(serverConfig.env)
                .build();
            
            StdioClientTransport stdioTransport = new StdioClientTransport(serverParams);
            
            return McpClient.sync(stdioTransport)
                .requestTimeout(Duration.ofSeconds(requestTimeoutSeconds))
                .build();
                
        } catch (Exception e) {
            logger.error("Error creating MCP client for server '{}'", serverConfig.id, e);
            throw new Exception("Error creating MCP client: " + e.getMessage(), e);
        }
    }
    
    private String[] parseCommand(String command) {
        return command.split("\\s+");
    }
    
    private void loadServerTools(Server server, McpSyncClient client) {
        try {
            ListToolsResult toolsResult = client.listTools();
            for (io.modelcontextprotocol.spec.McpSchema.Tool mcpTool : toolsResult.tools()) {
                Tool tool = Tool.fromMcp(mcpTool, server.getId());
                tool.setDomain(server.getDomain()); 
                server.addTool(tool);
            }
            
            logger.debug("Loaded {} tools for server '{}'", 
                        toolsResult.tools().size(), server.getId());
            
        } catch (Exception e) {
            logger.error("Error loading tools for server '{}'", server.getId(), e);
        }
    }
    
    private ToolExecutionResult executeToolWithRetry(Server server, Tool tool, Map<String, Object> args) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return executeToolDirect(server, tool, args);
                
            } catch (Exception e) {
                lastException = e;
                logger.error("Attempt {} of {} failed for tool '{}': {}", 
                           attempt, MAX_RETRY_ATTEMPTS, tool.getName(), e.getMessage());
                
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(1000 * attempt); 
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        return ToolExecutionResult.error("Failed to execute tool '" + tool.getName() + 
                             "' after " + MAX_RETRY_ATTEMPTS + " attempts", lastException);
    }
    
    private ToolExecutionResult executeToolDirect(Server server, Tool tool, Map<String, Object> args) throws Exception {
        McpSyncClient client = clients.get(server.getId());
        if (client == null) {
            throw new Exception("Client not found for server: " + server.getId());
        }
        CallToolRequest request = new CallToolRequest(tool.getName(), args);
        CallToolResult result = client.callTool(request);
        Boolean isError = result.isError();
        if (isError != null && isError) {
            throw new Exception("Error executing tool: " + result.toString());
        }
        
        String content = extractContentAsString(result.content());
        server.updateHeartbeat();
        
        return ToolExecutionResult.success(tool, content);
    }
    
    private String extractContentAsString(List<Content> contentList) {
        if (contentList == null || contentList.isEmpty()) {
            return "No return message";
        }

        for (Content content : contentList) {
            if (content instanceof TextContent textContent) {
                if (textContent.text() != null && !textContent.text().trim().isEmpty()) {
                    return textContent.text();
                }
            }
        }

        return "No message found";
    }
    
    public MCPStats getStats() {
        int totalServers = servers.size();
        int connectedServers = (int) servers.values().stream().filter(Server::isConnected).count();
        int totalTools = getAllAvailableTools().size();
        
        Map<String, Integer> toolsByDomain = new HashMap<>();
        for (Tool tool : getAllAvailableTools()) {
            toolsByDomain.merge(tool.getDomain(), 1, Integer::sum);
        }
        
        return new MCPStats(totalServers, connectedServers, totalTools, toolsByDomain);
    }
    
    public static class MCPStats {
        public final int totalServers;
        public final int connectedServers;
        public final int totalTools;
        public final Map<String, Integer> toolsByDomain;
        
        public MCPStats(int totalServers, int connectedServers, int totalTools, Map<String, Integer> toolsByDomain) {
            this.totalServers = totalServers;
            this.connectedServers = connectedServers;
            this.totalTools = totalTools;
            this.toolsByDomain = Map.copyOf(toolsByDomain);
        }
        
        @Override
        public String toString() {
            return String.format("MCPStats{servers: %d/%d, tools: %d, domains: %d}", 
                               connectedServers, totalServers, totalTools, toolsByDomain.size());
        }
    }
    
    public static class ToolExecutionResult {
        public final boolean success;
        public final Tool tool;
        public final String content;
        public final String message;
        public final Exception error;
        
        private ToolExecutionResult(boolean success, Tool tool, String content, String message, Exception error) {
            this.success = success;
            this.tool = tool;
            this.content = content;
            this.message = message;
            this.error = error;
        }
        
        public static ToolExecutionResult success(Tool tool, String content) {
            return new ToolExecutionResult(true, tool, content, "Tool executed successfully", null);
        }
        
        public static ToolExecutionResult error(String message) {
            return new ToolExecutionResult(false, null, null, message, null);
        }
        
        public static ToolExecutionResult error(String message, Exception error) {
            return new ToolExecutionResult(false, null, null, message, error);
        }
        
        @Override
        public String toString() {
            if (success) {
                return String.format("ToolExecutionResult{success=true, tool='%s', content='%s'}", 
                                   tool != null ? tool.getName() : "unknown", 
                                   content != null ? (content.length() > 100 ? content.substring(0, 100) + "..." : content) : "empty");
            } else {
                return String.format("ToolExecutionResult{success=false, message='%s'}", message);
            }
        }
    }
}