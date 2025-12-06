package com.example.server.service;

import com.example.common.dto.UserDTO;
import com.example.common.service.DirectoryService;
import com.example.server.config.Database;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DirectoryServiceImpl extends UnicastRemoteObject implements DirectoryService {

    public DirectoryServiceImpl() throws RemoteException {
        super();
    }

    @Override
    public UserDTO getUserInfo(long userId) throws RemoteException {
        // [SỬA] Thêm avatar_url, status_msg
        String sql = "SELECT id, username, display_name, last_ip, last_port, is_online, avatar_url, status_msg FROM users WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                UserDTO u = new UserDTO();
                u.setId(rs.getLong("id"));
                u.setUsername(rs.getString("username")); // Thêm dòng này nếu thiếu
                u.setDisplayName(rs.getString("display_name")); // Thêm dòng này
                u.setLastIp(rs.getString("last_ip"));
                u.setLastPort(rs.getInt("last_port"));
                u.setOnline(rs.getBoolean("is_online"));

                // [MỚI]
                u.setAvatarUrl(rs.getString("avatar_url"));
                u.setStatusMsg(rs.getString("status_msg"));

                return u;
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }
}