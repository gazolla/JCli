package com.gazapps.llm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

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
        try (OutputStream output = new FileOutputStream(configFile)) {
            Properties defaultProps = new Properties();
           
            defaultProps.setProperty("gemini.baseUrl", "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent");
            defaultProps.setProperty("gemini.apiKey", "");
            defaultProps.setProperty("gemini.model", "gemini-2.0-flash");
            defaultProps.setProperty("gemini.timeout", "60");
            defaultProps.setProperty("gemini.debug", "false");

            defaultProps.setProperty("groq.baseUrl", "https://api.groq.com/openai/v1/chat/completions");
            defaultProps.setProperty("groq.apiKey", "");
            defaultProps.setProperty("groq.model", "llama3-8b-8192");
            defaultProps.setProperty("groq.timeout", "60");
            defaultProps.setProperty("groq.debug", "false");

            defaultProps.setProperty("openai.baseUrl", "https://api.openai.com/v1/chat/completions");
            defaultProps.setProperty("openai.apiKey", "");
            defaultProps.setProperty("openai.model", "gpt-4");
            defaultProps.setProperty("openai.timeout", "60");
            defaultProps.setProperty("openai.debug", "false");

            defaultProps.setProperty("claude.baseUrl", "https://api.anthropic.com/v1/messages");
            defaultProps.setProperty("claude.apiKey", "");
            defaultProps.setProperty("claude.model", "claude-3-opus-20240229");
            defaultProps.setProperty("claude.timeout", "60");
            defaultProps.setProperty("claude.debug", "false");

            defaultProps.store(output, "LLM Provider Configurations");
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

    public boolean isLlmConfigValid(LlmProvider provider) {
        Map<String, String> config = getProviderConfig(provider.name().toLowerCase());
        return config.containsKey("apiKey") && !config.get("apiKey").startsWith("YOUR_");
    }
}