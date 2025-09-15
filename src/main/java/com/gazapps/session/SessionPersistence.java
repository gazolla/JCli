package com.gazapps.session;

import java.util.List;
import java.util.Optional;

/**
 * Interface for session persistence operations.
 * Following KISS principle: simple contract with essential operations.
 */
public interface SessionPersistence {
    
    /**
     * Saves a session to persistent storage.
     * @param session the session to save
     * @return true if save was successful, false otherwise
     */
    boolean saveSession(Session session);
    
    /**
     * Loads a session by ID from persistent storage.
     * @param sessionId the unique session identifier
     * @return Optional containing the session if found, empty otherwise
     */
    Optional<Session> loadSession(String sessionId);
    
    /**
     * Lists all available sessions with basic metadata.
     * @return list of session metadata
     */
    List<SessionMetadata> listSessions();
    
    /**
     * Deletes a session from persistent storage.
     * @param sessionId the unique session identifier
     * @return true if deletion was successful, false otherwise
     */
    boolean deleteSession(String sessionId);
    
    /**
     * Checks if a session exists in storage.
     * @param sessionId the unique session identifier
     * @return true if session exists, false otherwise
     */
    boolean sessionExists(String sessionId);
    
    /**
     * Closes the persistence layer and releases resources.
     */
    void close();
    
    /**
     * Session metadata for listing purposes.
     */
    class SessionMetadata {
        public final String id;
        public final String name;
        public final java.time.LocalDateTime createdAt;
        public final java.time.LocalDateTime lastAccessAt;
        public final int messageCount;
        public final int totalTokens;
        
        public SessionMetadata(String id, String name, java.time.LocalDateTime createdAt, 
                             java.time.LocalDateTime lastAccessAt, int messageCount, int totalTokens) {
            this.id = id;
            this.name = name;
            this.createdAt = createdAt;
            this.lastAccessAt = lastAccessAt;
            this.messageCount = messageCount;
            this.totalTokens = totalTokens;
        }
        
        @Override
        public String toString() {
            return String.format("Session{id='%s', name='%s', messages=%d, tokens=%d, lastAccess=%s}", 
                               id, name, messageCount, totalTokens, lastAccessAt);
        }
    }
}
