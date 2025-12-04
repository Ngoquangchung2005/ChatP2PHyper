package com.example.server;

import com.example.server.config.Database;
import com.example.server.service.*;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class ServerApp {
    public static void main(String[] args) {
        try {
            // 1. Khởi tạo kết nối DB (Test thử)
            Database.getConnection().close();
            System.out.println("Kết nối Database thành công!");

            // 2. Tạo Registry cho RMI
            int port = Database.getRmiPort();
            Registry registry = LocateRegistry.createRegistry(port);

            // Đăng ký các dịch vụ đã tách nhỏ
            registry.rebind("AuthService", new AuthServiceImpl());
            registry.rebind("GroupService", new GroupServiceImpl());

            // 3 SERVICE MỚI
            registry.rebind("FriendService", new FriendServiceImpl());
            registry.rebind("MessageService", new MessageServiceImpl());
            registry.rebind("DirectoryService", new DirectoryServiceImpl());

            System.out.println("Server đang chạy tại port: " + port);

            // Giữ server chạy
            Thread.currentThread().join();

        } catch (Exception e) {
            System.err.println("Không thể khởi động Server:");
            e.printStackTrace();
        }
    }
}