package com.gazapps.chat.wizard;

import com.gazapps.mcp.MCPConfig;
import com.gazapps.mcp.MCPManager;
import com.gazapps.mcp.domain.DomainDefinition;
import java.util.*;

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
            
            // Etapa 1: Info básica
            String serverId = collectServerId();
            if (serverId == null) return false;
            
            String description = collectDescription();
            
            // Etapa 2: Comando
            String command = collectAndValidateCommand();
            if (command == null) return false;
            
            // Etapa 3: Domínio
            String domain = selectOrCreateDomain();
            if (domain == null) return false;
            
            // Etapa 4: Opções
            int priority = collectPriority();
            
            // Etapa 5: Ambiente (auto-detectado)
            Map<String, String> env = detectEnvironment(command);
            
            // Etapa 6: Confirmação
            return confirmAndCreateServer(serverId, description, command, domain, priority, env);
            
        } catch (Exception e) {
            System.out.println("❌ Erro durante wizard: " + e.getMessage());
            return false;
        }
    }
    
    private void printHeader() {
        System.out.println("\n🚀 Assistente para Adicionar Servidor MCP\n");
    }
    
    private String collectServerId() {
        System.out.println("📝 Etapa 1/6 - Informações Básicas");
        System.out.println("━".repeat(40));
        
        while (true) {
            System.out.print("ID do servidor (único): ");
            String id = scanner.nextLine().trim();
            
            if (id.isEmpty()) {
                System.out.println("❌ ID não pode ser vazio");
                continue;
            }
            
            if (config.getServerConfig(id) != null) {
                System.out.println("❌ Servidor já existe: " + id);
                continue;
            }
            
            if (!id.matches("[a-zA-Z0-9_-]+")) {
                System.out.println("❌ Use apenas letras, números, _ ou -");
                continue;
            }
            
            return id;
        }
    }
    
    private String collectDescription() {
        System.out.print("Descrição: ");
        String desc = scanner.nextLine().trim();
        return desc.isEmpty() ? "Servidor MCP customizado" : desc;
    }
    
    private String collectAndValidateCommand() {
        System.out.println("\n📝 Etapa 2/6 - Comando de Execução");
        System.out.println("━".repeat(40));
        
        while (true) {
            System.out.print("Comando: ");
            String command = scanner.nextLine().trim();
            
            if (command.isEmpty()) {
                System.out.println("❌ Comando não pode ser vazio");
                continue;
            }
            
            if (validateCommand(command)) {
                return command;
            }
            
            System.out.print("Tentar outro comando? (s/N): ");
            if (!scanner.nextLine().toLowerCase().startsWith("s")) {
                return null;
            }
        }
    }
    
    private boolean validateCommand(String command) {
        String mainCommand = getMainCommand(command);
        
        System.out.printf("🔍 Verificando comando '%s'... ", mainCommand);
        
        if (!isCommandAvailable(mainCommand)) {
            System.out.println("❌ Não disponível");
            return false;
        }
        
        System.out.println("✅ Disponível");
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
        System.out.println("\n📝 Etapa 3/6 - Domínio");
        System.out.println("━".repeat(40));
        
        List<String> domains = mcpManager.getAvailableDomains();
        
        System.out.println("Domínios existentes:");
        for (int i = 0; i < domains.size(); i++) {
            System.out.printf("[%d] %s%n", i + 1, domains.get(i));
        }
        System.out.printf("[%d] 🆕 Criar novo domínio%n", domains.size() + 1);
        
        while (true) {
            System.out.printf("Selecione domínio (1-%d): ", domains.size() + 1);
            try {
                int choice = Integer.parseInt(scanner.nextLine().trim());
                
                if (choice >= 1 && choice <= domains.size()) {
                    return domains.get(choice - 1);
                } else if (choice == domains.size() + 1) {
                    return createNewDomain();
                } else {
                    System.out.println("❌ Opção inválida");
                }
            } catch (NumberFormatException e) {
                System.out.println("❌ Digite um número válido");
            }
        }
    }
    
    private String createNewDomain() {
        while (true) {
            System.out.print("Nome do novo domínio: ");
            String name = scanner.nextLine().trim().toLowerCase();
            
            if (name.isEmpty()) {
                System.out.println("❌ Nome não pode ser vazio");
                continue;
            }
            
            if (!name.matches("[a-zA-Z0-9_-]+")) {
                System.out.println("❌ Use apenas letras, números, _ ou -");
                continue;
            }
            
            if (mcpManager.getAvailableDomains().contains(name)) {
                System.out.println("❌ Domínio já existe");
                continue;
            }
            
            System.out.print("Descrição do domínio: ");
            String description = scanner.nextLine().trim();
            if (description.isEmpty()) {
                description = "Domínio " + name;
            }
            
            // Salvar novo domínio
            config.updateDomainsIfNeeded(name, description);
            return name;
        }
    }
    
    private int collectPriority() {
        System.out.println("\n📝 Etapa 4/6 - Configurações");
        System.out.println("━".repeat(40));
        
        while (true) {
            System.out.print("Prioridade (1=alta, 5=baixa) [2]: ");
            String input = scanner.nextLine().trim();
            
            if (input.isEmpty()) {
                return 2;
            }
            
            try {
                int priority = Integer.parseInt(input);
                if (priority >= 1 && priority <= 5) {
                    return priority;
                } else {
                    System.out.println("❌ Prioridade deve ser entre 1 e 5");
                }
            } catch (NumberFormatException e) {
                System.out.println("❌ Digite um número válido");
            }
        }
    }
    
    private Map<String, String> detectEnvironment(String command) {
        System.out.println("\n📝 Etapa 5/6 - Ambiente (auto-detectado)");
        System.out.println("━".repeat(40));
        
        Map<String, String> env = new HashMap<>();
        String mainCommand = getMainCommand(command).toLowerCase();
        
        switch (mainCommand) {
            case "uvx" -> {
                env.put("REQUIRES_UVX", "true");
                env.put("REQUIRES_PYTHON", "true");
                System.out.println("🐍 Detectado: Python/uvx");
            }
            case "npx" -> {
                env.put("REQUIRES_NODEJS", "true");
                System.out.println("📦 Detectado: Node.js/npx");
                if (command.contains("@")) {
                    env.put("REQUIRES_ONLINE", "true");
                    System.out.println("🌐 Detectado: Requer internet");
                }
            }
            case "python", "python3" -> {
                env.put("REQUIRES_PYTHON", "true");
                System.out.println("🐍 Detectado: Python");
            }
            case "node" -> {
                env.put("REQUIRES_NODEJS", "true");
                System.out.println("📦 Detectado: Node.js");
            }
            default -> System.out.println("❓ Ambiente não reconhecido");
        }
        
        return env;
    }
    
    private boolean confirmAndCreateServer(String serverId, String description, String command, 
                                         String domain, int priority, Map<String, String> env) {
        System.out.println("\n📝 Etapa 6/6 - Confirmação");
        System.out.println("━".repeat(40));
        
        System.out.println("\n📋 Resumo da Configuração:");
        System.out.println("┏" + "━".repeat(42) + "┓");
        System.out.printf("┃ ID: %-35s ┃%n", serverId);
        System.out.printf("┃ Comando: %-30s ┃%n", limitString(command, 30));
        System.out.printf("┃ Domínio: %-30s ┃%n", domain);
        System.out.printf("┃ Prioridade: %d | Habilitado: Sim      ┃%n", priority);
        System.out.printf("┃ Ambiente: %-27s ┃%n", limitString(String.join(", ", env.keySet()), 27));
        System.out.println("┗" + "━".repeat(42) + "┛");
        
        System.out.print("\nConfirmar adição? (S/n): ");
        String confirm = scanner.nextLine().trim();
        
        if (!confirm.isEmpty() && !confirm.toLowerCase().startsWith("s")) {
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
            System.out.println("\n🔄 Adicionando servidor...");
            
            // Criar configuração
            MCPConfig.ServerConfig serverConfig = new MCPConfig.ServerConfig(
                serverId, description, command, Collections.emptyList(),
                env, priority, true, domain
            );
            
            System.out.println("  💾 Salvando em mcp.json");
            config.addNewServer(serverId, serverConfig);
            
            System.out.println("  🔌 Conectando servidor");
            boolean connected = mcpManager.getMcpService().connectServer(serverConfig);
            
            if (connected) {
                System.out.println("  ✅ Tools adicionadas às coleções da LLM");
                return true;
            } else {
                System.out.println("  ❌ Falha ao conectar servidor");
                // Rollback
                config.removeServer(serverId);
                return false;
            }
            
        } catch (Exception e) {
            System.out.println("  ❌ Erro: " + e.getMessage());
            return false;
        }
    }
    
    private Object getConfigFromManager(MCPManager mcpManager) {
        try {
            // Usar reflection para acessar o campo config privado
            var field = mcpManager.getClass().getDeclaredField("config");
            field.setAccessible(true);
            return field.get(mcpManager);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao acessar configuração", e);
        }
    }
}
