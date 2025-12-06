package com.example.common.dto;

import java.io.Serializable;

public class UserDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private int unreadCount = 0;

    private long id;
    private String username;
    private String displayName;
    private boolean isOnline;
    private String lastIp;
    private int lastPort;

    // --- [MỚI] ---
    private String avatarUrl;   // Đường dẫn ảnh đại diện trên Server
    private String statusMsg;   // Ví dụ: "Đang bận", "Vui vẻ"...

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

    // [MỚI] Getter/Setter
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getStatusMsg() { return statusMsg; }
    public void setStatusMsg(String statusMsg) { this.statusMsg = statusMsg; }

    @Override
    public String toString() { return displayName; }
}