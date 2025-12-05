package com.example.server.service;

import com.example.common.dto.UserDTO;
import com.example.common.service.AuthService;
import com.example.common.service.ClientCallback;
import com.example.server.config.Database;
import com.example.server.util.PasswordHasher;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AuthServiceImpl extends UnicastRemoteObject implements AuthService {

    // Danh sách lưu "cái loa" (Callback) của các user đang online
    private static final Map<Long, ClientCallback> onlineClients = new ConcurrentHashMap<>();

    public AuthServiceImpl() throws RemoteException {
        super();
    }

    @Override
    public UserDTO login(String username, String password, String clientIp, int p2pPort) throws RemoteException {
        String sql = "SELECT * FROM users WHERE username = ?";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String dbHash = rs.getString("password_hash");

                // Kiểm tra mật khẩu
                if (PasswordHasher.check(password, dbHash)) {
                    long userId = rs.getLong("id");
                    String displayName = rs.getString("display_name");

                    // Cập nhật trạng thái vào DB
                    updateUserStatus(userId, true, clientIp, p2pPort);

                    // Trả về thông tin User cho Client
                    UserDTO user = new UserDTO(userId, username, displayName);
                    user.setOnline(true);
                    user.setLastIp(clientIp);
                    user.setLastPort(p2pPort);
                    return user;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RemoteException("Lỗi Database: " + e.getMessage());
        }
        return null;
    }

    @Override
    public void registerNotification(long userId, ClientCallback callback) throws RemoteException {
        onlineClients.put(userId, callback);
        System.out.println("User ID " + userId + " đã online và đăng ký nhận thông báo.");

        UserDTO me = getUserInfoFromDB(userId);
        if (me != null) {
            notifyFriendsOnly(me); // <--- SỬA: Chỉ báo cho bạn bè
        }
    }

    @Override
    public void logout(long userId) throws RemoteException {
        onlineClients.remove(userId);
        updateUserStatus(userId, false, null, 0);

        UserDTO meOffline = new UserDTO();
        meOffline.setId(userId);
        meOffline.setOnline(false);

        notifyFriendsOnly(meOffline); // <--- SỬA: Chỉ báo cho bạn bè

        System.out.println("User ID " + userId + " đã đăng xuất.");
    }

    // --- [HÀM MỚI] CHỈ GỬI THÔNG BÁO CHO BẠN BÈ ---
    private void notifyFriendsOnly(UserDTO changeUser) {
        // 1. Lấy danh sách ID bạn bè của người này từ Database
        List<Long> friendIds = getFriendIds(changeUser.getId());

        // 2. Chỉ gửi thông báo cho những bạn bè ĐANG ONLINE
        for (Long friendId : friendIds) {
            ClientCallback clientCb = onlineClients.get(friendId);
            if (clientCb != null) {
                new Thread(() -> {
                    try {
                        clientCb.onFriendStatusChange(changeUser);
                    } catch (RemoteException e) {
                        onlineClients.remove(friendId);
                    }
                }).start();
            }
        }
    }

    // [HÀM MỚI] Truy vấn DB lấy danh sách ID bạn bè
    private List<Long> getFriendIds(long userId) {
        List<Long> list = new ArrayList<>();
        // Chỉ lấy những người đã là bạn bè (ACCEPTED)
        String sql = "SELECT friend_id FROM friendships WHERE user_id = ? AND status = 'ACCEPTED'";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(rs.getLong("friend_id"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // ... (Các hàm hỗ trợ cũ giữ nguyên) ...

    private void updateUserStatus(long userId, boolean online, String ip, int port) {
        String sql = "UPDATE users SET is_online = ?, last_ip = ?, last_port = ? WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, online);
            ps.setString(2, ip);
            ps.setInt(3, port);
            ps.setLong(4, userId);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private UserDTO getUserInfoFromDB(long userId) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                UserDTO u = new UserDTO(rs.getLong("id"), rs.getString("username"), rs.getString("display_name"));
                u.setOnline(rs.getBoolean("is_online"));
                u.setLastIp(rs.getString("last_ip"));
                u.setLastPort(rs.getInt("last_port"));
                return u;
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    @Override
    public boolean register(String u, String p, String d, String e) throws RemoteException {
        // (Giữ nguyên code register của bạn)
        String sql = "INSERT INTO users (username, password_hash, display_name, email) VALUES (?, ?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, u);
            ps.setString(2, PasswordHasher.hash(p));
            ps.setString(3, d);
            ps.setString(4, e);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) { return false; }
    }

    @Override
    public boolean resetPassword(String u, String e, String n) throws RemoteException { return false; }

    public static ClientCallback getClientCallback(long userId) {
        return onlineClients.get(userId);
    }
}