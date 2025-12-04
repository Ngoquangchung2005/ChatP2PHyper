package com.example.client.net;

import com.example.common.service.AuthService;
import com.example.common.service.ChatService;
import com.example.common.service.GroupService;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class RmiClient {
    private static AuthService authService;
    private static ChatService chatService;
    private static GroupService groupService;

    // Cấu hình IP Server (Sau này có thể đưa ra file config)
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 1099;

    public static void connect() {
        try {
            // Tìm Server tại địa chỉ và cổng đã định
            Registry registry = LocateRegistry.getRegistry(SERVER_HOST, SERVER_PORT);

            // Lấy các dịch vụ về dùng
            authService = (AuthService) registry.lookup("AuthService");
            // chatService = (ChatService) registry.lookup("ChatService"); // Sẽ mở ở bước sau
            chatService = (ChatService) registry.lookup("ChatService");
            groupService = (GroupService) registry.lookup("GroupService");

            System.out.println("Đã kết nối tới Server RMI!");
        } catch (Exception e) {
            System.err.println("Lỗi kết nối Server: " + e.getMessage());
            e.printStackTrace();
        }

    }
    // Getter mới
    public static GroupService getGroupService() {
        if (groupService == null) connect();
        return groupService;
    }

    public static AuthService getAuthService() {
        if (authService == null) connect();
        return authService;
    }
    public static ChatService getChatService() {
        if (chatService == null) connect();
        return chatService;
    }
}