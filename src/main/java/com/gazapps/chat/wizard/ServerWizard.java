package com.gazapps.chat.wizard;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import com.gazapps.mcp.MCPConfig;
import com.gazapps.mcp.MCPManager;

public class ServerWizard {
    private final Scanner scanner;
    private final MCPManager mcpManager;
    private final MCPConfig config;
    
    public ServerWizard(MCPManager mcpManager) {
        this.scanner = new Scanner(System.in);
        this.mcpManager = mcpManager;
        this.config = (MCPConfig) getConfigFromManager(mcpManager);
    }
    
    public boolean runWizard() {
        try {
            printHeader();
            
            // Step 1: Basic Info
            String serverId = collectServerId();
            if (serverId == null) return false;
            
            String description = collectDescription();
            
            // Step 2: Command
            String command = collectAndValidateCommand();
            if (command == null) return false;
            
            // Step 3: Domain
            String domain = selectOrCreateDomain();
            if (domain == null) return false;
            
            // Step 4: Options
            int priority = collectPriority();
            
            // Step 5: Environment (auto-detected)
            Map<String, String> env = detectEnvironment(command);
            
            // Step 6: Confirmation
            return confirmAndCreateServer(serverId, description, command, domain, priority, env);
            
        } catch (Exception e) {
            System.out.println("❌ Error during wizard: " + e.getMessage());
            return false;
        }
    }
    
    private void printHeader() {
        System.out.println("\n🚀 MCP Server Addition Wizard\n");
    }
    
    private String collectServerId() {
        System.out.println("📝 Step 1/6 - Basic Information");
        System.out.println("━".repeat(40));
        
        while (true) {
            System.out.print("Server ID (unique): ");
            String id = scanner.nextLine().trim();
            
            if (id.isEmpty()) {
                System.out.println("❌ ID cannot be empty");
                continue;
            }
            
            if (config.getServerConfig(id) != null) {
                System.out.println("❌ Server already exists: " + id);
                continue;
            }
            
            if (!id.matches("[a-zA-Z0-9_-]+")) {
                System.out.println("❌ Use only letters, numbers, _ or -");
                continue;
            }
            
            return id;
        }
    }
    
    private String collectDescription() {
        System.out.print("Description: ");
        String desc = scanner.nextLine().trim();
        return desc.isEmpty() ? "Custom MCP Server" : desc;
    }
    
    private String collectAndValidateCommand() {
        System.out.println("\n📝 Step 2/6 - Execution Command");
        System.out.println("━".repeat(40));
        
        while (true) {
            System.out.print("Command: ");
            String command = scanner.nextLine().trim();
            
            if (command.isEmpty()) {
                System.out.println("❌ Command cannot be empty");
                continue;
            }
            
            if (validateCommand(command)) {
                return command;
            }
            
            System.out.print("Try another command? (y/N): ");
            if (!scanner.nextLine().toLowerCase().startsWith("y")) {
                return null;
            }
        }
    }
    
    private boolean validateCommand(String command) {
        String mainCommand = getMainCommand(command);
        
        System.out.printf("🔍 Checking command '%s'... ", mainCommand);
        
        if (!isCommandAvailable(mainCommand)) {
            System.out.println("❌ Not available");
            return false;
        }
        
        System.out.println("✅ Available");
        return true;
    }
    
    private boolean isCommandAvailable(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                pb.command("cmd.exe", "/c", "where", command);
            } else {
                pb.command("which", command);
            }
            
            Process process = pb.start();
            boolean finished = process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            
            return process.exitValue() == 0;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    private String getMainCommand(String command) {
        return command.trim().split("\\s+")[0];
    }
    
    private String selectOrCreateDomain() {
        System.out.println("\n📝 Step 3/6 - Domain");
        System.out.println("━".repeat(40));
        
        List<String> domains = mcpManager.getAvailableDomains();
        
        System.out.println("Existing domains:");
        for (int i = 0; i < domains.size(); i++) {
            System.out.printf("[%d] %s%n", i + 1, domains.get(i));
        }
        System.out.printf("[%d] 🆕 Create new domain%n", domains.size() + 1);
        
        while (true) {
            System.out.printf("Select domain (1-%d): ", domains.size() + 1);
            try {
                int choice = Integer.parseInt(scanner.nextLine().trim());
                
                if (choice >= 1 && choice <= domains.size()) {
                    return domains.get(choice - 1);
                } else if (choice == domains.size() + 1) {
                    return createNewDomain();
                } else {
                    System.out.println("❌ Invalid option");
                }
            } catch (NumberFormatException e) {
                System.out.println("❌ Enter a valid number");
            }
        }
    }
    
    private String createNewDomain() {
        while (true) {
            System.out.print("New domain name: ");
            String name = scanner.nextLine().trim().toLowerCase();
            
            if (name.isEmpty()) {
                System.out.println("❌ Name cannot be empty");
                continue;
            }
            
            if (!name.matches("[a-zA-Z0-9_-]+")) {
                System.out.println("❌ Use only letters, numbers, _ or -");
                continue;
            }
            
            if (mcpManager.getAvailableDomains().contains(name)) {
                System.out.println("❌ Domain already exists");
                continue;
            }
            
            System.out.print("Domain description: ");
            String description = scanner.nextLine().trim();
            if (description.isEmpty()) {
                description = "Domain " + name;
            }
            
            // Save new domain
            config.updateDomainsIfNeeded(name, description);
            return name;
        }
    }
    
    private int collectPriority() {
        System.out.println("\n📝 Step 4/6 - Settings");
        System.out.println("━".repeat(40));
        
        while (true) {
            System.out.print("Priority (1=high, 5=low) [2]: ");
            String input = scanner.nextLine().trim();
            
            if (input.isEmpty()) {
                return 2;
            }
            
            try {
                int priority = Integer.parseInt(input);
                if (priority >= 1 && priority <= 5) {
                    return priority;
                } else {
                    System.out.println("❌ Priority must be between 1 and 5");
                }
            } catch (NumberFormatException e) {
                System.out.println("❌ Enter a valid number");
            }
        }
    }
    
    private Map<String, String> detectEnvironment(String command) {
        System.out.println("\n📝 Step 5/6 - Environment (auto-detected)");
        System.out.println("━".repeat(40));
        
        Map<String, String> env = new HashMap<>();
        String mainCommand = getMainCommand(command).toLowerCase();
        
        switch (mainCommand) {
            case "uvx" -> {
                env.put("REQUIRES_UVX", "true");
                env.put("REQUIRES_PYTHON", "true");
                System.out.println("🐍 Detected: Python/uvx");
            }
            case "npx" -> {
                env.put("REQUIRES_NODEJS", "true");
                System.out.println("📦 Detected: Node.js/npx");
                if (command.contains("@")) {
                    env.put("REQUIRES_ONLINE", "true");
                    System.out.println("🌐 Detected: Requires internet");
                }
            }
            case "python", "python3" -> {
                env.put("REQUIRES_PYTHON", "true");
                System.out.println("🐍 Detected: Python");
            }
            case "node" -> {
                env.put("REQUIRES_NODEJS", "true");
                System.out.println("📦 Detected: Node.js");
            }
            default -> System.out.println("❓ Environment not recognized");
        }
        
        return env;
    }
    
    private boolean confirmAndCreateServer(String serverId, String description, String command, 
                                         String domain, int priority, Map<String, String> env) {
        System.out.println("\n📝 Step 6/6 - Confirmation");
        System.out.println("━".repeat(40));
        
        System.out.println("\n📋 Configuration Summary:");
        System.out.println("┏" + "━".repeat(42) + "┓");
        System.out.printf("┃ ID: %-35s ┃%n", serverId);
        System.out.printf("┃ Command: %-30s ┃%n", limitString(command, 30));
        System.out.printf("┃ Domain: %-30s ┃%n", domain);
        System.out.printf("┃ Priority: %d | Enabled: Yes        ┃%n", priority);
        System.out.printf("┃ Environment: %-27s ┃%n", limitString(String.join(", ", env.keySet()), 27));
        System.out.println("┗" + "━".repeat(42) + "┛");
        
        System.out.print("\nConfirm addition? (Y/n): ");
        String confirm = scanner.nextLine().trim();
        
        if (!confirm.isEmpty() && !confirm.toLowerCase().startsWith("y")) {
            return false;
        }
        
        return createServer(serverId, description, command, domain, priority, env);
    }
    
    private String limitString(String str, int maxLength) {
        return str.length() <= maxLength ? str : str.substring(0, maxLength - 3) + "...";
    }
    
    private boolean createServer(String serverId, String description, String command,
                               String domain, int priority, Map<String, String> env) {
        try {
            System.out.println("\n🔄 Adding server...");
            
            // Create configuration
            MCPConfig.ServerConfig serverConfig = new MCPConfig.ServerConfig(
                serverId, description, command, Collections.emptyList(),
                env, priority, true, domain
            );
            
            System.out.println("  💾 Saving to mcp.json");
            config.addNewServer(serverId, serverConfig);
            
            System.out.println("  🔌 Connecting server");
            boolean connected = mcpManager.getMcpService().connectServer(serverConfig);
            
            if (connected) {
                System.out.println("  ✅ Tools added to LLM collections");
                return true;
            } else {
                System.out.println("  ❌ Failed to connect server");
                // Rollback
                config.removeServer(serverId);
                return false;
            }
            
        } catch (Exception e) {
            System.out.println("  ❌ Error: " + e.getMessage());
            return false;
        }
    }
    
    private Object getConfigFromManager(MCPManager mcpManager) {
        try {
            // Use reflection to access private config field
            var field = mcpManager.getClass().getDeclaredField("config");
            field.setAccessible(true);
            return field.get(mcpManager);
        } catch (Exception e) {
            throw new RuntimeException("Error accessing configuration", e);
        }
    }
}