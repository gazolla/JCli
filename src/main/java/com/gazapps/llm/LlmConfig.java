package com.gazapps.llm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gazapps.mcp.MCPConfig;

public class LlmConfig {

    private static final Logger logger = LoggerFactory.getLogger(MCPConfig.class);
    private final Properties properties = new Properties();
    private final String configPath = "config/llm.properties";

    public LlmConfig() {
        try {
            String rootPath = System.getProperty(".");
            File configFile = new File(rootPath, configPath);
            if (!configFile.exists()) {
                createDefaultConfigFile(configFile);
            }
            
            try (InputStream input = new FileInputStream(configFile)) {
                properties.load(input);
            }
        } catch (IOException e) {           
        	logger.error("Error loading configuration: {}", e.getMessage());
        }
    }

    private void createDefaultConfigFile(File configFile) throws IOException {
        configFile.getParentFile().mkdirs();
        
        // Criar arquivo manualmente com formatação correta
        String defaultContent = "# Gemini Configuration\n" +
                "gemini.base.url=https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent\n" +
                "gemini.model=gemini-2.0-flash\n" +
                "gemini.timeout=30\n" +
                "gemini.debug=false\n" +
                "gemini.api.key=\n" +
                "\n" +
                "# Groq Configuration\n" +
                "groq.base.url=https://api.groq.com/openai/v1/chat/completions\n" +
                "groq.model=llama-3.3-70b-versatile\n" +
                "groq.timeout=30\n" +
                "groq.debug=true\n" +
                "groq.api.key=\n" +
                "\n" +
                "# Claude Configuration\n" +
                "claude.base.url=https://api.anthropic.com/v1/messages\n" +
                "claude.model=claude-3-5-sonnet-20241022\n" +
                "claude.timeout=30\n" +
                "claude.debug=false\n" +
                "claude.api.key=\n" +
                "\n" +
                "# OpenAI Configuration\n" +
                "openai.base.url=https://api.openai.com/v1/chat/completions\n" +
                "openai.model=gpt-4o\n" +
                "openai.timeout=30\n" +
                "openai.debug=true\n" +
                "openai.api.key=\n";
        
        try (java.io.FileWriter writer = new java.io.FileWriter(configFile)) {
            writer.write(defaultContent);
        }
    }

    public Map<String, String> getProviderConfig(String provider) {
        Map<String, String> config = new HashMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith(provider + ".")) {
                config.put(key.substring(provider.length() + 1), properties.getProperty(key));
            }
        }
        return config;
    }

    public Map<String, String> getGeminiConfig() {
        return getProviderConfig("gemini");
    }

    public Map<String, String> getGroqConfig() {
        return getProviderConfig("groq");
    }

    public Map<String, String> getOpenAiConfig() {
        return getProviderConfig("openai");
    }

    public Map<String, String> getClaudeConfig() {
        return getProviderConfig("claude");
    }

    public void saveApiKey(LlmProvider provider, String apiKey) throws IOException {
        String providerKey = provider.name().toLowerCase() + ".api.key";
        properties.setProperty(providerKey, apiKey);
        savePropertiesToFile();
        logger.info("API key saved for provider: {}", provider.name());
    }
    
    private void savePropertiesToFile() throws IOException {
        try (OutputStream output = new FileOutputStream(configPath)) {
            properties.store(output, "LLM Provider Configurations - Updated");
        }
    }
    
    public List<LlmProvider> getAllConfiguredProviders() {
        return Arrays.stream(LlmProvider.values())
            .filter(this::isLlmConfigValid)
            .collect(Collectors.toList());
    }

    public boolean isLlmConfigValid(LlmProvider provider) {
        // 1. Verificar no properties file
        Map<String, String> config = getProviderConfig(provider.name().toLowerCase());
        String fileApiKey = config.get("api.key");
        if (fileApiKey != null && !fileApiKey.trim().isEmpty() && !fileApiKey.startsWith("YOUR_")) {
            return true;
        }
        
        // 2. Verificar em environment variables
        String envKey = getEnvironmentKeyName(provider);
        String envApiKey = System.getenv(envKey);
        if (envApiKey != null && !envApiKey.trim().isEmpty()) {
            return true;
        }
        
        // 3. Verificar em system properties
        String propApiKey = System.getProperty(envKey);
        return propApiKey != null && !propApiKey.trim().isEmpty();
    }
    
    private String getEnvironmentKeyName(LlmProvider provider) {
        return switch (provider) {
            case GEMINI -> "GEMINI_API_KEY";
            case GROQ -> "GROQ_API_KEY";
            case CLAUDE -> "ANTHROPIC_API_KEY";
            case OPENAI -> "OPENAI_API_KEY";
        };
    }
}
