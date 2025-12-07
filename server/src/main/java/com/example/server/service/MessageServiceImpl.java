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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MessageServiceImpl extends UnicastRemoteObject implements MessageService {
    private static final String STORAGE_DIR = "server_files";

    public MessageServiceImpl() throws RemoteException {
        super();
        new File(STORAGE_DIR).mkdirs();
    }

    @Override
    public void saveMessage(MessageDTO msg) throws RemoteException {
        String sql = "INSERT INTO messages (conversation_id, sender_id, content, created_at, attachment_url, uuid) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, msg.getConversationId());
            ps.setLong(2, msg.getSenderId());
            // [AN TOÀN] Nếu content null thì lưu chuỗi rỗng
            ps.setString(3, msg.getContent() != null ? msg.getContent() : "");
            ps.setTimestamp(4, Timestamp.valueOf(msg.getCreatedAt()));
            ps.setString(5, msg.getAttachmentUrl());
            ps.setString(6, msg.getUuid()); // Lưu UUID

            int rows = ps.executeUpdate();
            if (rows > 0) {
                System.out.println(">> Server: Đã lưu tin nhắn của User " + msg.getSenderId());
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println(">> Lỗi SQL khi lưu tin nhắn: " + e.getMessage());
        }
    }
    // Hàm xử lý Sửa / Thu hồi
    public void updateMessage(String uuid, String newContent, MessageDTO.MessageType type) throws RemoteException {
        String sql = "";
        if (type == MessageDTO.MessageType.RECALL) {
            sql = "UPDATE messages SET content = 'Tin nhắn đã thu hồi', attachment_url = NULL WHERE uuid = ?";
        } else if (type == MessageDTO.MessageType.EDIT) {
            sql = "UPDATE messages SET content = ? WHERE uuid = ?";
        }

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (type == MessageDTO.MessageType.EDIT) {
                ps.setString(1, newContent);
                ps.setString(2, uuid);
            } else {
                ps.setString(1, uuid);
            }
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    @Override
    public List<MessageDTO> getHistory(long conversationId) throws RemoteException {
        List<MessageDTO> list = new ArrayList<>();

        String sql = "SELECT m.*, u.display_name FROM messages m " +
                "JOIN users u ON m.sender_id = u.id " +
                "WHERE conversation_id = ? ORDER BY created_at DESC LIMIT 50";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, conversationId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                MessageDTO msg = new MessageDTO();
                msg.setId(rs.getLong("id"));
                msg.setSenderId(rs.getLong("sender_id"));
                msg.setSenderName(rs.getString("display_name"));

                // [SỬA LỖI NPE TẠI ĐÂY] Kiểm tra null khi lấy content
                String content = rs.getString("content");
                msg.setContent(content != null ? content : ""); // Nếu null thì gán rỗng

                try {
                    msg.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                } catch (Exception e) {
                    msg.setCreatedAt(java.time.LocalDateTime.now()); // Fallback nếu lỗi thời gian
                }

                String attachUrl = rs.getString("attachment_url");
                msg.setAttachmentUrl(attachUrl);

                // Logic khôi phục loại tin nhắn (Giờ đã an toàn vì msg.getContent() không bao giờ null)
                if (attachUrl != null && !attachUrl.isEmpty()) {
                    if (msg.getContent().contains("[Hình ảnh]")) {
                        msg.setType(MessageDTO.MessageType.IMAGE);
                    } else if (msg.getContent().contains("[Tin nhắn thoại]")) {
                        msg.setType(MessageDTO.MessageType.AUDIO);
                    } else {
                        msg.setType(MessageDTO.MessageType.FILE);
                    }
                } else {
                    msg.setType(MessageDTO.MessageType.TEXT);
                }

                list.add(msg);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        Collections.reverse(list);
        return list;
    }

    // --- CÁC HÀM UPLOAD/DOWNLOAD KHÔNG ĐỔI ---
    @Override
    public String uploadFile(byte[] fileData, String fileName) throws RemoteException {
        if (fileData == null || fileData.length == 0) return null;
        try {
            String savedName = UUID.randomUUID().toString() + "_" + fileName;
            File dest = new File(STORAGE_DIR, savedName);
            try (FileOutputStream fos = new FileOutputStream(dest)) {
                fos.write(fileData);
            }
            System.out.println(">> Server: Đã nhận file " + savedName);
            return savedName;
        } catch (IOException e) {
            e.printStackTrace();
            throw new RemoteException("Lỗi lưu file: " + e.getMessage());
        }
    }

    @Override
    public byte[] downloadFile(String serverPath) throws RemoteException {
        if (serverPath == null) return null;
        try {
            File file = new File(STORAGE_DIR, serverPath);
            if (!file.exists()) return null;
            try (FileInputStream fis = new FileInputStream(file)) {
                return fis.readAllBytes();
            }
        } catch (IOException e) { return null; }
    }

    @Override
    public long getPrivateConversationId(long user1, long user2) throws RemoteException {
        String sqlFind = "SELECT c.id FROM conversations c JOIN conversation_members m1 ON c.id = m1.conversation_id JOIN conversation_members m2 ON c.id = m2.conversation_id WHERE c.is_group = FALSE AND m1.user_id = ? AND m2.user_id = ?";
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sqlFind)) {
            ps.setLong(1, user1); ps.setLong(2, user2);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong("id");
        } catch (SQLException e) {}

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO conversations (is_group, created_at) VALUES (FALSE, NOW())", Statement.RETURN_GENERATED_KEYS)) {
                ps.executeUpdate();
                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) {
                    long id = rs.getLong(1);
                    try (PreparedStatement psMem = conn.prepareStatement("INSERT INTO conversation_members (conversation_id, user_id) VALUES (?, ?)")) {
                        psMem.setLong(1, id); psMem.setLong(2, user1); psMem.addBatch();
                        psMem.setLong(1, id); psMem.setLong(2, user2); psMem.addBatch();
                        psMem.executeBatch();
                    }
                    conn.commit();
                    return id;
                }
            }
        } catch (SQLException e) {}
        return 0;
    }

    @Override
    public Map<Long, Integer> getUnreadCounts(long userId) throws RemoteException {
        Map<Long, Integer> map = new java.util.HashMap<>();
        String sql = "SELECT c.id as conv_id, c.is_group, COUNT(m.id) as unread FROM conversation_members cm JOIN conversations c ON cm.conversation_id = c.id JOIN messages m ON c.id = m.conversation_id WHERE cm.user_id = ? AND m.created_at > cm.last_seen_at AND m.sender_id != ? GROUP BY c.id, c.is_group";
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId); ps.setLong(2, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                long convId = rs.getLong("conv_id");
                boolean isGroup = rs.getBoolean("is_group");
                int count = rs.getInt("unread");
                if (isGroup) map.put(convId, count);
                else {
                    long friendId = getFriendIdFromConv(convId, userId);
                    if (friendId != 0) map.put(friendId, count);
                }
            }
        } catch (SQLException e) {}
        return map;
    }

    @Override
    public void markAsRead(long userId, long conversationId) throws RemoteException {
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement("UPDATE conversation_members SET last_seen_at = NOW() WHERE conversation_id = ? AND user_id = ?")) {
            ps.setLong(1, conversationId); ps.setLong(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {}
    }

    private long getFriendIdFromConv(long convId, long myId) {
        String sql = "SELECT user_id FROM conversation_members WHERE conversation_id = ? AND user_id != ?";
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, convId); ps.setLong(2, myId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong("user_id");
        } catch (SQLException e) {}
        return 0;
    }
}