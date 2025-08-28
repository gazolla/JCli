package com.gazapps.inference;

import java.util.List;
import java.util.Map;

/**
 * Interface Observer para feedback em tempo real durante inferência.
 */
public interface InferenceObserver {
    
    default void onInferenceStart(String query, String strategyName) {}
    
    default void onThought(String thought) {}
    
    default void onToolDiscovery(List<String> availableTools) {}
    
    default void onToolSelection(String toolName, Map<String, Object> args) {}
    
    default void onToolExecution(String toolName, String result) {}
    
    default void onPartialResponse(String partialContent) {}
    
    default void onInferenceComplete(String finalResponse) {}
    
    default void onError(String error, Exception exception) {}
}
