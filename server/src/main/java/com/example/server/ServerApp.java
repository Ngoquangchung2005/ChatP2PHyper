package com.example.server;

import com.example.server.config.Database;
import com.example.server.service.*;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.io.InputStream;
import java.util.Properties;

public class ServerApp {
    public static void main(String[] args) {
        try {
            // 1. Đọc cấu hình từ server.properties
            Properties props = new Properties();
            try (InputStream in = ServerApp.class.getClassLoader().getResourceAsStream("server.properties")) {
                if (in != null) props.load(in);
            }

            // Lấy IP từ file cấu hình (nếu không có thì mặc định localhost)
            String serverHostname = props.getProperty("server.hostname", "localhost");
            String serverPortStr = props.getProperty("server.rmi.port", "1099");
            int port = Integer.parseInt(serverPortStr);

            // 2. Cấu hình RMI Hostname (QUAN TRỌNG: Để Client từ máy khác gọi được)
            System.setProperty("java.rmi.server.hostname", serverHostname);

            // 3. Reset trạng thái user (như code cũ của bạn)
            resetAllUsersOffline();

            // 4. Khởi tạo Registry
            Registry registry = LocateRegistry.createRegistry(port);

            registry.rebind("AuthService", new AuthServiceImpl());
            registry.rebind("GroupService", new GroupServiceImpl());
            registry.rebind("FriendService", new FriendServiceImpl());
            registry.rebind("MessageService", new MessageServiceImpl());
            registry.rebind("DirectoryService", new DirectoryServiceImpl());

            System.out.println("=== SERVER ĐANG CHẠY ===");
            System.out.println("Host: " + serverHostname);
            System.out.println("Port: " + port);

            // Giữ server chạy
            Thread.currentThread().join();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void resetAllUsersOffline() {
        // (Giữ nguyên code reset DB của bạn)
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE users SET is_online = FALSE")) {
            ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
    }
}