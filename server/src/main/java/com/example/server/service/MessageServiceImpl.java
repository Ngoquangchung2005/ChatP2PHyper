package com.example.server.service;

import com.example.common.dto.MessageDTO;
import com.example.common.service.MessageService;
import com.example.server.config.Database;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MessageServiceImpl extends UnicastRemoteObject implements MessageService {

    public MessageServiceImpl() throws RemoteException {
        super();
    }

    @Override
    public void saveMessage(MessageDTO msg) throws RemoteException {
        String sql = "INSERT INTO messages (conversation_id, sender_id, content, created_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, msg.getConversationId());
            ps.setLong(2, msg.getSenderId());
            ps.setString(3, msg.getContent());
            ps.setTimestamp(4, Timestamp.valueOf(msg.getCreatedAt()));
            ps.executeUpdate();
            System.out.println("Đã lưu tin nhắn vào Conversation ID: " + msg.getConversationId());
        } catch (SQLException e) { e.printStackTrace(); }
    }

    @Override
    public List<MessageDTO> getHistory(long conversationId) throws RemoteException {
        List<MessageDTO> list = new ArrayList<>();
        String sql = "SELECT m.*, u.display_name FROM messages m " +
                "JOIN users u ON m.sender_id = u.id " +
                "WHERE conversation_id = ? ORDER BY created_at ASC LIMIT 50";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
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
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    @Override
    public long getPrivateConversationId(long user1, long user2) throws RemoteException {
        // 1. Tìm hội thoại cũ
        String sqlFind = "SELECT c.id FROM conversations c " +
                "JOIN conversation_members m1 ON c.id = m1.conversation_id " +
                "JOIN conversation_members m2 ON c.id = m2.conversation_id " +
                "WHERE c.is_group = FALSE AND m1.user_id = ? AND m2.user_id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlFind)) {
            ps.setLong(1, user1); ps.setLong(2, user2);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong("id");
        } catch (SQLException e) { e.printStackTrace(); }

        // 2. Tạo mới nếu chưa có
        Connection conn = null;
        try {
            conn = Database.getConnection();
            conn.setAutoCommit(false);
            long newConvId = 0;

            // Tạo Conversation
            String sqlConv = "INSERT INTO conversations (is_group, created_at) VALUES (FALSE, NOW())";
            try (PreparedStatement ps = conn.prepareStatement(sqlConv, Statement.RETURN_GENERATED_KEYS)) {
                ps.executeUpdate();
                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) newConvId = rs.getLong(1);
            }

            // Thêm thành viên
            String sqlMem = "INSERT INTO conversation_members (conversation_id, user_id) VALUES (?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sqlMem)) {
                ps.setLong(1, newConvId); ps.setLong(2, user1); ps.addBatch();
                ps.setLong(1, newConvId); ps.setLong(2, user2); ps.addBatch();
                ps.executeBatch();
            }
            conn.commit();
            return newConvId;
        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (Exception ex) {}
            e.printStackTrace();
        } finally {
            if (conn != null) try { conn.close(); } catch (Exception ex) {}
        }
        return 0;
    }
    // ... (Các hàm cũ giữ nguyên) ...

    @Override
    public Map<Long, Integer> getUnreadCounts(long userId) throws RemoteException {
        Map<Long, Integer> map = new java.util.HashMap<>();

        // Query đếm số tin nhắn được tạo SAU thời điểm last_seen_at của user
        String sql = "SELECT c.id as conv_id, c.is_group, COUNT(m.id) as unread " +
                "FROM conversation_members cm " +
                "JOIN conversations c ON cm.conversation_id = c.id " +
                "JOIN messages m ON c.id = m.conversation_id " +
                "WHERE cm.user_id = ? " +
                "  AND m.created_at > cm.last_seen_at " +
                "  AND m.sender_id != ? " + // Không đếm tin nhắn của chính mình
                "GROUP BY c.id, c.is_group";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, userId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                long convId = rs.getLong("conv_id");
                boolean isGroup = rs.getBoolean("is_group");
                int count = rs.getInt("unread");

                if (isGroup) {
                    // Nếu là nhóm: Key chính là ID nhóm (conv_id)
                    map.put(convId, count);
                } else {
                    // Nếu là chat 1-1: Key phải là ID của NGƯỜI BẠN kia
                    long friendId = getFriendIdFromConv(convId, userId);
                    if (friendId != 0) {
                        map.put(friendId, count);
                    }
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return map;
    }

    @Override
    public void markAsRead(long userId, long conversationId) throws RemoteException {
        // Cập nhật last_seen_at thành thời điểm hiện tại (NOW)
        String sql = "UPDATE conversation_members SET last_seen_at = NOW() WHERE conversation_id = ? AND user_id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, conversationId);
            ps.setLong(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // Hàm phụ: Tìm ID người kia trong cuộc hội thoại 1-1
    private long getFriendIdFromConv(long convId, long myId) {
        String sql = "SELECT user_id FROM conversation_members WHERE conversation_id = ? AND user_id != ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, convId);
            ps.setLong(2, myId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong("user_id");
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }
}