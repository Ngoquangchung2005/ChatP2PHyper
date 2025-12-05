package com.example.client.controller;

import com.example.client.net.P2PClient;
import com.example.client.net.RealTimeHandler; // Import mới
import com.example.client.net.RmiClient;
import com.example.client.store.SessionStore;
import com.example.common.dto.MessageDTO;
import com.example.common.dto.UserDTO;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

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
    private long activeConversationId = -1;

    // Không cần biến myCallback nữa, dùng handler
    private RealTimeHandler realTimeHandler;

    @FXML
    public void initialize() {
        conversationList.setCellFactory(param -> new FriendListCell());

        UserDTO me = SessionStore.currentUser;
        if (me != null) {
            myDisplayName.setText(me.getDisplayName());
            startP2P();
            loadFriendListInitial();
            registerRealTimeUpdates(); // Code gọn hơn nhiều
        }

        conversationList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) switchChat(newVal);
        });
    }

    // --- LOGIC GỌI REAL-TIME HANDLER ---
    private void registerRealTimeUpdates() {
        try {
            // Khởi tạo Handler và truyền chính controller này vào
            realTimeHandler = new RealTimeHandler(this);

            // Đăng ký với Server (Handler đã là UnicastRemoteObject nên không cần export lại)
            RmiClient.getAuthService().registerNotification(SessionStore.currentUser.getId(), realTimeHandler);
            System.out.println("Đã đăng ký Real-time updates.");

        } catch (Exception e) { e.printStackTrace(); }
    }

    // --- ĐỔI THÀNH PUBLIC ĐỂ HANDLER GỌI ĐƯỢC ---
    public void updateFriendInList(UserDTO updatedFriend) {
        boolean found = false;
        for (int i = 0; i < conversationList.getItems().size(); i++) {
            UserDTO u = conversationList.getItems().get(i);
            if (u.getId() == updatedFriend.getId()) {
                u.setOnline(updatedFriend.isOnline());
                u.setLastIp(updatedFriend.getLastIp());
                u.setLastPort(updatedFriend.getLastPort());
                found = true;
                conversationList.getItems().set(i, u);
                break;
            }
        }
        if (!found) conversationList.getItems().add(0, updatedFriend);
        conversationList.refresh();
    }

    public void addFriendToListDirectly(UserDTO newFriend) {
        Platform.runLater(() -> updateFriendInList(newFriend));
    }

    // --- CÁC HÀM LOGIC CỐT LÕI (LOAD, SWITCH, SEND) GIỮ NGUYÊN ---

    private void loadFriendListInitial() {
        new Thread(() -> {
            try {
                List<UserDTO> friends = RmiClient.getFriendService().getFriendList(SessionStore.currentUser.getId());
                Map<Long, Integer> unreadMap = RmiClient.getMessageService().getUnreadCounts(SessionStore.currentUser.getId());
                for (UserDTO u : friends) {
                    if (unreadMap.containsKey(u.getId())) u.setUnreadCount(unreadMap.get(u.getId()));
                }
                Platform.runLater(() -> {
                    conversationList.getItems().clear();
                    conversationList.getItems().addAll(friends);
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void reloadUnreadCounts() {
        new Thread(() -> {
            try {
                Map<Long, Integer> unreadMap = RmiClient.getMessageService().getUnreadCounts(SessionStore.currentUser.getId());
                Platform.runLater(() -> {
                    boolean changed = false;
                    for (int i = 0; i < conversationList.getItems().size(); i++) {
                        UserDTO u = conversationList.getItems().get(i);
                        int newCount = unreadMap.getOrDefault(u.getId(), 0);
                        if (u.getUnreadCount() != newCount) {
                            u.setUnreadCount(newCount);
                            conversationList.getItems().set(i, u);
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

    private void switchChat(UserDTO friendOrGroup) {
        this.currentChatUser = friendOrGroup;
        welcomeArea.setVisible(false);
        chatArea.setVisible(true);
        currentChatTitle.setText(friendOrGroup.getDisplayName());
        msgContainer.getChildren().clear();

        friendOrGroup.setUnreadCount(0);
        conversationList.refresh();

        new Thread(() -> {
            try {
                if ("GROUP".equals(friendOrGroup.getUsername())) {
                    activeConversationId = friendOrGroup.getId();
                } else {
                    activeConversationId = RmiClient.getMessageService()
                            .getPrivateConversationId(SessionStore.currentUser.getId(), friendOrGroup.getId());
                }
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
                    // SỬA: Gọi Helper thay vì viết code dài dòng
                    ChatUIHelper.addMessageBubble(msgContainer, msgScrollPane, msg.getContent(), isMe);
                }
            });
        } catch (Exception e) { e.printStackTrace(); }
    }

    // --- CÁC NÚT ĐIỀU HƯỚNG ---
    @FXML public void handleCreateGroup() { openDialog("/view/create-group.fxml", "Tạo Nhóm"); }
    @FXML public void handleAddFriend() { openDialog("/view/add-friend.fxml", "Thêm bạn bè"); }

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

    // Hàm phụ để mở dialog cho gọn
    private void openDialog(String fxml, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle(title);
            stage.setScene(new Scene(root));
            stage.showAndWait();
            loadFriendListInitial();
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
                RmiClient.getMessageService().markAsRead(SessionStore.currentUser.getId(), activeConversationId);
            } catch (Exception e) { e.printStackTrace(); }
        }).start();

        // SỬA: Gọi Helper
        ChatUIHelper.addMessageBubble(msgContainer, msgScrollPane, text, true);
        inputField.clear();
    }

    // --- NHẬN TIN NHẮN ---
    public void onMessageReceived(MessageDTO msg) {
        Platform.runLater(() -> {
            if (activeConversationId != -1 && msg.getConversationId() == activeConversationId) {
                // SỬA: Gọi Helper
                ChatUIHelper.addMessageBubble(msgContainer, msgScrollPane, msg.getContent(), false);
                new Thread(() -> {
                    try { RmiClient.getMessageService().markAsRead(SessionStore.currentUser.getId(), activeConversationId); } catch (Exception e) {}
                }).start();
            } else {
                increaseUnreadCountLocal(msg);
            }
        });
    }

    private void increaseUnreadCountLocal(MessageDTO msg) {
        for (int i = 0; i < conversationList.getItems().size(); i++) {
            UserDTO u = conversationList.getItems().get(i);
            boolean isMatch = false;
            if ("GROUP".equals(u.getUsername())) {
                if (u.getId() == msg.getConversationId()) isMatch = true;
            } else {
                if (u.getId() == msg.getSenderId()) isMatch = true;
            }

            if (isMatch) {
                int current = u.getUnreadCount();
                u.setUnreadCount(current + 1);
                conversationList.getItems().remove(i);
                conversationList.getItems().add(0, u);
                conversationList.refresh();
                break;
            }
        }
    }
}