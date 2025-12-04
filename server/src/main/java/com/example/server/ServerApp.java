package com.example.server;

import com.example.server.config.Database;
import com.example.server.service.AuthServiceImpl;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import com.example.server.service.ChatServiceImpl;
import com.example.server.service.GroupServiceImpl;

public class ServerApp {
    public static void main(String[] args) {
        try {
            // 1. Khởi tạo kết nối DB (Test thử)
            Database.getConnection().close();
            System.out.println("Kết nối Database thành công!");

            // 2. Tạo Registry cho RMI
            int port = Database.getRmiPort();
            Registry registry = LocateRegistry.createRegistry(port);

            // 3. Đăng ký các dịch vụ (Services)
            // Bước này mới chỉ có AuthService, bước sau ta sẽ thêm ChatService
            registry.rebind("AuthService", new AuthServiceImpl());
            registry.rebind("ChatService", new ChatServiceImpl());
            registry.rebind("GroupService", new GroupServiceImpl());

            System.out.println("Server đang chạy tại port: " + port);

            // Giữ server chạy
            Thread.currentThread().join();

        } catch (Exception e) {
            System.err.println("Không thể khởi động Server:");
            e.printStackTrace();
        }
    }
}