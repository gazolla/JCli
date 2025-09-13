package com.gazapps.chat;

import java.util.Map;
import java.util.stream.Collectors;


public class ChatFormatter {
    
    private static final String THINKING_ICON = "🤔";
    private static final String TOOL_ICON = "🔧";
    private static final String RESPONSE_ICON = "🤖";
    private static final String ERROR_ICON = "❌";
    
    public void showInferenceStart(String strategyName) {
        System.out.printf("🧠 Using %s inference...%n", strategyName);
    }
    
    public void showThinking(String thought) {
        if (thought == null || thought.trim().isEmpty()) return;
        
        String clean = thought.length() > 100 ? 
            thought.substring(0, 100) + "..." : thought;
        System.out.printf("%s %s%n", THINKING_ICON, clean);
    }
    
    public void showToolExecution(String toolName, Map<String, Object> args) {
        System.out.printf("%s Using: %s%n", TOOL_ICON, toolName);
        if (args != null && !args.isEmpty()) {
            System.out.printf("   Args: %s%n", formatArgs(args));
        }
    }
    
    public void showPartialResponse(String content) {
        if (content != null && !content.trim().isEmpty()) {
            System.out.print(content);
        }
    }
    
    public void showFinalResponse(String response) {
        System.out.printf("%n%s %s%n%n", RESPONSE_ICON, response);
    }
    
    public void showError(String error) {
        System.out.printf("%s %s%n", ERROR_ICON, error);
    }
    
    private String formatArgs(Map<String, Object> args) {
        return args.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining(", "));
    }
}
