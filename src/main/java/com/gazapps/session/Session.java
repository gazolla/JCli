package com.gazapps.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Central session object that maintains conversation state and configuration.
 * Following KISS principle: focused responsibilities, clear interface.
 */
public class Session {
    
    private static final Logger logger = LoggerFactory.getLogger(Session.class);
    
    private final String id;
    private final String name;
    private final LocalDateTime createdAt;
    private final List<Message> messages;
    private SessionConfig config;
    private LocalDateTime lastAccessAt;
    private boolean needsSummarization;
    private boolean needsSave;
    private final int maxTokens;
    private final int summaryThreshold;
    
    /**
     * Creates a new session with specified configuration.
     */
    public Session(String name, SessionConfig config, int maxTokens, int summaryThreshold) {
        this.id = UUID.randomUUID().toString();
        this.name = Objects.requireNonNull(name, "Session name cannot be null");
        this.config = Objects.requireNonNull(config, "Session config cannot be null");
        this.maxTokens = maxTokens;
        this.summaryThreshold = summaryThreshold;
        this.createdAt = LocalDateTime.now();
        this.lastAccessAt = LocalDateTime.now();
        this.messages = new ArrayList<>();
        this.needsSummarization = false;
        this.needsSave = false;
        
        logger.info("Created new session: {} (id: {})", name, id);
    }
    
    /**
     * Creates session from persistence (with existing id and timestamps).
     */
    public Session(String id, String name, SessionConfig config, LocalDateTime createdAt, 
                   LocalDateTime lastAccessAt, List<Message> messages, int maxTokens, int summaryThreshold) {
        this.id = Objects.requireNonNull(id, "Session id cannot be null");
        this.name = Objects.requireNonNull(name, "Session name cannot be null");
        this.config = Objects.requireNonNull(config, "Session config cannot be null");
        this.maxTokens = maxTokens;
        this.summaryThreshold = summaryThreshold;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.lastAccessAt = lastAccessAt != null ? lastAccessAt : LocalDateTime.now();
        this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
        this.needsSummarization = false;
        this.needsSave = false;
        
        // Check if summarization is needed on load
        checkSummarizationNeeded();
        
        logger.info("Loaded session: {} (id: {}) with {} messages", name, id, this.messages.size());
    }
    
    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public SessionConfig getConfig() { return config; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastAccessAt() { return lastAccessAt; }
    public List<Message> getMessages() { return List.copyOf(messages); }
    public boolean needsSummarization() { return needsSummarization; }
    public boolean needsSave() { return needsSave; }
    
    /**
     * Adds a new message to the session and handles automatic checks.
     */
    public void addMessage(Message message) {
        Objects.requireNonNull(message, "Message cannot be null");
        
        messages.add(message);
        this.lastAccessAt = LocalDateTime.now();
        this.needsSave = true;
        
        // Check if summarization is needed after adding message
        checkSummarizationNeeded();
        
        logger.debug("Added {} message to session {}: {} tokens", 
                    message.getType(), id, message.calculateTokens());
    }
    
    /**
     * Updates the session configuration.
     */
    public void updateConfig(SessionConfig newConfig) {
        Objects.requireNonNull(newConfig, "Session config cannot be null");
        
        if (config.hasChangedFrom(newConfig)) {
            this.config = newConfig;
            this.lastAccessAt = LocalDateTime.now();
            this.needsSave = true;
            
            // Add system message documenting the change
            if (!newConfig.getChangeHistory().isEmpty()) {
                var lastChange = newConfig.getChangeHistory().get(newConfig.getChangeHistory().size() - 1);
                String changeMsg = String.format("Configuration changed: %s", lastChange.toString());
                addMessage(Message.system(changeMsg));
            }
            
            logger.info("Updated config for session {}: {}", id, newConfig);
        }
    }
    
    /**
     * Gets recent messages up to a token limit.
     * Used for preparing context for LLM calls.
     */
    public List<Message> getRecentMessages(int tokenLimit) {
        List<Message> recent = new ArrayList<>();
        int tokenCount = 0;
        
        // Iterate backwards through messages
        for (int i = messages.size() - 1; i >= 0 && tokenCount < tokenLimit; i--) {
            Message msg = messages.get(i);
            int msgTokens = msg.calculateTokens();
            
            if (tokenCount + msgTokens <= tokenLimit) {
                recent.add(0, msg); // Add to front to maintain order
                tokenCount += msgTokens;
            } else {
                break; // Would exceed limit
            }
        }
        
        logger.debug("Retrieved {} recent messages ({} tokens) for session {}", 
                    recent.size(), tokenCount, id);
        return recent;
    }
    
    /**
     * Calculates total token count for all messages in session.
     */
    public int getTotalTokens() {
        return messages.stream()
                      .mapToInt(Message::calculateTokens)
                      .sum();
    }
    
    /**
     * Gets context optimized for LLM inference.
     * Includes summaries and recent messages within token limit.
     */
    public List<Message> getContextForLlm(int contextTokenLimit) {
        List<Message> context = new ArrayList<>();
        int tokenCount = 0;
        
        // First pass: collect summaries (they're prioritized)
        for (Message msg : messages) {
            if (msg.getType() == Message.Type.SUMMARY) {
                int msgTokens = msg.calculateTokens();
                if (tokenCount + msgTokens <= contextTokenLimit) {
                    context.add(msg);
                    tokenCount += msgTokens;
                }
            }
        }
        
        // Second pass: add recent non-summary messages
        List<Message> recentNonSummary = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg.getType() != Message.Type.SUMMARY) {
                int msgTokens = msg.calculateTokens();
                if (tokenCount + msgTokens <= contextTokenLimit) {
                    recentNonSummary.add(0, msg); // Maintain order
                    tokenCount += msgTokens;
                } else {
                    break; // Would exceed limit
                }
            }
        }
        
        context.addAll(recentNonSummary);
        
        logger.debug("Prepared LLM context: {} messages, {} tokens for session {}", 
                    context.size(), tokenCount, id);
        return context;
    }
    
    /**
     * Replaces a batch of messages with a summary message.
     * Used during summarization process.
     */
    public void replaceBatchWithSummary(int startIndex, int endIndex, Message summaryMessage) {
        if (startIndex < 0 || endIndex >= messages.size() || startIndex > endIndex) {
            throw new IllegalArgumentException("Invalid batch indices for summarization");
        }
        
        if (summaryMessage.getType() != Message.Type.SUMMARY) {
            throw new IllegalArgumentException("Replacement message must be of type SUMMARY");
        }
        
        // Remove the batch of messages
        List<Message> batch = new ArrayList<>();
        for (int i = endIndex; i >= startIndex; i--) {
            batch.add(0, messages.remove(i));
        }
        
        // Insert summary at the start position
        messages.add(startIndex, summaryMessage);
        
        this.lastAccessAt = LocalDateTime.now();
        this.needsSave = true;
        
        int batchTokens = batch.stream().mapToInt(Message::calculateTokens).sum();
        
        logger.info("Replaced batch of {} messages ({} tokens) with summary ({} tokens) in session {}", 
                   batch.size(), batchTokens, summaryMessage.calculateTokens(), id);
        
        // Recheck summarization need after replacement
        checkSummarizationNeeded();
    }
    
    /**
     * Marks session as saved (resets needsSave flag).
     */
    public void markSaved() {
        this.needsSave = false;
        logger.debug("Session {} marked as saved", id);
    }
    
    /**
     * Updates last access time.
     */
    public void touch() {
        this.lastAccessAt = LocalDateTime.now();
    }
    
    /**
     * Checks if summarization is needed based on token count.
     */
    private void checkSummarizationNeeded() {
        int totalTokens = getTotalTokens();
        this.needsSummarization = totalTokens > summaryThreshold;
        
        if (needsSummarization) {
            logger.info("Session {} needs summarization: {} tokens > {} threshold", 
                       id, totalTokens, summaryThreshold);
        }
    }
    
    /**
     * Gets statistics about the session.
     */
    public SessionStats getStats() {
        int totalMessages = messages.size();
        int totalTokens = getTotalTokens();
        int summaryCount = (int) messages.stream().filter(m -> m.getType() == Message.Type.SUMMARY).count();
        int userMessages = (int) messages.stream().filter(m -> m.getType() == Message.Type.USER).count();
        int assistantMessages = (int) messages.stream().filter(m -> m.getType() == Message.Type.ASSISTANT).count();
        
        return new SessionStats(totalMessages, totalTokens, summaryCount, userMessages, assistantMessages);
    }
    
    @Override
    public String toString() {
        return String.format("Session{id='%s', name='%s', messages=%d, tokens=%d, needsSave=%s}", 
                           id, name, messages.size(), getTotalTokens(), needsSave);
    }
    
    /**
     * Session statistics data class.
     */
    public static class SessionStats {
        public final int totalMessages;
        public final int totalTokens;
        public final int summaryCount;
        public final int userMessages;
        public final int assistantMessages;
        
        public SessionStats(int totalMessages, int totalTokens, int summaryCount, 
                           int userMessages, int assistantMessages) {
            this.totalMessages = totalMessages;
            this.totalTokens = totalTokens;
            this.summaryCount = summaryCount;
            this.userMessages = userMessages;
            this.assistantMessages = assistantMessages;
        }
        
        @Override
        public String toString() {
            return String.format("Stats{messages=%d, tokens=%d, summaries=%d, user=%d, assistant=%d}", 
                               totalMessages, totalTokens, summaryCount, userMessages, assistantMessages);
        }
    }
}
