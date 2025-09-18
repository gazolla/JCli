package com.gazapps.inference;

import java.util.List;
import com.gazapps.session.Message;

public interface Inference extends AutoCloseable {
    
    String processQuery(String query);
    
    String processQuery(String query, List<Message> context);
    
    String buildSystemPrompt();
    
    InferenceStrategy getStrategyName();
    
    // Default implementation para não quebrar implementações existentes
    @Override
    default void close() {
        // Default: não faz nada
    }
}
