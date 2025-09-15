package com.gazapps.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * H2 database implementation of SessionPersistence.
 * Following KISS principle: simple, reliable persistence with auto-initialization.
 */
public class SessionDatabase implements SessionPersistence {
    
    private static final Logger logger = LoggerFactory.getLogger(SessionDatabase.class);
    
    private final String dbPath;
    private final Connection connection;
    
    // DDL statements
    private static final String CREATE_SESSIONS_TABLE = """
        CREATE TABLE IF NOT EXISTS sessions (
            id VARCHAR(255) PRIMARY KEY,
            name VARCHAR(255) NOT NULL,
            config TEXT NOT NULL,
            created_at TIMESTAMP NOT NULL,
            last_access_at TIMESTAMP NOT NULL,
            message_count INTEGER DEFAULT 0,
            total_tokens INTEGER DEFAULT 0
        )
    """;
    
    private static final String CREATE_MESSAGES_TABLE = """
        CREATE TABLE IF NOT EXISTS messages (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            session_id VARCHAR(255) NOT NULL,
            sequence_number INTEGER NOT NULL,
            type VARCHAR(50) NOT NULL,
            content TEXT NOT NULL,
            metadata TEXT,
            timestamp TIMESTAMP NOT NULL,
            tokens INTEGER DEFAULT 0,
            FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE,
            UNIQUE (session_id, sequence_number)
        )
    """;
    
    private static final String CREATE_MESSAGES_INDEX = """
        CREATE INDEX IF NOT EXISTS idx_messages_session_seq 
        ON messages(session_id, sequence_number)
    """;
    
    /**
     * Initializes H2 database connection and creates tables.
     */
    public SessionDatabase(String dbPath) {
        this.dbPath = dbPath;
        
        try {
            // Configure H2 connection with AUTO_SERVER for debugging access
            String jdbcUrl = String.format("jdbc:h2:%s;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1", dbPath);
            this.connection = DriverManager.getConnection(jdbcUrl);
            
            // Initialize database schema
            initializeDatabase();
            
            logger.info("SessionDatabase initialized at: {}", dbPath);
            
        } catch (SQLException e) {
            logger.error("Failed to initialize session database at {}: {}", dbPath, e.getMessage());
            throw new RuntimeException("Cannot initialize session database", e);
        }
    }
    
    /**
     * Creates tables and indices if they don't exist.
     */
    private void initializeDatabase() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(CREATE_SESSIONS_TABLE);
            stmt.execute(CREATE_MESSAGES_TABLE);
            stmt.execute(CREATE_MESSAGES_INDEX);
            
            logger.info("Database schema verified/created");
        }
    }
    
    @Override
    public boolean saveSession(Session session) {
        try {
            connection.setAutoCommit(false);
            
            // Save or update session metadata
            saveSessionMetadata(session);
            
            // Save messages (replace all for simplicity)
            deleteSessionMessages(session.getId());
            saveSessionMessages(session);
            
            connection.commit();
            session.markSaved();
            
            logger.debug("Saved session {} with {} messages", session.getId(), session.getMessages().size());
            return true;
            
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                logger.error("Failed to rollback transaction", rollbackEx);
            }
            logger.error("Failed to save session {}: {}", session.getId(), e.getMessage());
            return false;
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                logger.error("Failed to reset auto-commit", e);
            }
        }
    }
    
    private void saveSessionMetadata(Session session) throws SQLException {
        String sql = """
            MERGE INTO sessions (id, name, config, created_at, last_access_at, message_count, total_tokens)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, session.getId());
            stmt.setString(2, session.getName());
            stmt.setString(3, session.getConfig().toJson());
            stmt.setTimestamp(4, Timestamp.valueOf(session.getCreatedAt()));
            stmt.setTimestamp(5, Timestamp.valueOf(session.getLastAccessAt()));
            stmt.setInt(6, session.getMessages().size());
            stmt.setInt(7, session.getTotalTokens());
            
            stmt.executeUpdate();
        }
    }
    
    private void deleteSessionMessages(String sessionId) throws SQLException {
        String sql = "DELETE FROM messages WHERE session_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, sessionId);
            stmt.executeUpdate();
        }
    }
    
    private void saveSessionMessages(Session session) throws SQLException {
        String sql = """
            INSERT INTO messages (session_id, sequence_number, type, content, metadata, timestamp, tokens)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            List<Message> messages = session.getMessages();
            
            for (int i = 0; i < messages.size(); i++) {
                Message msg = messages.get(i);
                
                stmt.setString(1, session.getId());
                stmt.setInt(2, i);
                stmt.setString(3, msg.getType().name());
                stmt.setString(4, msg.getContent());
                stmt.setString(5, msg.getMetadata().isEmpty() ? null : msg.toJson());
                stmt.setTimestamp(6, Timestamp.valueOf(msg.getTimestamp()));
                stmt.setInt(7, msg.calculateTokens());
                
                stmt.addBatch();
            }
            
            stmt.executeBatch();
        }
    }
    
    @Override
    public Optional<Session> loadSession(String sessionId) {
        try {
            // Load session metadata
            Optional<SessionData> sessionData = loadSessionMetadata(sessionId);
            if (sessionData.isEmpty()) {
                return Optional.empty();
            }
            
            // Load messages
            List<Message> messages = loadSessionMessages(sessionId);
            
            // Reconstruct session
            SessionData data = sessionData.get();
            Session session = new Session(
                data.id,
                data.name,
                data.config,
                data.createdAt,
                data.lastAccessAt,
                messages,
                150000, // TODO: get from config
                100000  // TODO: get from config
            );
            
            logger.debug("Loaded session {} with {} messages", sessionId, messages.size());
            return Optional.of(session);
            
        } catch (SQLException e) {
            logger.error("Failed to load session {}: {}", sessionId, e.getMessage());
            return Optional.empty();
        }
    }
    
    private Optional<SessionData> loadSessionMetadata(String sessionId) throws SQLException {
        String sql = "SELECT id, name, config, created_at, last_access_at FROM sessions WHERE id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, sessionId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new SessionData(
                        rs.getString("id"),
                        rs.getString("name"),
                        SessionConfig.fromJson(rs.getString("config")),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getTimestamp("last_access_at").toLocalDateTime()
                    ));
                }
            }
        }
        
        return Optional.empty();
    }
    
    private List<Message> loadSessionMessages(String sessionId) throws SQLException {
        String sql = """
            SELECT type, content, metadata, timestamp 
            FROM messages 
            WHERE session_id = ? 
            ORDER BY sequence_number
        """;
        
        List<Message> messages = new ArrayList<>();
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, sessionId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Message.Type type = Message.Type.valueOf(rs.getString("type"));
                    String content = rs.getString("content");
                    LocalDateTime timestamp = rs.getTimestamp("timestamp").toLocalDateTime();
                    
                    // Handle metadata
                    String metadataJson = rs.getString("metadata");
                    java.util.Map<String, Object> metadata = java.util.Map.of();
                    if (metadataJson != null && !metadataJson.trim().isEmpty()) {
                        try {
                            // Simple extraction for now - could be enhanced
                            metadata = java.util.Map.of();
                        } catch (Exception e) {
                            logger.warn("Failed to parse message metadata: {}", e.getMessage());
                        }
                    }
                    
                    messages.add(new Message(type, content, timestamp, metadata));
                }
            }
        }
        
        return messages;
    }
    
    @Override
    public List<SessionMetadata> listSessions() {
        String sql = """
            SELECT id, name, created_at, last_access_at, message_count, total_tokens 
            FROM sessions 
            ORDER BY last_access_at DESC
        """;
        
        List<SessionMetadata> sessions = new ArrayList<>();
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                sessions.add(new SessionMetadata(
                    rs.getString("id"),
                    rs.getString("name"),
                    rs.getTimestamp("created_at").toLocalDateTime(),
                    rs.getTimestamp("last_access_at").toLocalDateTime(),
                    rs.getInt("message_count"),
                    rs.getInt("total_tokens")
                ));
            }
            
        } catch (SQLException e) {
            logger.error("Failed to list sessions: {}", e.getMessage());
        }
        
        return sessions;
    }
    
    @Override
    public boolean deleteSession(String sessionId) {
        String sql = "DELETE FROM sessions WHERE id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, sessionId);
            int deleted = stmt.executeUpdate();
            
            if (deleted > 0) {
                logger.info("Deleted session: {}", sessionId);
                return true;
            } else {
                logger.warn("Session not found for deletion: {}", sessionId);
                return false;
            }
            
        } catch (SQLException e) {
            logger.error("Failed to delete session {}: {}", sessionId, e.getMessage());
            return false;
        }
    }
    
    @Override
    public boolean sessionExists(String sessionId) {
        String sql = "SELECT 1 FROM sessions WHERE id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, sessionId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
            
        } catch (SQLException e) {
            logger.error("Failed to check session existence {}: {}", sessionId, e.getMessage());
            return false;
        }
    }
    
    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                logger.info("SessionDatabase connection closed");
            }
        } catch (SQLException e) {
            logger.error("Error closing database connection: {}", e.getMessage());
        }
    }
    
    /**
     * Helper class for session metadata during loading.
     */
    private static class SessionData {
        final String id;
        final String name;
        final SessionConfig config;
        final LocalDateTime createdAt;
        final LocalDateTime lastAccessAt;
        
        SessionData(String id, String name, SessionConfig config, 
                   LocalDateTime createdAt, LocalDateTime lastAccessAt) {
            this.id = id;
            this.name = name;
            this.config = config;
            this.createdAt = createdAt;
            this.lastAccessAt = lastAccessAt;
        }
    }
}
