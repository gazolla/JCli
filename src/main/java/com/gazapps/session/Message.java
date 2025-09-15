package com.gazapps.session;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable message representation for session conversations.
 * Following KISS principle: simple, focused class with clear responsibilities.
 */
public final class Message {
    
    public enum Type {
        USER, ASSISTANT, SYSTEM, SUMMARY
    }
    
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final EncodingRegistry ENCODING_REGISTRY = Encodings.newDefaultEncodingRegistry();
    private static final Optional<Encoding> TOKENIZER = ENCODING_REGISTRY.getEncoding("cl100k_base"); // GPT-4 tokenizer
    
    private final Type type;
    private final String content;
    private final LocalDateTime timestamp;
    private final Map<String, Object> metadata;
    
    @JsonCreator
    public Message(
            @JsonProperty("type") Type type,
            @JsonProperty("content") String content,
            @JsonProperty("timestamp") LocalDateTime timestamp,
            @JsonProperty("metadata") Map<String, Object> metadata) {
        this.type = Objects.requireNonNull(type, "Message type cannot be null");
        this.content = Objects.requireNonNull(content, "Message content cannot be null");
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
        this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }
    
    // Convenience constructors
    public Message(Type type, String content) {
        this(type, content, LocalDateTime.now(), Map.of());
    }
    
    public Message(Type type, String content, Map<String, Object> metadata) {
        this(type, content, LocalDateTime.now(), metadata);
    }
    
    // Static factory methods for common message types
    public static Message user(String content) {
        return new Message(Type.USER, content);
    }
    
    public static Message assistant(String content) {
        return new Message(Type.ASSISTANT, content);
    }
    
    public static Message system(String content) {
        return new Message(Type.SYSTEM, content);
    }
    
    public static Message summary(String content) {
        return new Message(Type.SUMMARY, content);
    }
    
    // Getters
    public Type getType() { return type; }
    public String getContent() { return content; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public Map<String, Object> getMetadata() { return metadata; }
    
    /**
     * Calculates approximate token count using JTokkit.
     * Uses cl100k_base encoding (GPT-4 tokenizer) for consistency.
     */
    public int calculateTokens() {
        try {
            return TOKENIZER
                .map(encoding -> encoding.countTokens(content))
                .orElse(content.length() / 4); // Fallback if Optional is empty
        } catch (Exception e) {
            // Fallback: rough approximation (4 chars per token)
            return content.length() / 4;
        }
    }
    
    /**
     * Serializes message to JSON string for persistence.
     */
    public String toJson() {
        try {
            return JSON_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize message to JSON", e);
        }
    }
    
    /**
     * Deserializes message from JSON string.
     */
    public static Message fromJson(String json) {
        try {
            return JSON_MAPPER.readValue(json, Message.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize message from JSON", e);
        }
    }
    
    /**
     * Creates a copy with updated metadata.
     */
    public Message withMetadata(Map<String, Object> newMetadata) {
        return new Message(type, content, timestamp, newMetadata);
    }
    
    /**
     * Creates a copy with additional metadata entry.
     */
    public Message withMetadata(String key, Object value) {
        Map<String, Object> newMetadata = new java.util.HashMap<>(this.metadata);
        newMetadata.put(key, value);
        return new Message(type, content, timestamp, newMetadata);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Message message = (Message) obj;
        return type == message.type &&
               Objects.equals(content, message.content) &&
               Objects.equals(timestamp, message.timestamp) &&
               Objects.equals(metadata, message.metadata);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(type, content, timestamp, metadata);
    }
    
    @Override
    public String toString() {
        return String.format("Message{type=%s, content='%.50s%s', timestamp=%s, tokens=%d}", 
                           type, 
                           content.length() > 50 ? content.substring(0, 50) : content,
                           content.length() > 50 ? "..." : "",
                           timestamp,
                           calculateTokens());
    }
}
