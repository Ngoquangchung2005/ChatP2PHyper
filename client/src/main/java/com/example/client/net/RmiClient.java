package com.example.client.net;

import com.example.common.service.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.io.InputStream;
import java.util.Properties;

public class RmiClient {
    private static AuthService authService;
    private static GroupService groupService;
    private static FriendService friendService;
    private static MessageService messageService;
    private static DirectoryService directoryService;

    public static void connect() {
        try {
            // 1. Đọc file client.properties
            Properties props = new Properties();
            try (InputStream in = RmiClient.class.getClassLoader().getResourceAsStream("client.properties")) {
                if (in == null) {
                    System.err.println("Không tìm thấy client.properties, dùng mặc định localhost");
                } else {
                    props.load(in);
                }
            }

            String host = props.getProperty("server.host", "localhost");
            int port = Integer.parseInt(props.getProperty("server.port", "1099"));

            System.out.println("Đang kết nối tới Server: " + host + ":" + port + " ...");

            // 2. Kết nối RMI
            Registry registry = LocateRegistry.getRegistry(host, port);

            authService = (AuthService) registry.lookup("AuthService");
            groupService = (GroupService) registry.lookup("GroupService");
            friendService = (FriendService) registry.lookup("FriendService");
            messageService = (MessageService) registry.lookup("MessageService");
            directoryService = (DirectoryService) registry.lookup("DirectoryService");

            System.out.println("Kết nối Server thành công!");
        } catch (Exception e) {
            System.err.println("Lỗi kết nối Server: " + e.getMessage());
            // e.printStackTrace(); // Có thể mở lại để debug
        }
    }

    // ... (Các hàm getter service giữ nguyên) ...
    public static AuthService getAuthService() { if (authService == null) connect(); return authService; }
    public static GroupService getGroupService() { if (groupService == null) connect(); return groupService; }
    public static FriendService getFriendService() { if (friendService == null) connect(); return friendService; }
    public static MessageService getMessageService() { if (messageService == null) connect(); return messageService; }
    public static DirectoryService getDirectoryService() { if (directoryService == null) connect(); return directoryService; }
}