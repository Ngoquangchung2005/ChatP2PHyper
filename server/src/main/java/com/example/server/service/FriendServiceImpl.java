package com.example.server.service;

import com.example.common.dto.UserDTO;
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

    @Override
    public List<UserDTO> getFriendList(long userId) throws RemoteException {
        List<UserDTO> list = new ArrayList<>();

        // A. LẤY BẠN BÈ TỪ BẢNG FRIENDSHIPS
        String sqlFriends = "SELECT u.id, u.username, u.display_name, u.is_online, u.last_ip, u.last_port " +
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
                list.add(u);
            }
        } catch (SQLException e) { e.printStackTrace(); }

        // B. LẤY NHÓM CHAT
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
        } catch (SQLException e) { e.printStackTrace(); }

        return list;
    }

    @Override
    public List<UserDTO> searchUsers(String query) throws RemoteException {
        List<UserDTO> results = new ArrayList<>();
        String sql = "SELECT id, username, display_name FROM users WHERE (username LIKE ? OR display_name LIKE ?) AND id != ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String likeQuery = "%" + query + "%";
            ps.setString(1, likeQuery);
            ps.setString(2, likeQuery);
            ps.setLong(3, -1); // Tạm thời lấy hết (Client tự lọc hoặc nâng cấp sau)
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                UserDTO u = new UserDTO();
                u.setId(rs.getLong("id"));
                u.setUsername(rs.getString("username"));
                u.setDisplayName(rs.getString("display_name"));
                results.add(u);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return results;
    }

    @Override
    public boolean addFriend(long userId, long friendId) throws RemoteException {
        String sql = "INSERT INTO friendships (user_id, friend_id, status) VALUES (?, ?, 'ACCEPTED')";
        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                // Chiều A -> B
                ps.setLong(1, userId); ps.setLong(2, friendId); ps.addBatch();
                // Chiều B -> A
                ps.setLong(1, friendId); ps.setLong(2, userId); ps.addBatch();
                ps.executeBatch();
                conn.commit();
                return true;
            } catch (SQLException ex) { conn.rollback(); ex.printStackTrace(); }
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }
}