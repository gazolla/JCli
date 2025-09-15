package com.gazapps.session;

import com.gazapps.config.Config;
import com.gazapps.inference.InferenceStrategy;
import com.gazapps.llm.Llm;
import com.gazapps.llm.LlmProvider;
import com.gazapps.mcp.MCPManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Central coordinator for session management and conversational context.
 * Following KISS principle: single responsibility for session orchestration.
 */
public class SessionManager implements AutoCloseable {
    
    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);
    
    private final Config config;
    private final SessionPersistence persistence;
    private ConversationSummarizer summarizer;
    
    private Session currentSession;
    private boolean autoSave;
    
    /**
     * Initializes SessionManager with configuration and persistence.
     */
    public SessionManager(Config config) {
        this.config = Objects.requireNonNull(config, "Config cannot be null");
        this.persistence = new SessionDatabase(config.getSessionDbPath());
        this.autoSave = config.isSessionAutoSave();
        
        // Summarizer will be initialized when we have an LLM
        this.summarizer = null; // Will be set via setLlm()
        
        logger.info("SessionManager initialized with database at: {}", config.getSessionDbPath());
    }
    
    /**
     * Full constructor with all dependencies (for testing or custom setups).
     */
    public SessionManager(Config config, SessionPersistence persistence, ConversationSummarizer summarizer) {
        this.config = Objects.requireNonNull(config, "Config cannot be null");
        this.persistence = Objects.requireNonNull(persistence, "Persistence cannot be null");
        this.summarizer = summarizer;
        this.autoSave = config.isSessionAutoSave();
        
        logger.info("SessionManager initialized with custom persistence and summarizer");
    }
    
    /**
     * Sets the LLM for summarization operations.
     */
    public void setLlm(Llm llm) {
        if (llm != null) {
            this.summarizer = new ConversationSummarizer(llm);
            logger.info("Summarizer configured with LLM: {}", llm.getProviderName());
        }
    }
    
    /**
     * Creates a new session with specified name and current system configuration.
     */
    public Session createNewSession(String name, Llm llm, InferenceStrategy strategy, MCPManager mcpManager) {
        Objects.requireNonNull(name, "Session name cannot be null");
        Objects.requireNonNull(llm, "LLM cannot be null");
        Objects.requireNonNull(strategy, "Strategy cannot be null");
        Objects.requireNonNull(mcpManager, "MCPManager cannot be null");
        
        // Get current MCP server configuration
        Set<String> enabledServers = mcpManager.getConnectedServers().keySet();
        
        // Create session configuration
        SessionConfig sessionConfig = new SessionConfig(
            llm.getProviderName(),
            strategy,
            enabledServers
        );
        
        // Create new session
        Session session = new Session(
            name,
            sessionConfig,
            config.getSessionMaxTokens(),
            config.getSessionSummaryThreshold()
        );
        
        // Save immediately if auto-save is enabled
        if (autoSave) {
            persistence.saveSession(session);
        }
        
        this.currentSession = session;
        
        logger.info("Created new session '{}' with id: {}", name, session.getId());
        
        return session;
    }
    
    /**
     * Loads an existing session by ID.
     */
    public Optional<Session> loadSession(String sessionId) {
        Objects.requireNonNull(sessionId, "Session ID cannot be null");
        
        Optional<Session> session = persistence.loadSession(sessionId);
        
        if (session.isPresent()) {
            this.currentSession = session.get();
            this.currentSession.touch(); // Update last access time
            
            if (autoSave) {
                persistence.saveSession(this.currentSession);
            }
            
            logger.info("Loaded session '{}' with {} messages", 
                       session.get().getName(), session.get().getMessages().size());
        } else {
            logger.warn("Session not found: {}", sessionId);
        }
        
        return session;
    }
    
    /**
     * Saves the current session if it exists and needs saving.
     */
    public boolean saveCurrentSession() {
        if (currentSession == null) {
            logger.warn("No current session to save");
            return false;
        }
        
        if (!currentSession.needsSave()) {
            logger.debug("Current session {} doesn't need saving", currentSession.getId());
            return true;
        }
        
        boolean success = persistence.saveSession(currentSession);
        if (success) {
            logger.info("Saved current session: {}", currentSession.getId());
        } else {
            logger.error("Failed to save current session: {}", currentSession.getId());
        }
        
        return success;
    }
    
    /**
     * Switches to a different session.
     */
    public boolean switchToSession(String sessionId) {
        // Save current session if needed
        if (currentSession != null && autoSave && currentSession.needsSave()) {
            saveCurrentSession();
        }
        
        // Load new session
        Optional<Session> newSession = loadSession(sessionId);
        return newSession.isPresent();
    }
    
    /**
     * Gets the current active session.
     */
    public Optional<Session> getCurrentSession() {
        return Optional.ofNullable(currentSession);
    }
    
    /**
     * Adds a user message to the current session.
     */
    public void addUserMessage(String content) {
        if (currentSession == null) {
            throw new IllegalStateException("No current session available");
        }
        
        Message userMessage = Message.user(content);
        currentSession.addMessage(userMessage);
        
        // Check for summarization need
        if (currentSession.needsSummarization() && summarizer != null) {
            performSummarization();
        }
        
        // Auto-save if enabled
        if (autoSave) {
            persistence.saveSession(currentSession);
        }
        
        logger.debug("Added user message to session {}: {} characters", 
                    currentSession.getId(), content.length());
    }
    
    /**
     * Adds an assistant message to the current session.
     */
    public void addAssistantMessage(String content) {
        if (currentSession == null) {
            throw new IllegalStateException("No current session available");
        }
        
        Message assistantMessage = Message.assistant(content);
        currentSession.addMessage(assistantMessage);
        
        // Check for summarization need
        if (currentSession.needsSummarization() && summarizer != null) {
            performSummarization();
        }
        
        // Auto-save if enabled
        if (autoSave) {
            persistence.saveSession(currentSession);
        }
        
        logger.debug("Added assistant message to session {}: {} characters", 
                    currentSession.getId(), content.length());
    }
    
    /**
     * Gets conversational context optimized for LLM inference.
     */
    public List<Message> getContextForLlm() {
        if (currentSession == null) {
            return List.of();
        }
        
        // Use a reasonable context token limit (about 75% of max tokens)
        int contextLimit = (int) (config.getSessionMaxTokens() * 0.75);
        
        List<Message> context = currentSession.getContextForLlm(contextLimit);
        
        logger.debug("Prepared LLM context: {} messages for session {}", 
                    context.size(), currentSession.getId());
        
        return context;
    }
    
    /**
     * Gets recent messages with a specified token limit.
     */
    public List<Message> getRecentMessages(int tokenLimit) {
        if (currentSession == null) {
            return List.of();
        }
        
        return currentSession.getRecentMessages(tokenLimit);
    }
    
    /**
     * Changes the LLM provider for the current session.
     */
    public boolean changeLlmProvider(LlmProvider newProvider) {
        if (currentSession == null) {
            logger.warn("Cannot change LLM provider: no current session");
            return false;
        }
        
        SessionConfig currentConfig = currentSession.getConfig();
        SessionConfig newConfig = currentConfig.withLlmProvider(newProvider);
        
        currentSession.updateConfig(newConfig);
        
        if (autoSave) {
            persistence.saveSession(currentSession);
        }
        
        logger.info("Changed LLM provider to {} for session {}", newProvider, currentSession.getId());
        return true;
    }
    
    /**
     * Changes the inference strategy for the current session.
     */
    public boolean changeInferenceStrategy(InferenceStrategy newStrategy) {
        if (currentSession == null) {
            logger.warn("Cannot change inference strategy: no current session");
            return false;
        }
        
        SessionConfig currentConfig = currentSession.getConfig();
        SessionConfig newConfig = currentConfig.withInferenceStrategy(newStrategy);
        
        currentSession.updateConfig(newConfig);
        
        if (autoSave) {
            persistence.saveSession(currentSession);
        }
        
        logger.info("Changed inference strategy to {} for session {}", newStrategy, currentSession.getId());
        return true;
    }
    
    /**
     * Toggles an MCP server for the current session.
     */
    public boolean toggleMcpServer(String serverId) {
        if (currentSession == null) {
            logger.warn("Cannot toggle MCP server: no current session");
            return false;
        }
        
        SessionConfig currentConfig = currentSession.getConfig();
        SessionConfig newConfig;
        
        if (currentConfig.getEnabledMcpServers().contains(serverId)) {
            newConfig = currentConfig.withRemovedMcpServer(serverId);
            logger.info("Disabled MCP server {} for session {}", serverId, currentSession.getId());
        } else {
            newConfig = currentConfig.withAddedMcpServer(serverId);
            logger.info("Enabled MCP server {} for session {}", serverId, currentSession.getId());
        }
        
        currentSession.updateConfig(newConfig);
        
        if (autoSave) {
            persistence.saveSession(currentSession);
        }
        
        return true;
    }
    
    /**
     * Lists all available sessions.
     */
    public List<SessionPersistence.SessionMetadata> listSessions() {
        return persistence.listSessions();
    }
    
    /**
     * Deletes a session by ID.
     */
    public boolean deleteSession(String sessionId) {
        // If deleting current session, clear it
        if (currentSession != null && currentSession.getId().equals(sessionId)) {
            currentSession = null;
        }
        
        boolean success = persistence.deleteSession(sessionId);
        if (success) {
            logger.info("Deleted session: {}", sessionId);
        }
        
        return success;
    }
    
    /**
     * Clears the conversation history of the current session.
     */
    public boolean clearCurrentSessionHistory() {
        if (currentSession == null) {
            logger.warn("Cannot clear history: no current session");
            return false;
        }
        
        // Create a new session with the same configuration but no messages
        String currentName = currentSession.getName();
        SessionConfig currentConfig = currentSession.getConfig();
        
        Session newSession = new Session(
            currentName,
            currentConfig,
            config.getSessionMaxTokens(),
            config.getSessionSummaryThreshold()
        );
        
        // Replace current session
        this.currentSession = newSession;
        
        if (autoSave) {
            persistence.saveSession(currentSession);
        }
        
        logger.info("Cleared history for session '{}'", currentName);
        return true;
    }
    
    /**
     * Performs conversation summarization if needed.
     */
    private void performSummarization() {
        if (currentSession == null || summarizer == null) {
            return;
        }
        
        try {
            boolean summarized = summarizer.summarizeIfNeeded(currentSession);
            if (summarized) {
                logger.info("Performed summarization for session {}", currentSession.getId());
            }
        } catch (Exception e) {
            logger.error("Failed to perform summarization for session {}: {}", 
                        currentSession.getId(), e.getMessage());
        }
    }
    
    /**
     * Gets session statistics.
     */
    public Optional<Session.SessionStats> getCurrentSessionStats() {
        return currentSession != null ? Optional.of(currentSession.getStats()) : Optional.empty();
    }
    
    @Override
    public void close() {
        // Save current session if needed
        if (currentSession != null && autoSave && currentSession.needsSave()) {
            saveCurrentSession();
        }
        
        // Close persistence layer
        if (persistence != null) {
            persistence.close();
        }
        
        logger.info("SessionManager closed");
    }
}
