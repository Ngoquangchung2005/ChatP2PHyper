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
    public long createGroup(String groupName, List<Long> memberIds, String avatarUrl) throws RemoteException {
        long conversationId = 0;
        Connection conn = null;
        try {
            conn = Database.getConnection();
            conn.setAutoCommit(false);

            // 1. Tạo Conversation (Đã thêm avatar_url)
            String sqlConv = "INSERT INTO conversations (name, is_group, created_at, avatar_url) VALUES (?, TRUE, NOW(), ?)";
            try (PreparedStatement ps = conn.prepareStatement(sqlConv, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, groupName);
                // Nếu avatarUrl null thì set NULL, ngược lại set String
                ps.setString(2, avatarUrl);

                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) conversationId = rs.getLong(1);
                }
            }

            // 2. Thêm thành viên (Giữ nguyên logic cũ)
            String sqlMem = "INSERT INTO conversation_members (conversation_id, user_id, is_admin) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sqlMem)) {
                for (Long userId : memberIds) {
                    ps.setLong(1, conversationId);
                    ps.setLong(2, userId);
                    // Người tạo (người đầu tiên trong list) là Admin
                    boolean isAdmin = (userId.equals(memberIds.get(0)));
                    ps.setBoolean(3, isAdmin);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();

            // 3. Thông báo Real-time (Sửa lại để truyền cả avatar mới)
            if (conversationId > 0) {
                notifyGroupMembers(conversationId, groupName, avatarUrl, memberIds);
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

    // [SỬA] Hàm notify nhận thêm avatarUrl
    private void notifyGroupMembers(long groupId, String groupName, String avatarUrl, List<Long> memberIds) {
        UserDTO groupDTO = new UserDTO();
        groupDTO.setId(groupId);
        groupDTO.setDisplayName("[Nhóm] " + groupName);
        groupDTO.setUsername("GROUP");
        groupDTO.setOnline(true);
        groupDTO.setAvatarUrl(avatarUrl); // Set avatar cho thông báo realtime

        for (Long userId : memberIds) {
            ClientCallback cb = AuthServiceImpl.getClientCallback(userId);
            if (cb != null) {
                new Thread(() -> {
                    try {
                        cb.onAddedToGroup(groupDTO);
                    } catch (RemoteException e) { e.printStackTrace(); }
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
    public boolean leaveGroup(long userId, long groupId) throws RemoteException {
        String sql = "DELETE FROM conversation_members WHERE user_id = ? AND conversation_id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, groupId);

            int rows = ps.executeUpdate();
            if (rows > 0) {
                System.out.println("User " + userId + " đã rời nhóm " + groupId);

                // (Tùy chọn) Bạn có thể thêm logic gửi tin nhắn hệ thống báo "User đã rời nhóm" vào đây
                // Bằng cách gọi MessageService.saveMessage(...)

                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
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
    // 1. LẤY DANH SÁCH THÀNH VIÊN ĐẦY ĐỦ
    @Override
    public List<UserDTO> getGroupMembers(long groupId) throws RemoteException {
        List<UserDTO> members = new ArrayList<>();
        // Đảm bảo câu lệnh SQL đúng chính tả tên bảng và cột
        String sql = "SELECT u.id, u.username, u.display_name, u.avatar_url, u.is_online, cm.is_admin " +
                "FROM users u " +
                "JOIN conversation_members cm ON u.id = cm.user_id " +
                "WHERE cm.conversation_id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, groupId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                UserDTO u = new UserDTO();
                u.setId(rs.getLong("id"));
                u.setUsername(rs.getString("username"));
                u.setDisplayName(rs.getString("display_name"));
                u.setAvatarUrl(rs.getString("avatar_url"));
                u.setOnline(rs.getBoolean("is_online"));
                // [MỚI]
                u.setAdmin(rs.getBoolean("is_admin"));
                members.add(u);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return members;
    }

    // 2. THÊM THÀNH VIÊN
    @Override
    public boolean addMemberToGroup(long groupId, long newMemberId) throws RemoteException {
        // Kiểm tra xem đã trong nhóm chưa
        if (isMember(groupId, newMemberId)) return false;

        String sql = "INSERT INTO conversation_members (conversation_id, user_id) VALUES (?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, groupId);
            ps.setLong(2, newMemberId);
            int rows = ps.executeUpdate();

            if (rows > 0) {
                // [REAL-TIME] Báo cho người vừa được thêm biết để họ hiện nhóm lên
                notifyNewMember(groupId, newMemberId);
                return true;
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    @Override
    public boolean removeMemberFromGroup(long requesterId, long groupId, long targetId) throws RemoteException {
        // [CODE MỚI] BƯỚC 1: Kiểm tra quyền Admin của người yêu cầu (requesterId)
        // Nếu không phải Admin thì không cho xóa -> trả về false
        if (!isAdmin(groupId, requesterId)) {
            System.out.println("User " + requesterId + " không phải Admin, từ chối xóa.");
            return false;
        }

        // [CODE CŨ] BƯỚC 2: Thực hiện xóa khỏi Database
        String sql = "DELETE FROM conversation_members WHERE user_id = ? AND conversation_id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, targetId);
            ps.setLong(2, groupId);

            int rows = ps.executeUpdate();
            if (rows > 0) {
                // [CODE CŨ] BƯỚC 3: Bắn tín hiệu Realtime cho người bị kick
                // Để client của họ tự động đóng cửa sổ chat
                notifyUserRemoved(groupId, targetId);
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
    // Hàm phụ trợ để gửi thông báo
    private void notifyUserRemoved(long groupId, long targetId) {
        try {
            // Lấy tên nhóm để hiển thị thông báo cho đẹp
            String groupName = "Nhóm chat";
            try (Connection conn = Database.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT name FROM conversations WHERE id = ?")) {
                ps.setLong(1, groupId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) groupName = rs.getString("name");
            }

            // Tìm Callback của người bị kick
            ClientCallback targetCb = AuthServiceImpl.getClientCallback(targetId);
            if (targetCb != null) {
                final String finalGroupName = groupName;
                new Thread(() -> {
                    try {
                        // Gọi về Client của người bị kick
                        targetCb.onRemovedFromGroup(groupId, finalGroupName);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }).start();
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // --- Helper ---
    private boolean isMember(long groupId, long userId) {
        String sql = "SELECT 1 FROM conversation_members WHERE conversation_id = ? AND user_id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, groupId);
            ps.setLong(2, userId);
            return ps.executeQuery().next();
        } catch (SQLException e) { return false; }
    }

    private void notifyNewMember(long groupId, long userId) {
        try {
            // Lấy tên nhóm
            String groupName = "Nhóm Chat";
            try (Connection conn = Database.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT name FROM conversations WHERE id = ?")) {
                ps.setLong(1, groupId);
                ResultSet rs = ps.executeQuery();
                if(rs.next()) groupName = rs.getString("name");
            }

            UserDTO groupDTO = new UserDTO();
            groupDTO.setId(groupId);
            groupDTO.setDisplayName("[Nhóm] " + groupName);
            groupDTO.setUsername("GROUP");
            groupDTO.setOnline(true);

            ClientCallback cb = AuthServiceImpl.getClientCallback(userId);
            if (cb != null) cb.onAddedToGroup(groupDTO);

        } catch (Exception e) { e.printStackTrace(); }
    }
    // --- [MỚI] CẬP NHẬT THÔNG TIN NHÓM ---
    @Override
    public boolean updateGroupInfo(long requesterId, long groupId, String newName, String avatarUrl) throws RemoteException {
        if (!isAdmin(groupId, requesterId)) return false;

        StringBuilder sql = new StringBuilder("UPDATE conversations SET ");
        List<Object> params = new ArrayList<>();

        if (newName != null && !newName.isEmpty()) {
            sql.append("name = ?, ");
            params.add(newName);
        }
        if (avatarUrl != null) {
            sql.append("avatar_url = ?, ");
            params.add(avatarUrl);
        }

        // Xóa dấu phẩy cuối
        if (params.isEmpty()) return false;
        sql.setLength(sql.length() - 2);

        sql.append(" WHERE id = ?");
        params.add(groupId);

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                if (params.get(i) instanceof String) ps.setString(i + 1, (String) params.get(i));
                else ps.setLong(i + 1, (Long) params.get(i));
            }
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    // --- [MỚI] GIẢI TÁN NHÓM ---
    @Override
    public boolean dissolveGroup(long requesterId, long groupId) throws RemoteException {
        if (!isAdmin(groupId, requesterId)) return false;

        // Xóa tất cả thành viên trước
        String sqlMem = "DELETE FROM conversation_members WHERE conversation_id = ?";
        // Xóa nhóm
        String sqlConv = "DELETE FROM conversations WHERE id = ?";

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps1 = conn.prepareStatement(sqlMem);
                 PreparedStatement ps2 = conn.prepareStatement(sqlConv)) {

                ps1.setLong(1, groupId);
                ps1.executeUpdate();

                ps2.setLong(1, groupId);
                ps2.executeUpdate();

                conn.commit();
                return true;
            } catch (SQLException e) { conn.rollback(); }
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    // Helper: Check Admin
    private boolean isAdmin(long groupId, long userId) {
        String sql = "SELECT is_admin FROM conversation_members WHERE conversation_id = ? AND user_id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, groupId);
            ps.setLong(2, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getBoolean("is_admin");
        } catch (SQLException e) {}
        return false;
    }

}