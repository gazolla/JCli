package com.gazapps.chat;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gazapps.inference.Inference;
import com.gazapps.inference.InferenceFactory;
import com.gazapps.inference.InferenceObserver;
import com.gazapps.inference.InferenceStrategy;
import com.gazapps.llm.Llm;
import com.gazapps.mcp.MCPManager;

/**
 * Processador principal de chat que implementa observer pattern.
 */
public class ChatProcessor implements InferenceObserver {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatProcessor.class);
    
    private final MCPManager mcpManager;
    private final Llm llm;
    private final CommandHandler commandHandler;
    private final ChatFormatter formatter;
    
    private InferenceStrategy currentStrategy = InferenceStrategy.SIMPLE;
    private boolean debugMode = false;
    
    public ChatProcessor(MCPManager mcpManager, Llm llm) {
        this.mcpManager = mcpManager;
        this.llm = llm;
        this.commandHandler = new CommandHandler(this, mcpManager);
        this.formatter = new ChatFormatter();
    }
    
    // ===== PROCESSAMENTO DE QUERIES =====
    
    public void processQuery(String query) {
        processQuery(query, currentStrategy);
    }
    
    public void processQuery(String query, InferenceStrategy strategy) {
        try {
            Map<String, Object> options = Map.of(
                "observer", this, 
                "debug", debugMode,
                "maxIterations", 10
            );
            
            Inference inference = InferenceFactory.createInference(strategy, mcpManager, llm, options);
            inference.processQuery(query);
            
            inference.close();
            
        } catch (Exception e) {
            onError("Erro ao processar query", e);
        }
    }
    
    // ===== OBSERVER CALLBACKS =====
    
    @Override
    public void onInferenceStart(String query, String strategyName) {
       if (debugMode) {
            formatter.showInferenceStart(strategyName);
        }
    }
    
    @Override
    public void onThought(String thought) {
        if (debugMode) {
            formatter.showThinking(thought);
        }
    }
    
    @Override
    public void onToolDiscovery(List<String> availableTools) {
        if (debugMode && !availableTools.isEmpty()) {
            System.out.printf("🔍 Found %d tools: %s%n", 
                availableTools.size(), 
                String.join(", ", availableTools.subList(0, Math.min(3, availableTools.size())))
            );
        }
    }
    
    @Override
    public void onToolSelection(String toolName, Map<String, Object> args) {
        formatter.showToolExecution(toolName, args);
    }
    
    @Override
    public void onToolExecution(String toolName, String result) {
        if (debugMode && result != null) {
            String cleanResult = result.length() > 100 ? 
                result.substring(0, 100) + "..." : result;
            System.out.printf("📋 Result: %s%n", cleanResult);
        }
    }
    
    @Override
    public void onPartialResponse(String partialContent) {
        formatter.showPartialResponse(partialContent);
    }
    
    @Override
    public void onInferenceComplete(String finalResponse) {
        if (finalResponse != null && !finalResponse.trim().isEmpty()) {
            formatter.showFinalResponse(finalResponse);
        } 
    }    
    @Override
    public void onError(String error, Exception exception) {
        logger.error("Inference error: {}", error, exception);
        formatter.showError(error + (exception != null ? ": " + exception.getMessage() : ""));
    }
    
    // ===== SISTEMA DE COMANDOS =====
    
    public void processCommand(String command) {
        commandHandler.handle(command);
    }
    
    // ===== GETTERS/SETTERS =====
    
    public void setCurrentStrategy(InferenceStrategy strategy) {
        this.currentStrategy = strategy;
    }
    
    public InferenceStrategy getCurrentStrategy() {
        return currentStrategy;
    }
    
    public boolean toggleDebug() {
        this.debugMode = !this.debugMode;
        return this.debugMode;
    }
    
    public boolean isDebugMode() {
        return debugMode;
    }
}
