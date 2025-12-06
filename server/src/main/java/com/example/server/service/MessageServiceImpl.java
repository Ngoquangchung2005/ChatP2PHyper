package com.example.server.service;

import com.example.common.dto.MessageDTO;
import com.example.common.service.MessageService;
import com.example.server.config.Database;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MessageServiceImpl extends UnicastRemoteObject implements MessageService {
    // Thư mục lưu file trên Server
    private static final String STORAGE_DIR = "server_files";

    public MessageServiceImpl() throws RemoteException {
        super();
        // Tạo thư mục nếu chưa có
        new File(STORAGE_DIR).mkdirs();
    }


    @Override
    public void saveMessage(MessageDTO msg) throws RemoteException {
        // [CẬP NHẬT] Thêm attachment_url vào SQL
        String sql = "INSERT INTO messages (conversation_id, sender_id, content, created_at, attachment_url) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, msg.getConversationId());
            ps.setLong(2, msg.getSenderId());
            ps.setString(3, msg.getContent());
            ps.setTimestamp(4, Timestamp.valueOf(msg.getCreatedAt()));
            // Lưu đường dẫn file (có thể null)
            ps.setString(5, msg.getAttachmentUrl());

            ps.executeUpdate();
            System.out.println("Đã lưu tin nhắn (kèm file: " + (msg.getAttachmentUrl() != null) + ")");
        } catch (SQLException e) { e.printStackTrace(); }
    }
    @Override
    public List<MessageDTO> getHistory(long conversationId) throws RemoteException {
        List<MessageDTO> list = new ArrayList<>();
        // [CẬP NHẬT] Select thêm attachment_url
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

                // [MỚI] Lấy đường dẫn file
                String attachUrl = rs.getString("attachment_url");
                msg.setAttachmentUrl(attachUrl);

                // Khôi phục loại tin nhắn dựa trên nội dung hoặc file
                if (attachUrl != null) {
                    if (msg.getContent().contains("[Hình ảnh]")) msg.setType(MessageDTO.MessageType.IMAGE);
                    else if (msg.getContent().contains("[Tin nhắn thoại]")) msg.setType(MessageDTO.MessageType.AUDIO);
                    else msg.setType(MessageDTO.MessageType.FILE);

                    // Lưu ý: Ta KHÔNG tải fileData ngay ở đây để tránh nặng đường truyền
                    // Client sẽ gọi downloadFile khi cần hiển thị
                } else {
                    msg.setType(MessageDTO.MessageType.TEXT);
                }

                list.add(msg);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }
    // --- [MỚI] IMPLEMENT UPLOAD FILE ---
    @Override
    public String uploadFile(byte[] fileData, String fileName) throws RemoteException {
        if (fileData == null || fileData.length == 0) return null;

        try {
            // Tạo tên file ngẫu nhiên để tránh trùng: uuid_filename
            String savedName = UUID.randomUUID().toString() + "_" + fileName;
            File dest = new File(STORAGE_DIR, savedName);

            try (FileOutputStream fos = new FileOutputStream(dest)) {
                fos.write(fileData);
            }

            System.out.println("Server đã nhận file: " + savedName);
            return savedName; // Trả về tên file để lưu vào DB

        } catch (IOException e) {
            e.printStackTrace();
            throw new RemoteException("Lỗi lưu file trên server: " + e.getMessage());
        }
    }

    // --- [MỚI] IMPLEMENT DOWNLOAD FILE ---
    @Override
    public byte[] downloadFile(String serverPath) throws RemoteException {
        try {
            File file = new File(STORAGE_DIR, serverPath);
            if (!file.exists()) return null;

            try (FileInputStream fis = new FileInputStream(file)) {
                return fis.readAllBytes();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
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

        // Query đếm tin nhắn:
        // Lấy các tin nhắn (messages) thuộc hội thoại mà user tham gia
        // Điều kiện: Thời gian tạo tin nhắn > thời gian xem cuối cùng của user (last_seen_at)
        // Và: Người gửi tin nhắn KHÔNG phải là chính user đó
        String sql = "SELECT c.id as conv_id, c.is_group, COUNT(m.id) as unread " +
                "FROM conversation_members cm " +
                "JOIN conversations c ON cm.conversation_id = c.id " +
                "JOIN messages m ON c.id = m.conversation_id " +
                "WHERE cm.user_id = ? " +
                "  AND m.created_at > cm.last_seen_at " +
                "  AND m.sender_id != ? " +
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
                    // Chat nhóm: Key là ID nhóm
                    map.put(convId, count);
                } else {
                    // Chat 1-1: Key là ID của người bạn (phải tìm ID người kia trong hội thoại này)
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
        // Cập nhật thời điểm xem cuối cùng là NOW()
        String sql = "UPDATE conversation_members SET last_seen_at = NOW() WHERE conversation_id = ? AND user_id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, conversationId);
            ps.setLong(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // Hàm phụ: Tìm ID người kia trong hội thoại 1-1
    private long getFriendIdFromConv(long convId, long myId) {
        String sql = "SELECT user_id FROM conversation_members WHERE conversation_id = ? AND user_id != ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, convId);
            ps.setLong(2, myId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong("user_id");
        } catch (SQLException e) {}
        return 0;
    }
}