package com.gazapps.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gazapps.llm.LlmProvider;

public class EnvironmentSetup {
    
    private static final Logger logger = LoggerFactory.getLogger(EnvironmentSetup.class);
    private static Scanner scanner = new Scanner(System.in);
    
    private static final List<ProviderInfo> PROVIDERS = Arrays.asList(
        new ProviderInfo(LlmProvider.GEMINI, "GEMINI_API_KEY", "Google's latest AI model"),
        new ProviderInfo(LlmProvider.GROQ, "GROQ_API_KEY", "Lightning fast with Llama models"),
        new ProviderInfo(LlmProvider.CLAUDE, "ANTHROPIC_API_KEY", "Anthropic's Claude models"),
        new ProviderInfo(LlmProvider.OPENAI, "OPENAI_API_KEY", "GPT models from OpenAI")
    );
    
    public static class ProviderInfo {
        public final LlmProvider provider;
        public final String envKey;
        public final String description;
        
        public ProviderInfo(LlmProvider provider, String envKey, String description) {
            this.provider = provider;
            this.envKey = envKey;
            this.description = description;
        }
    }
    
    public static boolean ensureConfigurationReady() {
        logger.info("Checking environment configuration...");
        
        // 1. Create directory structure
        if (!createDirectoryStructure()) {
            return false;
        }
        
        // 2. Check configured providers
        List<LlmProvider> configuredProviders = getConfiguredProviders();
        
        if (configuredProviders.isEmpty()) {
            System.out.println("❌ No API keys configured");
            System.out.println("🧙‍♂️ Starting Configuration Wizard...");
            return runConfigurationWizard();
        } else {
            System.out.printf("✅ Found configured providers: %s%n", configuredProviders);
            return true;
        }
    }
    
    private static boolean createDirectoryStructure() {
        String[] dirs = {"config", "config/mcp", "documents", "log", "log/llm", "log/inference"};
        
        try {
            for (String dir : dirs) {
                Files.createDirectories(Paths.get(dir));
            }
            System.out.println("📁 Configuration structure verified");
            return true;
        } catch (IOException e) {
            System.out.println("❌ Error creating directory structure: " + e.getMessage());
            return false;
        }
    }
    
    public static List<LlmProvider> getConfiguredProviders() {
        return PROVIDERS.stream()
            .filter(p -> isProviderConfigured(p.provider))
            .map(p -> p.provider)
            .toList();
    }
    
    public static boolean isProviderConfigured(LlmProvider provider) {
        String envKey = getEnvironmentKeyName(provider);
        String value = getApiKeyFromEnvironment(envKey);
        return value != null && !value.trim().isEmpty() && !value.startsWith("your_");
    }
    
    private static String getEnvironmentKeyName(LlmProvider provider) {
        return switch (provider) {
            case GEMINI -> "GEMINI_API_KEY";
            case GROQ -> "GROQ_API_KEY";
            case CLAUDE -> "ANTHROPIC_API_KEY";
            case OPENAI -> "OPENAI_API_KEY";
        };
    }
    
    private static String getApiKeyFromEnvironment(String envKey) {
        // 1. System property
        String value = System.getProperty(envKey);
        if (value != null && !value.trim().isEmpty()) {
            return value;
        }
        
        // 2. Environment variable
        value = System.getenv(envKey);
        if (value != null && !value.trim().isEmpty()) {
            return value;
        }
        
        return null;
    }
    
    private static boolean runConfigurationWizard() {
        System.out.println("\n🧙‍♂️ JCli Configuration Wizard");
        System.out.println("===============================");
        System.out.println("JCli needs at least one LLM provider to work.");
        System.out.println("Available providers:\n");
        
        boolean hasConfigured = false;
        
        for (ProviderInfo providerInfo : PROVIDERS) {
            System.out.printf("Configure %s? (%s)%n", providerInfo.provider.name(), providerInfo.description);
            System.out.print("Enter API key (or press Enter to skip): ");
            
            String apiKey = scanner.nextLine().trim();
            
            if (!apiKey.isEmpty() && !apiKey.startsWith("your_")) {
                // Salvar na sessão atual
                System.setProperty(providerInfo.envKey, apiKey);
                
                // Salvar no arquivo llm.properties
                saveApiKeyToFile(providerInfo.provider, apiKey);
                
                System.out.printf("✅ %s configured!%n%n", providerInfo.provider.name());
                hasConfigured = true;
            } else {
                System.out.printf("⏭️ Skipped %s%n%n", providerInfo.provider.name());
            }
        }
        
        if (hasConfigured) {
            System.out.println("🎉 Configuration completed successfully!");
            System.out.println("✅ System ready!");
            return true;
        } else {
            System.out.println("❌ No providers configured. JCli cannot start.");
            System.out.println("\nPlease set at least one API key:");
            for (ProviderInfo p : PROVIDERS) {
                System.out.printf("- %s=your_key%n", p.envKey);
            }
            return false;
        }
    }
    
    private static void saveApiKeyToFile(LlmProvider provider, String apiKey) {
        try {
            Path llmPropertiesPath = Paths.get("config/llm.properties");
            
            // Ler arquivo existente se existir
            List<String> lines = new ArrayList<>();
            if (Files.exists(llmPropertiesPath)) {
                lines = Files.readAllLines(llmPropertiesPath);
            }
            
            // Atualizar linha da API key
            String propKey = getEnvironmentKeyName(provider).toLowerCase().replace("_", ".");
            String newLine = propKey + "=" + apiKey;
            
            boolean updated = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith(propKey + "=")) {
                    lines.set(i, newLine);
                    updated = true;
                    break;
                }
            }
            
            if (!updated) {
                lines.add(newLine);
            }
            
            // Salvar arquivo
            Files.write(llmPropertiesPath, lines);
            logger.info("API key saved to {}", llmPropertiesPath);
            
        } catch (IOException e) {
            logger.error("Error saving API key to file: {}", e.getMessage());
        }
    }
    
    public static String getCurrentWorkspacePath() {
        try {
            Path path = Paths.get("./documents");
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
            return path.toAbsolutePath().toString();
        } catch (IOException e) {
            logger.warn("Error expanding workspace path: {}", e.getMessage());
            return "./documents";
        }
    }
    
    public static void cleanup() {
        if (scanner != null) {
            scanner.close();
        }
    }
}
