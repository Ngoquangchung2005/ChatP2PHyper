package com.example.server.service;

import com.example.common.dto.MessageDTO;
import com.example.common.dto.UserDTO;
import com.example.common.service.ChatService;
import com.example.server.config.Database;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ChatServiceImpl extends UnicastRemoteObject implements ChatService {

    public ChatServiceImpl() throws RemoteException {
        super();
    }

    @Override
    public List<UserDTO> getFriendList(long userId) throws RemoteException {
        List<UserDTO> list = new ArrayList<>();

        // 1. LẤY DANH SÁCH BẠN BÈ
        String sqlFriends = "SELECT id, username, display_name, is_online, last_ip, last_port FROM users WHERE id != ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlFriends)) {
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                UserDTO u = new UserDTO();
                u.setId(rs.getLong("id"));
                u.setUsername(rs.getString("username"));
                u.setDisplayName(rs.getString("display_name"));
                u.setOnline(rs.getBoolean("is_online"));
                u.setLastIp(rs.getString("last_ip"));
                u.setLastPort(rs.getInt("last_port"));
                list.add(u);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // 2. LẤY DANH SÁCH NHÓM CHAT
        String sqlGroups = "SELECT c.id, c.name FROM conversations c " +
                "JOIN conversation_members cm ON c.id = cm.conversation_id " +
                "WHERE cm.user_id = ? AND c.is_group = TRUE";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlGroups)) {
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                UserDTO group = new UserDTO();
                group.setId(rs.getLong("id"));
                group.setDisplayName("[Nhóm] " + rs.getString("name"));
                group.setUsername("GROUP");
                group.setOnline(true);
                list.add(group);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return list;
    }

    // ... (Các hàm searchUsers, addFriend, createGroup, getGroupMemberIds giữ nguyên) ...
    @Override
    public List<UserDTO> searchUsers(String query) throws RemoteException { return new ArrayList<>(); }
    @Override
    public boolean addFriend(long userId, long friendId) throws RemoteException { return true; }
    @Override
    public long createGroup(String groupName, List<Long> memberIds) throws RemoteException { return 0; } // Lưu ý: Hàm này nên dùng GroupService
    @Override
    public List<Long> getGroupMemberIds(long conversationId) throws RemoteException { return new ArrayList<>(); } // Lưu ý: Hàm này nên dùng GroupService

    // --- SỬA 2 HÀM DƯỚI ĐÂY ---

    @Override
    public void saveMessage(MessageDTO msg) throws RemoteException {
        String sql = "INSERT INTO messages (conversation_id, sender_id, content, created_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // [SỬA LẠI]: Dùng ID thật từ message gửi lên
            ps.setLong(1, msg.getConversationId());

            ps.setLong(2, msg.getSenderId());
            ps.setString(3, msg.getContent());
            ps.setTimestamp(4, Timestamp.valueOf(msg.getCreatedAt()));
            ps.executeUpdate();
            System.out.println("Đã lưu tin nhắn vào Conversation ID: " + msg.getConversationId());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<MessageDTO> getHistory(long conversationId) throws RemoteException {
        List<MessageDTO> list = new ArrayList<>();
        String sql = "SELECT m.*, u.display_name FROM messages m " +
                "JOIN users u ON m.sender_id = u.id " +
                "WHERE conversation_id = ? ORDER BY created_at ASC LIMIT 50";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // [SỬA LẠI]: Dùng ID được truyền vào tham số
            ps.setLong(1, conversationId);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                MessageDTO msg = new MessageDTO();
                msg.setId(rs.getLong("id"));
                msg.setSenderId(rs.getLong("sender_id"));
                msg.setSenderName(rs.getString("display_name"));
                msg.setContent(rs.getString("content"));
                msg.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                list.add(msg);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public UserDTO getUserInfo(long userId) throws RemoteException {
        String sql = "SELECT id, last_ip, last_port, is_online FROM users WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                UserDTO u = new UserDTO();
                u.setId(rs.getLong("id"));
                u.setLastIp(rs.getString("last_ip"));
                u.setLastPort(rs.getInt("last_port"));
                u.setOnline(rs.getBoolean("is_online"));
                return u;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}