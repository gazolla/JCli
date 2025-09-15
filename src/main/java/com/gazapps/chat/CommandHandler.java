package com.gazapps.chat;

import com.gazapps.chat.wizard.ServerWizard;
import com.gazapps.inference.InferenceStrategy;
import com.gazapps.llm.Llm;
import com.gazapps.llm.LlmBuilder;
import com.gazapps.mcp.MCPManager;
import com.gazapps.mcp.domain.Server;
import com.gazapps.session.SessionManager;
import com.gazapps.session.SessionPersistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CommandHandler {

    private final ChatProcessor chatProcessor;
    private final MCPManager mcpManager;
    private final SessionManager sessionManager;

    public CommandHandler(ChatProcessor chatProcessor, MCPManager mcpManager, SessionManager sessionManager) {
        this.chatProcessor = chatProcessor;
        this.mcpManager = mcpManager;
        this.sessionManager = sessionManager;
    }

    public void handle(String command) {
        String[] parts = command.substring(1).split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";

        switch (cmd) {
            case "help" -> showHelp();
            case "status" -> showStatus();
            case "tools" -> showTools();
            case "servers" -> showServers();
            case "strategy" -> changeStrategy(args);
            case "llm" -> changeLlm(args);
            case "debug" -> toggleDebug();
            case "clear" -> clearScreen();
            case "disable" -> disableServer(args);
            case "enable" -> enableServer(args);
            case "addserver" -> addNewServer();
            case "session" -> handleSessionCommand(args);
            case "context" -> handleContextCommand(args);
            case "quit", "exit" -> System.exit(0);
            default -> showUnknownCommand(cmd);
        }
    }

    private void changeLlm(String providerName) {
        if (providerName.trim().isEmpty()) {
            System.out.println("❌ LLM provider name required. Available: openai, claude, gemini, groq");
            System.out.printf("   Current provider: %s%n", chatProcessor.getCurrentLlm().getProviderName());
            return;
        }

        String provider = providerName.toLowerCase().trim();
        String currentProvider = chatProcessor.getCurrentLlm().getProviderName().toString().toLowerCase();

        if (currentProvider.equals(provider)) {
            System.out.printf("✅ Already using %s%n", provider);
            return;
        }

        try {
            System.out.printf("🔄 Switching from %s to %s...%n", currentProvider, provider);

            Llm newLlm = switch (provider) {
                case "openai" -> LlmBuilder.openai(null);
                case "claude" -> LlmBuilder.claude(null);
                case "gemini" -> LlmBuilder.gemini(null);
                case "groq" -> LlmBuilder.groq(null);
                default -> {
                    System.out.printf("❌ Unknown LLM provider: %s%n", provider);
                    System.out.println("   Available providers: openai, claude, gemini, groq");
                    yield null;
                }
            };

            if (newLlm != null) {
                boolean success = chatProcessor.changeLlm(newLlm);

                if (success) {
                    System.out.printf("✅ Successfully switched to %s%n", provider);

                    System.out.printf("   Provider: %s%n", newLlm.getProviderName());
                    if (newLlm.getCapabilities() != null) {
                        System.out.printf("   Capabilities: %s%n", newLlm.getCapabilities());
                    }
                } else {
                    System.out.println("❌ Failed to switch LLM provider");
                    System.out.printf("   Staying with %s%n", currentProvider);
                }
            }

        } catch (Exception e) {
            System.out.printf("❌ Error switching LLM: %s%n", e.getMessage());
            System.out.printf("   Staying with %s%n", currentProvider);
        }
    }

    private void showHelp() {
        System.out.println("""
                🔧 JCli Commands:

                /help                    - Show this help
                /status                  - System status
                /tools                   - List MCP tools
                /servers                 - List MCP servers
                /strategy <name>         - Change inference (simple|react|reflection)
                /llm <provider>          - Change LLM provider (openai|claude|gemini|groq)
                /disable [num]           - Disable server (removes tools from LLM)
                /enable [num]            - Enable server (adds tools to LLM)
                /addserver               - Add new MCP server (wizard)
                /debug                   - Toggle debug mode
                /clear                   - Clear screen
                /quit                    - Exit

                💡 Just type your questions naturally!
                """);
    }

    private void showStatus() {
        var servers = mcpManager.getConnectedServers();
        var domains = mcpManager.getAvailableDomains();

        System.out.printf("""
                📊 System Status:
                🖥️ Servers: %d connected
                🛠️ Tools: Available across %d domains
                🤖 LLM: %s
                🧠 Strategy: %s
                🔧 Debug: %s
                ⚡ Health: %s
                %n""", servers.size(), domains.size(), chatProcessor.getCurrentLlm().getProviderName(),
                chatProcessor.getCurrentStrategy().name().toLowerCase(), chatProcessor.isDebugMode() ? "ON" : "OFF",
                mcpManager.isHealthy() ? "✅ Healthy" : "❌ Issues");
    }

    private void showTools() {
        var domains = mcpManager.getAvailableDomains();

        System.out.println("🔧 Available Tools:");
        for (String domain : domains) {
            var tools = mcpManager.getToolsByDomain(domain);
            if (!tools.isEmpty()) {
                System.out.printf("%n📁 %s Domain:%n", domain);
                tools.forEach(tool -> System.out.printf("  • %s - %s%n", tool.getName(), tool.getDescription()));
            }
        }
        System.out.println();
    }

    private void showServers() {
        var servers = mcpManager.getConnectedServers();

        System.out.println("🖥️ MCP Servers:");
        if (servers.isEmpty()) {
            System.out.println("  No servers connected");
        } else {
            servers.values().forEach(server -> System.out.printf("  • %s (%s) - %d tools%n", server.getId(),
                    server.isHealthy() ? "✅" : "❌", server.getTools().size()));
        }
        System.out.println();
    }

    private void changeStrategy(String strategyName) {
        if (strategyName.trim().isEmpty()) {
            System.out.println("❌ Strategy name required. Available: simple, react, reflection");
            return;
        }

        try {
            InferenceStrategy strategy = InferenceStrategy.fromString(strategyName);
            chatProcessor.setCurrentStrategy(strategy);
            System.out.printf("✅ Strategy changed to: %s%n", strategy.name().toLowerCase());
        } catch (IllegalArgumentException e) {
            System.out.println("❌ Invalid strategy. Available: simple, react, reflection");
        }
    }

    private void toggleDebug() {
        boolean oldState = chatProcessor.isDebugMode();
        boolean newState = chatProcessor.toggleDebug();
        System.out.printf("🔧 Debug mode: %s → %s%n", oldState ? "ON" : "OFF", newState ? "ON" : "OFF");
    }

    private void clearScreen() {
        System.out.print("\\033[2J\\033[H");
        System.out.flush();
    }

    private void disableServer(String args) {
        if (args.trim().isEmpty()) {
            showServerListForAction("disable", "disable");
            return;
        }

        try {
            int serverIndex = Integer.parseInt(args.trim());
            List<String> serverIds = getConnectedServerIds();

            if (serverIndex < 1 || serverIndex > serverIds.size()) {
                System.out.println("❌ Invalid number");
                return;
            }

            String serverId = serverIds.get(serverIndex - 1);

            System.out.printf("🔄 Disabling server '%s'...%n", serverId);

            if (mcpManager.disableServer(serverId)) {
                System.out.printf("✅ Server '%s' disabled successfully!%n", serverId);
                System.out.println("   • Tools removed from LLM collections");
                System.out.println("   • Server disconnected");
                System.out.println("   • mcp.json updated");
            } else {
                System.out.printf("❌ Failed to disable server '%s'%n", serverId);
            }

        } catch (NumberFormatException e) {
            System.out.println("❌ Use a valid number or /disable without arguments to list");
        }
    }

    private void enableServer(String args) {
        if (args.trim().isEmpty()) {
            showDisabledServers();
            return;
        }

        try {
            int serverIndex = Integer.parseInt(args.trim());
            List<String> disabledIds = mcpManager.getDisabledServerIds();

            if (serverIndex < 1 || serverIndex > disabledIds.size()) {
                System.out.println("❌ Invalid number");
                return;
            }

            String serverId = disabledIds.get(serverIndex - 1);

            System.out.printf("🔄 Enabling server '%s'...%n", serverId);

            if (mcpManager.enableServer(serverId)) {
                System.out.printf("✅ Server '%s' enabled successfully!%n", serverId);
                System.out.println("   • Server connected");
                System.out.println("   • Tools added to LLM collections");
                System.out.println("   • mcp.json updated");
            } else {
                System.out.printf("❌ Failed to enable server '%s'%n", serverId);
            }

        } catch (NumberFormatException e) {
            System.out.println("❌ Use a valid number or /enable without arguments to list");
        }
    }

    private void showServerListForAction(String action, String command) {
        Map<String, Server> servers = mcpManager.getConnectedServers();

        System.out.printf("🔧 Servers Available to %s:%n%n", action);

        if (servers.isEmpty()) {
            System.out.println("  No servers connected");
            return;
        }

        int index = 1;
        for (Map.Entry<String, Server> entry : servers.entrySet()) {
            String id = entry.getKey();
            Server server = entry.getValue();
            String status = server.isHealthy() ? "✅" : "❌";

            System.out.printf("[%d] %s (%s) - %d tools %s%n",
                    index++, id, server.getDomain(),
                    server.getTools().size(), status);
        }

        System.out.printf("%nType /%s [number] to %s%n", command, action);
    }

    private void showDisabledServers() {
        List<String> disabledIds = mcpManager.getDisabledServerIds();

        System.out.println("🔧 Disabled Servers:\n");

        if (disabledIds.isEmpty()) {
            System.out.println("  No servers disabled");
            return;
        }

        for (int i = 0; i < disabledIds.size(); i++) {
            String id = disabledIds.get(i);
            System.out.printf("[%d] %s%n", i + 1, id);
        }

        System.out.println("\nType /enable [number] to enable");
    }

    private void addNewServer() {
        ServerWizard wizard = new ServerWizard(mcpManager);
        boolean success = wizard.runWizard();

        if (success) {
            System.out.println("✅ Server added and connected successfully!");
            System.out.println("   • mcp.json updated");
            System.out.println("   • domains.json updated (if necessary)");
            System.out.println("   • Tools available for the LLM");
            System.out.println("💡 Use /servers to check status");
        } else {
            System.out.println("❌ Operation canceled or failed");
        }
    }

    private List<String> getConnectedServerIds() {
        return new ArrayList<>(mcpManager.getConnectedServers().keySet());
    }
    
    // ===== SESSION COMMANDS =====
    
    private void handleSessionCommand(String args) {
        String[] parts = args.trim().split("\\s+", 2);
        if (parts.length == 0 || parts[0].isEmpty()) {
            showSessionHelp();
            return;
        }
        
        String subCommand = parts[0].toLowerCase();
        String subArgs = parts.length > 1 ? parts[1] : "";
        
        switch (subCommand) {
            case "new" -> createNewSession(subArgs);
            case "load" -> loadSession(subArgs);
            case "list" -> listSessions();
            case "save" -> saveCurrentSession();
            case "delete" -> deleteSession(subArgs);
            default -> {
                System.out.printf("❌ Unknown session command: %s%n", subCommand);
                showSessionHelp();
            }
        }
    }
    
    private void handleContextCommand(String args) {
        String[] parts = args.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            showContextHelp();
            return;
        }
        
        String subCommand = parts[0].toLowerCase();
        
        switch (subCommand) {
            case "clear" -> clearSessionHistory();
            default -> {
                System.out.printf("❌ Unknown context command: %s%n", subCommand);
                showContextHelp();
            }
        }
    }
    
    private void showSessionHelp() {
        System.out.println("""
                💬 Session Commands:
                /session new [name]      - Create new session
                /session load <id>       - Load existing session  
                /session list            - List all sessions
                /session save            - Save current session
                /session delete <id>     - Delete session
                """);
    }
    
    private void showContextHelp() {
        System.out.println("""
                📝 Context Commands:
                /context clear           - Clear current session history
                """);
    }
    
    private void createNewSession(String name) {
        if (name.trim().isEmpty()) {
            name = "session-" + System.currentTimeMillis();
        }
        
        try {
            var session = sessionManager.createNewSession(
                name.trim(),
                chatProcessor.getCurrentLlm(),
                chatProcessor.getCurrentStrategy(),
                mcpManager
            );
            
            System.out.printf("✅ Created new session '%s'%n", session.getName());
            System.out.printf("   ID: %s%n", session.getId());
            System.out.println("   • Session is now active");
            System.out.println("   • All conversations will be saved");
            
        } catch (Exception e) {
            System.out.printf("❌ Failed to create session: %s%n", e.getMessage());
        }
    }
    
    private void loadSession(String sessionId) {
        if (sessionId.trim().isEmpty()) {
            System.out.println("❌ Session ID required");
            System.out.println("Use /session list to see available sessions");
            return;
        }
        
        Optional<com.gazapps.session.Session> session = sessionManager.loadSession(sessionId.trim());
        
        if (session.isPresent()) {
            var s = session.get();
            System.out.printf("✅ Loaded session '%s'%n", s.getName());
            System.out.printf("   ID: %s%n", s.getId());
            System.out.printf("   Messages: %d%n", s.getMessages().size());
            System.out.printf("   Total tokens: %d%n", s.getTotalTokens());
            System.out.printf("   Last access: %s%n", s.getLastAccessAt());
        } else {
            System.out.printf("❌ Session not found: %s%n", sessionId);
            System.out.println("Use /session list to see available sessions");
        }
    }
    
    private void listSessions() {
        List<SessionPersistence.SessionMetadata> sessions = sessionManager.listSessions();
        
        if (sessions.isEmpty()) {
            System.out.println("💬 No sessions found");
            System.out.println("Use /session new to create your first session");
            return;
        }
        
        System.out.println("💬 Available Sessions:");
        System.out.println();
        
        var currentSession = sessionManager.getCurrentSession();
        String currentId = currentSession.map(s -> s.getId()).orElse(null);
        
        for (SessionPersistence.SessionMetadata session : sessions) {
            String indicator = session.id.equals(currentId) ? "⭐" : "  ";
            System.out.printf("%s [%s] %s%n", indicator, 
                             session.id.substring(0, Math.min(8, session.id.length())), 
                             session.name);
            System.out.printf("     Messages: %d | Tokens: %d | Last: %s%n", 
                             session.messageCount, session.totalTokens, session.lastAccessAt);
            System.out.println();
        }
        
        System.out.println("💫 Use /session load <id> to switch sessions");
    }
    
    private void saveCurrentSession() {
        boolean success = sessionManager.saveCurrentSession();
        
        if (success) {
            System.out.println("✅ Current session saved successfully");
        } else {
            System.out.println("❌ Failed to save current session");
        }
    }
    
    private void deleteSession(String sessionId) {
        if (sessionId.trim().isEmpty()) {
            System.out.println("❌ Session ID required");
            System.out.println("Use /session list to see available sessions");
            return;
        }
        
        System.out.printf("⚠️ Are you sure you want to delete session %s? (y/N): ", sessionId);
        
        try (var scanner = new java.util.Scanner(System.in)) {
            String confirmation = scanner.nextLine().trim().toLowerCase();
            
            if ("y".equals(confirmation) || "yes".equals(confirmation)) {
                boolean success = sessionManager.deleteSession(sessionId.trim());
                
                if (success) {
                    System.out.printf("✅ Session %s deleted successfully%n", sessionId);
                } else {
                    System.out.printf("❌ Failed to delete session %s%n", sessionId);
                }
            } else {
                System.out.println("❌ Deletion cancelled");
            }
        }
    }
    
    private void clearSessionHistory() {
        var currentSession = sessionManager.getCurrentSession();
        
        if (currentSession.isEmpty()) {
            System.out.println("❌ No active session to clear");
            return;
        }
        
        System.out.printf("⚠️ Clear history for session '%s'? (y/N): ", currentSession.get().getName());
        
        try (var scanner = new java.util.Scanner(System.in)) {
            String confirmation = scanner.nextLine().trim().toLowerCase();
            
            if ("y".equals(confirmation) || "yes".equals(confirmation)) {
                boolean success = sessionManager.clearCurrentSessionHistory();
                
                if (success) {
                    System.out.println("✅ Session history cleared successfully");
                    System.out.println("   • Previous conversation removed");
                    System.out.println("   • Session configuration preserved");
                } else {
                    System.out.println("❌ Failed to clear session history");
                }
            } else {
                System.out.println("❌ Clear cancelled");
            }
        }
    }

    private void showUnknownCommand(String cmd) {
        System.out.printf("❌ Unknown command: %s. Type /help for available commands.%n", cmd);
    }
}