package com.gazapps.session;

import java.util.List;
import java.util.stream.Collectors;

public final class ConversationContextBuilder {
    
    private ConversationContextBuilder() {
        // Utility class
    }
    
    public static String buildForSimple(List<Message> context, int maxExchanges) {
        return buildBasicContext(context, maxExchanges, 120);
    }
    
    public static String buildForReAct(List<Message> context, int maxExchanges) {
        return buildAdvancedContext(context, maxExchanges, 100, true);
    }
    
    public static String buildForReflection(List<Message> context, int maxExchanges) {
        return buildAdvancedContext(context, maxExchanges, 150, false);
    }
    
    public static String buildForToolContext(List<Message> context, String domain, int maxMessages) {
        if (context.isEmpty()) return "";
        
        List<Message> relevantHistory = context.stream()
            .filter(msg -> {
                String content = msg.getContent().toLowerCase();
                return content.contains(domain.toLowerCase()) || 
                       msg.getType() == Message.Type.USER ||
                       msg.getType() == Message.Type.ASSISTANT;
            })
            .limit(maxMessages)
            .collect(Collectors.toList());
        
        if (relevantHistory.isEmpty()) {
            return buildBasicContext(context, 2, 100);
        }
        
        return relevantHistory.stream()
            .map(msg -> formatMessage(msg, 100))
            .collect(Collectors.joining("\n", "Related conversation context:\n", "\n\n"));
    }
    
    private static String buildBasicContext(List<Message> context, int maxExchanges, int truncateAt) {
        if (context.isEmpty()) return "";
        
        return context.stream()
            .filter(msg -> msg.getType() == Message.Type.USER || 
                          msg.getType() == Message.Type.ASSISTANT)
            .limit(maxExchanges * 2)
            .map(msg -> formatMessage(msg, truncateAt))
            .collect(Collectors.joining("\n", "Recent conversation:\n", "\n\n"));
    }
    
    private static String buildAdvancedContext(List<Message> context, int maxMessages, int truncateAt, boolean includeTools) {
        if (context.isEmpty()) return "";
        
        return context.stream()
            .filter(msg -> {
                if (includeTools) {
                    return msg.getType() == Message.Type.USER || 
                           msg.getType() == Message.Type.ASSISTANT ||
                           msg.getType() == Message.Type.SUMMARY ||
                           (msg.getType() == Message.Type.SYSTEM && msg.getContent().contains("tool"));
                } else {
                    return msg.getType() == Message.Type.USER || 
                           msg.getType() == Message.Type.ASSISTANT ||
                           msg.getType() == Message.Type.SUMMARY;
                }
            })
            .limit(maxMessages)
            .map(msg -> formatMessage(msg, truncateAt))
            .collect(Collectors.joining("\n", "Conversation context:\n", "\n\n"));
    }
    
    private static String formatMessage(Message msg, int truncateAt) {
        String content = msg.getContent();
        if (content.length() > truncateAt) {
            content = content.substring(0, truncateAt) + "...";
        }
        return String.format("%s: %s", msg.getType().toString().toLowerCase(), content);
    }
}
