package com.gazapps.session;

import com.gazapps.llm.Llm;
import com.gazapps.llm.LlmResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;


public class ConversationSummarizer {
    
    private static final Logger logger = LoggerFactory.getLogger(ConversationSummarizer.class);
    
    private final Llm llm;
    
    // Summarization prompts
    private static final String SUMMARY_PROMPT_TEMPLATE = """
        Please summarize the following conversation messages concisely, preserving key information:
        
        - Technical decisions and solutions implemented
        - Files created, modified, or discussed
        - Problems identified and resolved
        - Important context for future queries
        - Configuration changes made
        
        Messages to summarize:
        %s
        
        Provide a concise summary (2-3 sentences max) that captures the essential information:
        """;
    
    private static final int MIN_BATCH_SIZE = 5; // Minimum messages to summarize at once
    private static final int MAX_BATCH_SIZE = 20; // Maximum messages to summarize at once
    private static final double SUMMARIZATION_RATIO = 0.3; // Keep recent 30% of messages unsummarized
    
    public ConversationSummarizer(Llm llm) {
        this.llm = Objects.requireNonNull(llm, "LLM cannot be null");
    }
    
    /**
     * Checks if summarization is needed and performs it if necessary.
     */
    public boolean summarizeIfNeeded(Session session) {
        Objects.requireNonNull(session, "Session cannot be null");
        
        if (!shouldSummarize(session)) {
            return false;
        }
        
        try {
            return performSummarization(session);
        } catch (Exception e) {
            logger.error("Summarization failed for session {}: {}", session.getId(), e.getMessage());
            
            // Fallback: remove old messages without summarizing
            return performFallbackCleanup(session);
        }
    }
    
    /**
     * Determines if summarization is needed based on token count.
     */
    public boolean shouldSummarize(Session session) {
        if (session == null) return false;
        
        int totalTokens = session.getTotalTokens();
        int threshold = getSessionSummaryThreshold(session);
        
        boolean needsSummarization = totalTokens > threshold;
        
        if (needsSummarization) {
            logger.info("Session {} needs summarization: {} tokens > {} threshold", 
                       session.getId(), totalTokens, threshold);
        }
        
        return needsSummarization;
    }
    
    /**
     * Performs the actual summarization process.
     */
    private boolean performSummarization(Session session) {
        List<Message> messages = session.getMessages();
        
        if (messages.size() < MIN_BATCH_SIZE) {
            logger.debug("Too few messages to summarize in session {}", session.getId());
            return false;
        }
        
        // Calculate how many messages to keep recent (unsummarized)
        int totalMessages = messages.size();
        int messagesToKeepRecent = Math.max(MIN_BATCH_SIZE, (int) (totalMessages * SUMMARIZATION_RATIO));
        int messagesToSummarize = totalMessages - messagesToKeepRecent;
        
        if (messagesToSummarize < MIN_BATCH_SIZE) {
            logger.debug("Not enough old messages to summarize in session {}", session.getId());
            return false;
        }
        
        // Find a good batch to summarize (exclude existing summaries from the batch)
        SummarizationBatch batch = findBestBatchToSummarize(messages, messagesToSummarize);
        
        if (batch == null || batch.messages.isEmpty()) {
            logger.warn("No suitable batch found for summarization in session {}", session.getId());
            return false;
        }
        
        // Generate summary
        String summaryContent = generateSummary(batch.messages);
        
        if (summaryContent == null || summaryContent.trim().isEmpty()) {
            logger.error("Failed to generate summary for session {}", session.getId());
            return performFallbackCleanup(session);
        }
        
        // Replace batch with summary
        Message summaryMessage = Message.summary(summaryContent);
        session.replaceBatchWithSummary(batch.startIndex, batch.endIndex, summaryMessage);
        
        logger.info("Successfully summarized {} messages into summary for session {}: '{}...'", 
                   batch.messages.size(), session.getId(), 
                   summaryContent.length() > 50 ? summaryContent.substring(0, 50) : summaryContent);
        
        return true;
    }
    
    /**
     * Finds the best batch of messages to summarize.
     */
    private SummarizationBatch findBestBatchToSummarize(List<Message> messages, int maxMessagesToSummarize) {
        // Start from the beginning, but skip existing summaries
        int startIndex = 0;
        
        // Skip any existing summaries at the beginning
        while (startIndex < messages.size() && messages.get(startIndex).getType() == Message.Type.SUMMARY) {
            startIndex++;
        }
        
        if (startIndex >= messages.size()) {
            return null; // All messages are summaries
        }
        
        // Find a batch of non-summary messages
        List<Message> batchMessages = new java.util.ArrayList<>();
        int currentIndex = startIndex;
        int batchSize = Math.min(maxMessagesToSummarize, MAX_BATCH_SIZE);
        
        while (currentIndex < messages.size() && batchMessages.size() < batchSize) {
            Message msg = messages.get(currentIndex);
            
            // Stop if we hit another summary (keep summaries separate)
            if (msg.getType() == Message.Type.SUMMARY && !batchMessages.isEmpty()) {
                break;
            }
            
            // Add non-summary messages to batch
            if (msg.getType() != Message.Type.SUMMARY) {
                batchMessages.add(msg);
            }
            
            currentIndex++;
        }
        
        if (batchMessages.size() < MIN_BATCH_SIZE) {
            return null;
        }
        
        return new SummarizationBatch(startIndex, currentIndex - 1, batchMessages);
    }
    
    /**
     * Generates a summary using the LLM.
     */
    private String generateSummary(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        
        try {
            // Format messages for summarization
            StringBuilder messagesText = new StringBuilder();
            for (Message msg : messages) {
                messagesText.append(String.format("[%s] %s\n", 
                                                 msg.getType().name(), 
                                                 msg.getContent()));
            }
            
            // Create summarization prompt
            String prompt = String.format(SUMMARY_PROMPT_TEMPLATE, messagesText.toString());
            
            // Get summary from LLM
            LlmResponse response = llm.generateResponse(prompt);
            
            if (response != null && response.getContent() != null && !response.getContent().trim().isEmpty()) {
                String summary = response.getContent().trim();
                
                // Validate summary is reasonable
                if (summary.length() > 10 && summary.length() < 1000) {
                    return summary;
                } else {
                    logger.warn("Generated summary has unusual length: {} characters", summary.length());
                    return summary; // Use it anyway
                }
            } else {
                logger.error("LLM returned empty or null response for summarization");
                return null;
            }
            
        } catch (Exception e) {
            logger.error("Failed to generate summary using LLM: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Fallback cleanup when summarization fails - removes old messages without summarizing.
     */
    private boolean performFallbackCleanup(Session session) {
        List<Message> messages = session.getMessages();
        
        if (messages.size() <= MIN_BATCH_SIZE) {
            return false; // Can't clean up if too few messages
        }
        
        // Keep only the most recent messages (50% of total)
        int messagesToKeep = Math.max(MIN_BATCH_SIZE, messages.size() / 2);
        int messagesToRemove = messages.size() - messagesToKeep;
        
        if (messagesToRemove <= 0) {
            return false;
        }
        
        // Create a fallback summary message
        String fallbackSummary = String.format(
            "Previous conversation (%d messages) removed due to token limit. " +
            "Session history continues from this point.", 
            messagesToRemove
        );
        
        Message fallbackSummaryMessage = Message.summary(fallbackSummary);
        
        // Replace the old messages with fallback summary
        session.replaceBatchWithSummary(0, messagesToRemove - 1, fallbackSummaryMessage);
        
        logger.warn("Performed fallback cleanup for session {}: removed {} messages", 
                   session.getId(), messagesToRemove);
        
        return true;
    }
    
    /**
     * Gets the summarization threshold for a session.
     */
    private int getSessionSummaryThreshold(Session session) {
        // For now, use a hardcoded value. In future, this could be configurable per session
        return 100000; // tokens
    }
    
    /**
     * Helper class to represent a batch of messages for summarization.
     */
    private static class SummarizationBatch {
        final int startIndex;
        final int endIndex;
        final List<Message> messages;
        
        SummarizationBatch(int startIndex, int endIndex, List<Message> messages) {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.messages = messages;
        }
    }
}
