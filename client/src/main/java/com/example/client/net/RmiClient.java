package com.example.client.net;

import com.example.common.service.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class RmiClient {
    private static AuthService authService;
    private static GroupService groupService;

    // 3 Service mới tách ra
    private static FriendService friendService;
    private static MessageService messageService;
    private static DirectoryService directoryService;

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 1099;

    public static void connect() {
        try {
            // Tìm Server tại địa chỉ và cổng đã định
            Registry registry = LocateRegistry.getRegistry(SERVER_HOST, SERVER_PORT);

            // Lấy các dịch vụ về dùng
            authService = (AuthService) registry.lookup("AuthService");
            groupService = (GroupService) registry.lookup("GroupService");

            // Lookup 3 service mới
            friendService = (FriendService) registry.lookup("FriendService");
            messageService = (MessageService) registry.lookup("MessageService");
            directoryService = (DirectoryService) registry.lookup("DirectoryService");

            System.out.println("Đã kết nối tới Server RMI!");
        } catch (Exception e) {
            // In lỗi ra màn hình nếu kết nối thất bại
            System.err.println("Lỗi kết nối Server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Các hàm Getter để lấy service (Nếu chưa có thì tự connect)
    public static AuthService getAuthService() {
        if (authService == null) connect();
        return authService;
    }

    public static GroupService getGroupService() {
        if (groupService == null) connect();
        return groupService;
    }

    public static FriendService getFriendService() {
        if (friendService == null) connect();
        return friendService;
    }

    public static MessageService getMessageService() {
        if (messageService == null) connect();
        return messageService;
    }

    public static DirectoryService getDirectoryService() {
        if (directoryService == null) connect();
        return directoryService;
    }
}