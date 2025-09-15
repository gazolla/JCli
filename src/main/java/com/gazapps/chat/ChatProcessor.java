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
import com.gazapps.session.Message;
import com.gazapps.session.SessionManager;


public class ChatProcessor implements InferenceObserver {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatProcessor.class);
    
    private  MCPManager mcpManager;
    private  Llm llm;
    private final CommandHandler commandHandler;
    private final ChatFormatter formatter;
    private final SessionManager sessionManager;
    
    
    private InferenceStrategy currentStrategy = InferenceStrategy.SIMPLE;
    private boolean debugMode = false;
    private long queryStartTime = 0;
    private final String RESPONSE_ICON = "⏱️";
    
    public ChatProcessor(MCPManager mcpManager, Llm llm, SessionManager sessionManager) {
        this.mcpManager = mcpManager;
        this.llm = llm;
        this.sessionManager = sessionManager;
        this.commandHandler = new CommandHandler(this, mcpManager, sessionManager);
        this.formatter = new ChatFormatter();
    }
    
    // ===== PROCESSAMENTO DE QUERIES =====
    
    public void processQuery(String query) {
        processQuery(query, currentStrategy);
    }
    
    public void processQuery(String query, InferenceStrategy strategy) {
    	queryStartTime = System.currentTimeMillis();
    	
    	if (!checkLlmCapabilities()) {
    		return; // Error already displayed
    	}
    	
     	try {
            sessionManager.addUserMessage(query);
        } catch (IllegalStateException e) {
            formatter.showError("❌ No active session. Please create or load a session first.");
            return;
        }
    	
    	// Get conversational context from session
    	List<Message> context = sessionManager.getContextForLlm();
    	
    	try {
            processWithContext(query, context, strategy);
        } catch (Exception e) {
            onError("Erro ao processar query", e);
        }
    }
    
    /**
     * Processes query with conversational context from session.
     */
    public void processWithContext(String query, List<Message> context, InferenceStrategy strategy) {
        Map<String, Object> options = Map.of(
            "observer", this, 
            "debug", debugMode,
            "maxIterations", 10,
            "context", context  // Pass session context to inference
        );
        
        Inference inference = InferenceFactory.createInference(strategy, mcpManager, llm, options);
        inference.processQuery(query);
        
        inference.close();
    }
    
    // ===== OBSERVER CALLBACKS =====
    
    @Override
    public void onInferenceStart(String query, String strategyName) {
            formatter.showInferenceStart(strategyName);
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
        	long duration = System.currentTimeMillis() - queryStartTime;
        	
        	// Add assistant response to session
        	try {
                sessionManager.addAssistantMessage(finalResponse);
            } catch (IllegalStateException e) {
                logger.warn("Could not save assistant message to session: {}", e.getMessage());
            }
        	
        	showFinalResponse(finalResponse, duration);
        } 
    }   
    
    public void showFinalResponse(String response) {
        System.out.printf("%n%s %s%n%n", RESPONSE_ICON, response);
    }
    
    public void showFinalResponse(String response, long durationMillis) {
        String timing = formatTiming(durationMillis);
        System.out.printf("%n%s %s %s%n%n", RESPONSE_ICON, response, timing);
    }

    private String formatTiming(long millis) {
        if (millis < 1000) {
            return String.format("(⏱️ %d ms)", millis);
        } else {
            return String.format("(⏱️ %.1f secs)", millis / 1000.0);
        }
    }
    
    @Override
    public void onError(String error, Exception exception) {
        logger.error("Inference error: {}", error, exception);
        
        // KISS: Enhanced error display with user-friendly messages
        String userMessage = formatUserFriendlyError(error, exception);
        formatter.showError(userMessage);
    }
    
    /**
     * KISS: Simple LLM capabilities check
     */
    private boolean checkLlmCapabilities() {
        if (llm == null) {
            formatter.showError("❌ No LLM available");
            return false;
        }
        
        // Health check using MCPManager
        if (!mcpManager.isLlmHealthy()) {
            formatter.showError("⚠️ LLM health check failed - responses may be limited");
            // Continue anyway for graceful degradation
        }
        
        return true;
    }
    
    /**
     * KISS: Convert technical errors to user-friendly messages
     */
    private String formatUserFriendlyError(String error, Exception exception) {
        if (exception != null) {
            String exceptionMsg = exception.getMessage();
            
            // Common error patterns
            if (exceptionMsg != null) {
                if (exceptionMsg.contains("API key") || exceptionMsg.contains("authentication")) {
                    return "❌ Authentication error - Please check your API key configuration";
                }
                if (exceptionMsg.contains("timeout") || exceptionMsg.contains("timed out")) {
                    return "⏱️ Request timed out - Please try again";
                }
                if (exceptionMsg.contains("rate limit") || exceptionMsg.contains("429")) {
                    return "🔄 Rate limit reached - Please wait a moment and try again";
                }
                if (exceptionMsg.contains("network") || exceptionMsg.contains("connection")) {
                    return "🌐 Network error - Please check your internet connection";
                }
            }
        }
        
        // Default: show original error with icon
        return "❌ " + error + (exception != null ? ": " + exception.getMessage() : "");
    }
    
    // ===== SISTEMA DE COMANDOS =====
    
    public void processCommand(String command) {
        commandHandler.handle(command);
    }
    
    // ===== GETTERS/SETTERS =====
    
    public void setCurrentStrategy(InferenceStrategy strategy) {
        this.currentStrategy = strategy;
        
        // Update session configuration if we have an active session
        sessionManager.changeInferenceStrategy(strategy);
    }
    
    public boolean changeLlm(Llm newLlm) {
        if (newLlm == null) {
            logger.warn("Attempted to change to null LLM");
            return false;
        }

        MCPManager oldMcpManager = this.mcpManager;
        Llm oldLlm = this.llm;
        
        try {
            logger.info("Changing LLM from {} to {}", oldLlm.getProviderName(), newLlm.getProviderName());
            
            this.llm = newLlm;
            this.mcpManager.setLlm(newLlm);
            if (!this.mcpManager.isHealthy()) {
                logger.warn("New MCPManager is not healthy, rolling back");
                this.mcpManager.close();
                return false;
            }
            
            // Update SessionManager with new LLM
            sessionManager.setLlm(newLlm);
            sessionManager.changeLlmProvider(newLlm.getProviderName());
            
            logger.info("Successfully changed LLM to {}", newLlm.getProviderName());
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to change LLM", e);
            this.llm = oldLlm;
            this.mcpManager = oldMcpManager;
            
            return false;
        }
    }
    
    public Llm getCurrentLlm() {
        return llm;
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
    
    public SessionManager getSessionManager() {
        return sessionManager;
    }
}
