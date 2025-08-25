package com.gazapps.llm;
 /**
     * Enum para identificar provedores LLM.
     */
    public enum LlmProvider {
        GEMINI,
        GROQ,
        OPENAI,
        CLAUDE;
        
        public static LlmProvider fromString(String value) {
            if (value == null) return null;
            for (LlmProvider provider : LlmProvider.values()) {
                if (provider.name().equalsIgnoreCase(value.trim())) {
                    return provider;
                }
            }
            throw new IllegalArgumentException("Invalid LLM Provider: " + value);
        }
    }