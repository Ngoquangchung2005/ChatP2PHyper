package com.example.client.controller;

import com.example.client.net.P2PClient;
import com.example.client.net.RmiClient;
import com.example.client.store.SessionStore;
import com.example.common.dto.MessageDTO;
import com.example.common.dto.UserDTO;
import com.example.common.service.ClientCallback;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class MainController {

    @FXML private Label myDisplayName;
    @FXML private ListView<UserDTO> conversationList;
    @FXML private VBox chatArea, welcomeArea, msgContainer;
    @FXML private Label currentChatTitle;
    @FXML private TextField inputField;
    @FXML private ScrollPane msgScrollPane;

    private P2PClient p2pClient;
    private UserDTO currentChatUser;

    // Lưu ID hội thoại đang mở để kiểm tra tin nhắn đến
    private long activeConversationId = -1;

    // Giữ tham chiếu Callback để không bị Garbage Collector quét mất
    private ClientCallback myCallback;

    @FXML
    public void initialize() {
        // --- QUAN TRỌNG: Kích hoạt giao diện Custom Cell (để hiện chấm đỏ/xanh và số tin nhắn) ---
        // Class FriendListCell phải được tạo như hướng dẫn trước đó
        conversationList.setCellFactory(param -> new FriendListCell());
        // ----------------------------------------------------------------------------------------

        UserDTO me = SessionStore.currentUser;
        if (me != null) {
            myDisplayName.setText(me.getDisplayName());

            // 1. Khởi động P2P Server để nhận tin nhắn
            startP2P();

            // 2. Tải danh sách bạn bè & Số tin chưa đọc từ Server
            loadFriendListInitial();

            // 3. Đăng ký nhận thông báo Real-time (Online/Offline/Kết bạn)
            registerRealTimeUpdates();
        }

        // Sự kiện khi chọn vào một dòng chat
        conversationList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) switchChat(newVal);
        });
    }

    // --- CƠ CHẾ REAL-TIME (SERVER GỌI VỀ CLIENT) ---
    private void registerRealTimeUpdates() {
        try {
            myCallback = new ClientCallback() {
                // 1. Bạn bè Online/Offline
                @Override
                public void onFriendStatusChange(UserDTO friend) throws RemoteException {
                    // Cập nhật trạng thái hiển thị
                    Platform.runLater(() -> updateFriendInList(friend));
                }

                // 2. Có lời mời kết bạn mới
                @Override
                public void onNewFriendRequest(UserDTO sender) throws RemoteException {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Thông báo");
                        alert.setHeaderText("Lời mời kết bạn mới!");
                        alert.setContentText(sender.getDisplayName() + " muốn kết bạn.");
                        alert.show();
                    });
                }

                // 3. Lời mời được chấp nhận
                @Override
                public void onFriendRequestAccepted(UserDTO newFriend) throws RemoteException {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Tin vui");
                        alert.setHeaderText(null);
                        alert.setContentText(newFriend.getDisplayName() + " đã chấp nhận lời mời!");
                        alert.show();

                        newFriend.setOnline(true);
                        addFriendToListDirectly(newFriend);
                    });
                }

                // 4. Được thêm vào nhóm chat
                @Override
                public void onAddedToGroup(UserDTO newGroup) throws RemoteException {
                    Platform.runLater(() -> updateFriendInList(newGroup));
                }
            };

            // Export object RMI
            UnicastRemoteObject.exportObject(myCallback, 0);

            // Đăng ký với Server
            RmiClient.getAuthService().registerNotification(SessionStore.currentUser.getId(), myCallback);
            System.out.println("Đã đăng ký Real-time updates.");

        } catch (Exception e) { e.printStackTrace(); }
    }

    // Hàm cập nhật danh sách thông minh
    private void updateFriendInList(UserDTO updatedFriend) {
        boolean found = false;
        for (int i = 0; i < conversationList.getItems().size(); i++) {
            UserDTO u = conversationList.getItems().get(i);
            if (u.getId() == updatedFriend.getId()) {
                // Cập nhật trạng thái Online/IP/Port
                u.setOnline(updatedFriend.isOnline());
                u.setLastIp(updatedFriend.getLastIp());
                u.setLastPort(updatedFriend.getLastPort());

                // LƯU Ý: Không ghi đè unreadCount ở đây vì updatedFriend từ Server về
                // thường không chứa count. Count được quản lý bởi logic khác.

                found = true;

                // [QUAN TRỌNG]: Ép ListView vẽ lại dòng này (để đổi màu chấm xanh/đỏ)
                conversationList.getItems().set(i, u);
                break;
            }
        }

        // Nếu chưa có (bạn mới/nhóm mới) thì thêm vào đầu
        if (!found) conversationList.getItems().add(0, updatedFriend);

        // Refresh toàn bộ để vẽ lại
        conversationList.refresh();
    }

    // Hàm public cho Controller khác gọi (VD: FriendRequestController)
    public void addFriendToListDirectly(UserDTO newFriend) {
        Platform.runLater(() -> updateFriendInList(newFriend));
    }

    // --- LOGIC TẢI DỮ LIỆU BAN ĐẦU ---
    private void loadFriendListInitial() {
        new Thread(() -> {
            try {
                // 1. Lấy danh sách bạn bè/nhóm từ FriendService
                List<UserDTO> friends = RmiClient.getFriendService().getFriendList(SessionStore.currentUser.getId());

                // 2. Lấy số lượng tin nhắn chưa đọc từ MessageService
                Map<Long, Integer> unreadMap = RmiClient.getMessageService().getUnreadCounts(SessionStore.currentUser.getId());

                // 3. Gán số lượng vào từng UserDTO
                for (UserDTO u : friends) {
                    if (unreadMap.containsKey(u.getId())) {
                        u.setUnreadCount(unreadMap.get(u.getId()));
                    }
                }

                Platform.runLater(() -> {
                    conversationList.getItems().clear();
                    conversationList.getItems().addAll(friends);
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    // --- LOGIC TẢI LẠI SỐ TIN CHƯA ĐỌC ---
    private void reloadUnreadCounts() {
        new Thread(() -> {
            try {
                // Gọi Server lấy danh sách đếm mới nhất
                Map<Long, Integer> unreadMap = RmiClient.getMessageService().getUnreadCounts(SessionStore.currentUser.getId());

                Platform.runLater(() -> {
                    boolean changed = false;
                    for (int i = 0; i < conversationList.getItems().size(); i++) {
                        UserDTO u = conversationList.getItems().get(i);
                        int newCount = 0;
                        if (unreadMap.containsKey(u.getId())) {
                            newCount = unreadMap.get(u.getId());
                        }

                        // Nếu số lượng thay đổi thì cập nhật và vẽ lại
                        if (u.getUnreadCount() != newCount) {
                            u.setUnreadCount(newCount);
                            conversationList.getItems().set(i, u); // Kích hoạt vẽ lại cell
                            changed = true;
                        }
                    }
                    if (changed) conversationList.refresh();
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void startP2P() {
        p2pClient = new P2PClient(SessionStore.p2pPort, this::onMessageReceived);
        p2pClient.start();
    }

    // --- LOGIC CHUYỂN ĐỔI CHAT & MARK READ ---
    private void switchChat(UserDTO friendOrGroup) {
        this.currentChatUser = friendOrGroup;
        welcomeArea.setVisible(false);
        chatArea.setVisible(true);
        currentChatTitle.setText(friendOrGroup.getDisplayName());
        msgContainer.getChildren().clear();

        // Xóa số tin chưa đọc ngay lập tức trên UI
        friendOrGroup.setUnreadCount(0);
        conversationList.refresh(); // Vẽ lại để mất số đỏ

        new Thread(() -> {
            try {
                if ("GROUP".equals(friendOrGroup.getUsername())) {
                    activeConversationId = friendOrGroup.getId();
                } else {
                    activeConversationId = RmiClient.getMessageService()
                            .getPrivateConversationId(SessionStore.currentUser.getId(), friendOrGroup.getId());
                }

                // Gọi Server đánh dấu đã đọc
                RmiClient.getMessageService().markAsRead(SessionStore.currentUser.getId(), activeConversationId);

                loadHistory(activeConversationId);

            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void loadHistory(long conversationId) {
        try {
            List<MessageDTO> history = RmiClient.getMessageService().getHistory(conversationId);
            Platform.runLater(() -> {
                msgContainer.getChildren().clear();
                for (MessageDTO msg : history) {
                    boolean isMe = msg.getSenderId() == SessionStore.currentUser.getId();
                    addMessageBubble(msg.getContent(), isMe);
                }
                msgScrollPane.setVvalue(1.0);
            });
        } catch (Exception e) { e.printStackTrace(); }
    }

    // --- CÁC NÚT ĐIỀU HƯỚNG ---
    @FXML
    public void handleCreateGroup() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/create-group.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("Tạo Nhóm");
            stage.setScene(new Scene(root));
            stage.showAndWait();
            loadFriendListInitial();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML
    public void handleAddFriend() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/add-friend.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("Thêm bạn bè");
            stage.setScene(new Scene(root));
            stage.showAndWait();
            loadFriendListInitial();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML
    public void handleShowRequests() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/friend-requests.fxml"));
            Parent root = loader.load();
            FriendRequestController ctrl = loader.getController();
            ctrl.setMainController(this);
            Stage stage = new Stage();
            stage.setTitle("Lời mời kết bạn");
            stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) { e.printStackTrace(); }
    }

    // --- GỬI TIN NHẮN ---
    @FXML
    public void handleSend() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || currentChatUser == null || activeConversationId == -1) return;

        MessageDTO msg = new MessageDTO();
        msg.setSenderId(SessionStore.currentUser.getId());
        msg.setContent(text);
        msg.setCreatedAt(LocalDateTime.now());
        msg.setSenderName(SessionStore.currentUser.getDisplayName());
        msg.setConversationId(activeConversationId);

        boolean isGroup = "GROUP".equals(currentChatUser.getUsername());

        new Thread(() -> {
            try {
                if (isGroup) {
                    List<Long> memberIds = RmiClient.getGroupService().getGroupMemberIds(activeConversationId);
                    for (Long memId : memberIds) {
                        if (memId == SessionStore.currentUser.getId()) continue;
                        UserDTO memInfo = RmiClient.getDirectoryService().getUserInfo(memId);
                        if (memInfo != null && memInfo.isOnline()) {
                            p2pClient.send(memInfo.getLastIp(), memInfo.getLastPort(), msg);
                        }
                    }
                } else {
                    UserDTO target = RmiClient.getDirectoryService().getUserInfo(currentChatUser.getId());
                    if (target != null && target.isOnline()) {
                        p2pClient.send(target.getLastIp(), target.getLastPort(), msg);
                    }
                }
                RmiClient.getMessageService().saveMessage(msg);

                // Mình gửi thì coi như mình đã đọc -> Mark read để server không tính là unread
                RmiClient.getMessageService().markAsRead(SessionStore.currentUser.getId(), activeConversationId);

            } catch (Exception e) { e.printStackTrace(); }
        }).start();

        addMessageBubble(text, true);
        inputField.clear();
    }

    // --- NHẬN TIN NHẮN ---
    public void onMessageReceived(MessageDTO msg) {
        Platform.runLater(() -> {
            // Trường hợp 1: Đang mở đúng cửa sổ chat đó -> Hiện tin luôn
            if (activeConversationId != -1 && msg.getConversationId() == activeConversationId) {
                addMessageBubble(msg.getContent(), false);

                // Gọi Server đánh dấu đã đọc ngay vì người dùng đang nhìn thấy
                new Thread(() -> {
                    try {
                        RmiClient.getMessageService().markAsRead(SessionStore.currentUser.getId(), activeConversationId);
                    } catch (Exception e) {}
                }).start();

            } else {
                // Trường hợp 2: Đang chat với người khác hoặc đang thu nhỏ
                // -> Cần tăng số Badge đỏ lên
                reloadUnreadCounts();
            }
        });
    }

    private void addMessageBubble(String text, boolean isMe) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(350);
        label.setStyle(isMe
                ? "-fx-background-color: #0084ff; -fx-text-fill: white; -fx-background-radius: 15; -fx-padding: 8 12;"
                : "-fx-background-color: #f0f0f0; -fx-text-fill: black; -fx-background-radius: 15; -fx-padding: 8 12;");

        HBox container = new HBox(label);
        container.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        msgContainer.getChildren().add(container);
        Platform.runLater(() -> msgScrollPane.setVvalue(1.0));
    }
}