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
    private ClientCallback myCallback;

    @FXML
    public void initialize() {
        UserDTO me = SessionStore.currentUser;
        if (me != null) {
            myDisplayName.setText(me.getDisplayName());
            startP2P();
            loadFriendListInitial();
            registerRealTimeUpdates();
        }

        conversationList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) switchChat(newVal);
        });
    }

    private void registerRealTimeUpdates() {
        try {
            myCallback = new ClientCallback() {
                @Override
                public void onFriendStatusChange(UserDTO friend) throws RemoteException {
                    Platform.runLater(() -> updateFriendInList(friend));
                }
            };
            UnicastRemoteObject.exportObject(myCallback, 0);
            RmiClient.getAuthService().registerNotification(SessionStore.currentUser.getId(), myCallback);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void updateFriendInList(UserDTO updatedFriend) {
        boolean found = false;
        for (UserDTO u : conversationList.getItems()) {
            if (u.getId() == updatedFriend.getId()) {
                u.setOnline(updatedFriend.isOnline());
                u.setLastIp(updatedFriend.getLastIp());
                u.setLastPort(updatedFriend.getLastPort());
                found = true;
                break;
            }
        }
        if (!found) conversationList.getItems().add(0, updatedFriend);
        conversationList.refresh();
    }

    private void loadFriendListInitial() {
        new Thread(() -> {
            try {
                // SỬA: Dùng FriendService
                List<UserDTO> friends = RmiClient.getFriendService().getFriendList(SessionStore.currentUser.getId());
                Platform.runLater(() -> {
                    conversationList.getItems().clear();
                    conversationList.getItems().addAll(friends);
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

        new Thread(() -> {
            try {
                if ("GROUP".equals(friendOrGroup.getUsername())) {
                    activeConversationId = friendOrGroup.getId();
                } else {
                    // SỬA: Dùng MessageService để lấy ID
                    activeConversationId = RmiClient.getMessageService()
                            .getPrivateConversationId(SessionStore.currentUser.getId(), friendOrGroup.getId());
                }
                loadHistory(activeConversationId);
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void loadHistory(long conversationId) {
        try {
            // SỬA: Dùng MessageService để lấy lịch sử
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
                    // Dùng GroupService để lấy thành viên
                    List<Long> memberIds = RmiClient.getGroupService().getGroupMemberIds(activeConversationId);
                    for (Long memId : memberIds) {
                        if (memId == SessionStore.currentUser.getId()) continue;

                        // SỬA: Dùng DirectoryService để tìm IP
                        UserDTO memInfo = RmiClient.getDirectoryService().getUserInfo(memId);
                        if (memInfo != null && memInfo.isOnline()) {
                            p2pClient.send(memInfo.getLastIp(), memInfo.getLastPort(), msg);
                        }
                    }
                } else {
                    // SỬA: Dùng DirectoryService để tìm IP
                    UserDTO target = RmiClient.getDirectoryService().getUserInfo(currentChatUser.getId());
                    if (target != null && target.isOnline()) {
                        p2pClient.send(target.getLastIp(), target.getLastPort(), msg);
                    }
                }

                // SỬA: Dùng MessageService để lưu tin nhắn
                RmiClient.getMessageService().saveMessage(msg);

            } catch (Exception e) { e.printStackTrace(); }
        }).start();

        addMessageBubble(text, true);
        inputField.clear();
    }

    public void onMessageReceived(MessageDTO msg) {
        Platform.runLater(() -> {
            if (activeConversationId != -1 && msg.getConversationId() == activeConversationId) {
                addMessageBubble(msg.getContent(), false);
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