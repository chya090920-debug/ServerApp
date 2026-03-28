package com.chat.server.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChatRoom {
    private String roomId;
    private String password;
    private boolean hasPassword;
    private Set<String> members;
    private List<ChatMessage> messages;
    private long createTime;
    private String creatorToken;

    public ChatRoom(String roomId, String creatorToken) {
        this.roomId = roomId;
        this.creatorToken = creatorToken;
        this.hasPassword = false;
        this.password = "";
        this.members = new HashSet<>();
        this.messages = new ArrayList<>();
        this.createTime = System.currentTimeMillis();
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        // 只保留最近500条消息
        if (messages.size() > 500) {
            messages.remove(0);
        }
    }

    public void addMember(String token) {
        members.add(token);
    }

    public void removeMember(String token) {
        members.remove(token);
    }

    public boolean verifyPassword(String inputPassword) {
        if (!hasPassword) return true;
        return password.equals(inputPassword);
    }

    // Getters and Setters
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public String getPassword() { return password; }
    public void setPassword(String password) { 
        this.password = password; 
        this.hasPassword = password != null && !password.isEmpty();
    }
    public boolean hasPassword() { return hasPassword; }
    public Set<String> getMembers() { return members; }
    public List<ChatMessage> getMessages() { return messages; }
    public long getCreateTime() { return createTime; }
    public String getCreatorToken() { return creatorToken; }
}
