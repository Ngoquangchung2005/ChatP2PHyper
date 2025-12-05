package com.example.server;

import com.example.server.config.Database;
import com.example.server.service.*;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.sql.Connection;
import java.sql.PreparedStatement;

public class ServerApp {
    public static void main(String[] args) {
        try {
            // 1. [FIX LỖI] RESET TRẠNG THÁI ONLINE CỦA TẤT CẢ USER KHI KHỞI ĐỘNG SERVER
            resetAllUsersOffline();
            // -------------------------------------------------------------------------

            int port = 1099;
            Registry registry = LocateRegistry.createRegistry(port);

            registry.rebind("AuthService", new AuthServiceImpl());
            registry.rebind("GroupService", new GroupServiceImpl());
            registry.rebind("FriendService", new FriendServiceImpl());
            registry.rebind("MessageService", new MessageServiceImpl());
            registry.rebind("DirectoryService", new DirectoryServiceImpl());

            System.out.println("Server đang chạy tại port: " + port);

            // Giữ server chạy
            Thread.currentThread().join();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- HÀM MỚI ĐỂ RESET DB ---
    private static void resetAllUsersOffline() {
        String sql = "UPDATE users SET is_online = FALSE";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int count = ps.executeUpdate();
            System.out.println("Đã reset trạng thái Offline cho " + count + " user.");

        } catch (Exception e) {
            System.err.println("Lỗi khi reset trạng thái user: " + e.getMessage());
        }
    }
}