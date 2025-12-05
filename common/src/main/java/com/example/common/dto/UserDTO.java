package com.example.common.dto;

import java.io.Serializable;

public class UserDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private int unreadCount = 0; // [MỚI] Số tin nhắn chưa đọc

    private long id;
    private String username;
    private String displayName;
    private boolean isOnline;
    private String lastIp; // IP để kết nối P2P
    private int lastPort;  // Port để kết nối P2P

    public UserDTO() {}

    public UserDTO(long id, String username, String displayName) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
    }

    // --- Getters & Setters ---
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public boolean isOnline() { return isOnline; }
    public void setOnline(boolean online) { isOnline = online; }
    public String getLastIp() { return lastIp; }
    public void setLastIp(String lastIp) { this.lastIp = lastIp; }
    public int getLastPort() { return lastPort; }
    public void setLastPort(int lastPort) { this.lastPort = lastPort; }
    public int getUnreadCount() { return unreadCount; }
    public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }

    @Override
    public String toString() { return displayName; } // Để hiển thị trên ListView
}