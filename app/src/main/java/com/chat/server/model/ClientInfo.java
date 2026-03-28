package com.chat.server.model;

import org.java_websocket.WebSocket;
import java.util.HashSet;
import java.util.Set;

public class ClientInfo {
    private String token;
    private String displayName;
    private WebSocket connection;
    private Set<String> joinedRooms;
    private long connectTime;
    private boolean isBanned;
    private boolean isMuted;

    public ClientInfo(String token, WebSocket connection) {
        this.token = token;
        this.connection = connection;
        this.displayName = "用户" + token.substring(0, 6);
        this.joinedRooms = new HashSet<>();
        this.connectTime = System.currentTimeMillis();
        this.isBanned = false;
        this.isMuted = false;
    }

    // Getters and Setters
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public WebSocket getConnection() { return connection; }
    public void setConnection(WebSocket connection) { this.connection = connection; }
    public Set<String> getJoinedRooms() { return joinedRooms; }
    public long getConnectTime() { return connectTime; }
    public boolean isBanned() { return isBanned; }
    public void setBanned(boolean banned) { isBanned = banned; }
    public boolean isMuted() { return isMuted; }
    public void setMuted(boolean muted) { isMuted = muted; }
}
