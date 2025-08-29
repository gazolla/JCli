package com.gazapps.llm;


public class LlmException extends RuntimeException {
    
    private final LlmProvider provider;
    private final ErrorType errorType;
    
    public enum ErrorType {
        COMMUNICATION("Communication error with LLM"),
        RATE_LIMIT("Rate limit reached"),
        TIMEOUT("Request timeout"),
        INVALID_REQUEST("Invalid request"),
        TOOL_ERROR("Tool execution error"),
        AUTHENTICATION("Authentication error"),
        UNKNOWN("Unknown error");
        
        private final String description;
        
        ErrorType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public LlmException(LlmProvider provider, ErrorType errorType, String message) {
        super(message);
        this.provider = provider;
        this.errorType = errorType;
    }
    
    public LlmException(LlmProvider provider, ErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.errorType = errorType;
    }
    
    public LlmProvider getProvider() {
        return provider;
    }
    
    public ErrorType getErrorType() {
        return errorType;
    }
    
    @Override
    public String toString() {
        return String.format("LlmException{provider='%s', type=%s, message='%s'}", 
                           provider, errorType, getMessage());
    }
}
