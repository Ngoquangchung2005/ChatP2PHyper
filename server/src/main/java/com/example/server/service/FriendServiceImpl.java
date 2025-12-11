package com.example.server.service;

import com.example.common.dto.UserDTO;
import com.example.common.service.ClientCallback;
import com.example.common.service.FriendService;
import com.example.server.config.Database;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class FriendServiceImpl extends UnicastRemoteObject implements FriendService {

    public FriendServiceImpl() throws RemoteException {
        super();
    }

    // 1. LẤY DANH SÁCH BẠN BÈ
    @Override
    public List<UserDTO> getFriendList(long userId) throws RemoteException {
        List<UserDTO> list = new ArrayList<>();

        // [SỬA] Thêm u.avatar_url và u.status_msg vào câu SELECT
        String sqlFriends = "SELECT u.id, u.username, u.display_name, u.is_online, u.last_ip, u.last_port, u.avatar_url, u.status_msg " +
                "FROM users u " +
                "JOIN friendships f ON u.id = f.friend_id " +
                "WHERE f.user_id = ? AND f.status = 'ACCEPTED'";

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

                // [MỚI] Set thêm Avatar và Status
                u.setAvatarUrl(rs.getString("avatar_url"));
                u.setStatusMsg(rs.getString("status_msg"));

                list.add(u);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // [SỬA LỖI] Thêm c.avatar_url vào câu SELECT
        String sqlGroups = "SELECT c.id, c.name, c.avatar_url FROM conversations c " +
                "JOIN conversation_members cm ON c.id = cm.conversation_id " +
                "WHERE cm.user_id = ? AND c.is_group = TRUE";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlGroups)) {
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                UserDTO group = new UserDTO();
                group.setId(rs.getLong("id")); // ID của conversation
                group.setDisplayName("[Nhóm] " + rs.getString("name"));
                group.setUsername("GROUP"); // Đánh dấu là nhóm
                group.setOnline(true);
                // [THÊM DÒNG NÀY] Set Avatar cho nhóm
                group.setAvatarUrl(rs.getString("avatar_url"));
                list.add(group);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return list;
    }
    // 2. TÌM KIẾM NGƯỜI DÙNG
    @Override
    public List<UserDTO> searchUsers(String query) throws RemoteException {
        List<UserDTO> results = new ArrayList<>();
        // [SỬA] Thêm avatar_url vào SELECT
        String sql = "SELECT id, username, display_name, avatar_url, status_msg FROM users " +
                "WHERE (username LIKE ? OR display_name LIKE ?) LIMIT 20";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String likeQuery = "%" + query + "%";
            ps.setString(1, likeQuery);
            ps.setString(2, likeQuery);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                UserDTO u = new UserDTO();
                u.setId(rs.getLong("id"));
                u.setUsername(rs.getString("username"));
                u.setDisplayName(rs.getString("display_name"));

                // [MỚI]
                u.setAvatarUrl(rs.getString("avatar_url"));
                u.setStatusMsg(rs.getString("status_msg"));

                results.add(u);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results;
    }

    // 3. GỬI LỜI MỜI KẾT BẠN (PENDING)
    @Override
    public boolean sendFriendRequest(long senderId, long receiverId) throws RemoteException {
        // Kiểm tra xem đã kết bạn hoặc đã gửi lời mời chưa
        if (isFriendOrRequestSent(senderId, receiverId)) {
            return false;
        }

        String sql = "INSERT INTO friendships (user_id, friend_id, status) VALUES (?, ?, 'PENDING')";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, senderId);
            ps.setLong(2, receiverId);
            int row = ps.executeUpdate();

            if (row > 0) {
                // [REAL-TIME] Báo ngay cho người nhận (nếu họ đang online)
                ClientCallback receiverCb = AuthServiceImpl.getClientCallback(receiverId);
                if (receiverCb != null) {
                    UserDTO senderInfo = getUserInfo(senderId);
                    // Chạy luồng riêng để không block
                    new Thread(() -> {
                        try {
                            receiverCb.onNewFriendRequest(senderInfo);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }).start();
                }
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // 4. LẤY DANH SÁCH LỜI MỜI KẾT BẠN ĐANG CHỜ
    @Override
    public List<UserDTO> getPendingRequests(long userId) throws RemoteException {
        List<UserDTO> list = new ArrayList<>();
        // Lấy những người đã gửi lời mời cho mình (Mình là friend_id trong bảng friendships)
        // và trạng thái là PENDING
        String sql = "SELECT u.id, u.username, u.display_name FROM users u " +
                "JOIN friendships f ON u.id = f.user_id " +
                "WHERE f.friend_id = ? AND f.status = 'PENDING'";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                UserDTO u = new UserDTO(rs.getLong("id"), rs.getString("username"), rs.getString("display_name"));
                list.add(u);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // 5. CHẤP NHẬN LỜI MỜI KẾT BẠN
    @Override
    public boolean acceptFriendRequest(long userId, long senderId) throws RemoteException {
        // userId: Người đang bấm chấp nhận (Mình)
        // senderId: Người đã gửi lời mời (Bạn mới)

        Connection conn = null;
        try {
            conn = Database.getConnection();
            conn.setAutoCommit(false); // Dùng Transaction

            // B1: Update dòng lời mời cũ thành ACCEPTED
            // (Lưu ý: trong bảng friendships, người gửi là user_id, người nhận là friend_id)
            String sqlUpdate = "UPDATE friendships SET status = 'ACCEPTED' WHERE user_id = ? AND friend_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlUpdate)) {
                ps.setLong(1, senderId);
                ps.setLong(2, userId);
                ps.executeUpdate();
            }

            // B2: Insert dòng ngược lại để cả 2 chiều đều là bạn bè (ACCEPTED)
            String sqlInsert = "INSERT INTO friendships (user_id, friend_id, status) VALUES (?, ?, 'ACCEPTED')";
            try (PreparedStatement ps = conn.prepareStatement(sqlInsert)) {
                ps.setLong(1, userId);
                ps.setLong(2, senderId);
                ps.executeUpdate();
            }

            conn.commit();

            // [REAL-TIME] Báo cho người gửi (senderId) biết là đã được chấp nhận
            ClientCallback senderCb = AuthServiceImpl.getClientCallback(senderId);
            if (senderCb != null) {
                UserDTO acceptorInfo = getUserInfo(userId);
                acceptorInfo.setOnline(true); // Mình đang online thì mới bấm chấp nhận được
                new Thread(() -> {
                    try {
                        senderCb.onFriendRequestAccepted(acceptorInfo);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
            }
            return true;

        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) {}
            e.printStackTrace();
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException e) {}
        }
        return false;
    }

    // --- CÁC HÀM PHỤ TRỢ ---

    // Hàm lấy thông tin user đầy đủ từ DB
    private UserDTO getUserInfo(long userId) {
        String sql = "SELECT id, username, display_name, is_online, last_ip, last_port FROM users WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                UserDTO u = new UserDTO();
                u.setId(rs.getLong("id"));
                u.setUsername(rs.getString("username"));
                u.setDisplayName(rs.getString("display_name"));
                u.setOnline(rs.getBoolean("is_online"));
                u.setLastIp(rs.getString("last_ip"));
                u.setLastPort(rs.getInt("last_port"));
                return u;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // Hàm kiểm tra xem đã kết bạn chưa để tránh gửi lặp
    private boolean isFriendOrRequestSent(long user1, long user2) {
        String sql = "SELECT 1 FROM friendships WHERE user_id = ? AND friend_id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, user1);
            ps.setLong(2, user2);
            ResultSet rs = ps.executeQuery();
            return rs.next(); // Nếu có rồi thì trả về true
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

}