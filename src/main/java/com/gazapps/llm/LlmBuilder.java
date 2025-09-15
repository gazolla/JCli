package com.gazapps.llm;

import com.gazapps.llm.providers.Claude;
import com.gazapps.llm.providers.Gemini;
import com.gazapps.llm.providers.Groq;
import com.gazapps.llm.providers.OpenAI;

public class LlmBuilder {
    
     
    private LlmBuilder() {
        // Utility class - prevent instantiation
    }
    
     public static Llm gemini(String apiKey) {
        try {
            String resolvedKey = resolveApiKey(LlmProvider.GEMINI, apiKey);
            return new Gemini();
        } catch (Exception e) {
            throw new LlmException(LlmProvider.GEMINI, LlmException.ErrorType.INVALID_REQUEST, 
                                 "Failed to create Gemini instance: " + e.getMessage(), e);
        }
    }
    
   public static Llm groq(String apiKey) {
        try {
            String resolvedKey = resolveApiKey(LlmProvider.GROQ, apiKey);
            return new Groq();
        } catch (Exception e) {
            throw new LlmException(LlmProvider.GROQ, LlmException.ErrorType.INVALID_REQUEST, 
                                 "Failed to create Groq instance: " + e.getMessage(), e);
        }
    }
    
    public static Llm claude(String apiKey) {
        try {
            String resolvedKey = resolveApiKey(LlmProvider.CLAUDE, apiKey);
            return new Claude();
        } catch (Exception e) {
            throw new LlmException(LlmProvider.CLAUDE, LlmException.ErrorType.INVALID_REQUEST, 
                                 "Failed to create Claude instance: " + e.getMessage(), e);
        }
    }
    
   public static Llm openai(String apiKey) {
        try {
            String resolvedKey = resolveApiKey(LlmProvider.OPENAI, apiKey);
            return new OpenAI();
        } catch (Exception e) {
            throw new LlmException(LlmProvider.OPENAI, LlmException.ErrorType.INVALID_REQUEST, 
                                 "Failed to create OpenAI instance: " + e.getMessage(), e);
        }
    }
    
     private static String resolveApiKey(LlmProvider provider, String providedKey) {
        if (providedKey != null && !providedKey.trim().isEmpty() && !providedKey.startsWith("your_")) {
            return providedKey;
        }
        
        String envKeyName = getEnvironmentKeyName(provider);
        String systemKey = System.getProperty(envKeyName);
        if (systemKey != null && !systemKey.trim().isEmpty()) {
            return systemKey;
        }
        
        String envKey = System.getenv(envKeyName);
        if (envKey != null && !envKey.trim().isEmpty()) {
            return envKey;
        }
        
        LlmConfig config = new LlmConfig();
        if (config.isLlmConfigValid(provider)) {
            return "configured"; // Signal that config is valid
        }
        
        throw new LlmException(provider, LlmException.ErrorType.AUTHENTICATION, 
                             "API Key is required for " + provider.name() + 
                             ". Please set " + envKeyName + " or configure in LlmConfig.");
    }
    
     private static String getEnvironmentKeyName(LlmProvider provider) {
        return switch (provider) {
            case GEMINI -> "GEMINI_API_KEY";
            case GROQ -> "GROQ_API_KEY";
            case CLAUDE -> "ANTHROPIC_API_KEY";
            case OPENAI -> "OPENAI_API_KEY";
        };
    }
}
