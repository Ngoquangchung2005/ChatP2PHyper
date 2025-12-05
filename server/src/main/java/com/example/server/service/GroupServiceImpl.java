package com.example.server.service;

import com.example.common.dto.UserDTO;
import com.example.common.service.ClientCallback; // Nhớ import
import com.example.common.service.GroupService;
import com.example.server.config.Database;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GroupServiceImpl extends UnicastRemoteObject implements GroupService {

    public GroupServiceImpl() throws RemoteException {
        super();
    }

    @Override
    public long createGroup(String groupName, List<Long> memberIds) throws RemoteException {
        long conversationId = 0;
        Connection conn = null;
        try {
            conn = Database.getConnection();
            conn.setAutoCommit(false);

            // 1. Tạo Conversation
            String sqlConv = "INSERT INTO conversations (name, is_group, created_at) VALUES (?, TRUE, NOW())";
            try (PreparedStatement ps = conn.prepareStatement(sqlConv, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, groupName);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) conversationId = rs.getLong(1);
                }
            }

            // 2. Thêm thành viên
            String sqlMem = "INSERT INTO conversation_members (conversation_id, user_id) VALUES (?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sqlMem)) {
                for (Long userId : memberIds) {
                    ps.setLong(1, conversationId);
                    ps.setLong(2, userId);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();

            // 3. [MỚI] THÔNG BÁO REAL-TIME CHO CÁC THÀNH VIÊN
            if (conversationId > 0) {
                notifyGroupMembers(conversationId, groupName, memberIds);
            }

            return conversationId;
        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) {}
            e.printStackTrace();
            return 0;
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException e) {}
        }
    }

    // --- HÀM BẮN THÔNG BÁO ---
    private void notifyGroupMembers(long groupId, String groupName, List<Long> memberIds) {
        // Tạo đối tượng Group (giả lập UserDTO như logic cũ) để hiển thị lên List
        UserDTO groupDTO = new UserDTO();
        groupDTO.setId(groupId);
        groupDTO.setDisplayName("[Nhóm] " + groupName);
        groupDTO.setUsername("GROUP");
        groupDTO.setOnline(true);

        // Duyệt qua từng thành viên
        for (Long userId : memberIds) {
            // Lấy callback của người đó từ AuthServiceImpl
            ClientCallback cb = AuthServiceImpl.getClientCallback(userId);
            if (cb != null) {
                new Thread(() -> {
                    try {
                        cb.onAddedToGroup(groupDTO);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }).start();
            }
        }
    }

    @Override
    public List<Long> getGroupMemberIds(long conversationId) throws RemoteException {
        List<Long> ids = new ArrayList<>();
        String sql = "SELECT user_id FROM conversation_members WHERE conversation_id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, conversationId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ids.add(rs.getLong("user_id"));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return ids;
    }

    @Override
    public List<UserDTO> getMyGroups(long userId) throws RemoteException {
        List<UserDTO> list = new ArrayList<>();
        String sql = "SELECT c.id, c.name FROM conversations c " +
                "JOIN conversation_members cm ON c.id = cm.conversation_id " +
                "WHERE cm.user_id = ? AND c.is_group = TRUE";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
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
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }
}