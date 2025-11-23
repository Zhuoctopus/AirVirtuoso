package com.dstteam.zhuoctopus.airvirtuoso.model;

/**
 * Represents a chat message in the chatbot conversation
 */
public class ChatMessage {
    public enum MessageType {
        USER,    // Message from user
        BOT,     // Message from chatbot
        SYSTEM   // System message (e.g., "Typing...")
    }
    
    private final MessageType type;
    private final String message;
    private final long timestamp;
    
    public ChatMessage(MessageType type, String message) {
        this.type = type;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }
    
    public ChatMessage(MessageType type, String message, long timestamp) {
        this.type = type;
        this.message = message;
        this.timestamp = timestamp;
    }
    
    public MessageType getType() {
        return type;
    }
    
    public String getMessage() {
        return message;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public boolean isUser() {
        return type == MessageType.USER;
    }
    
    public boolean isBot() {
        return type == MessageType.BOT;
    }
    
    public boolean isSystem() {
        return type == MessageType.SYSTEM;
    }
}
