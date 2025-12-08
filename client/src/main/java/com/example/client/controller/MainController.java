package com.example.client.controller;

import com.example.client.net.P2PClient;
import com.example.client.net.RealTimeHandler;
import com.example.client.net.RmiClient;
import com.example.client.store.SessionStore;
import com.example.common.dto.MessageDTO;
import com.example.common.dto.UserDTO;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

public class MainController {

    // --- FXML FIELDS (Để public hoặc có getter để các Manager truy cập) ---
    @FXML public BorderPane mainBorderPane;
    @FXML public Label myDisplayName;
    @FXML public ImageView myAvatarView;
    @FXML public ListView<UserDTO> conversationList;
    @FXML public VBox chatArea, welcomeArea, msgContainer;
    @FXML public Label currentChatTitle;
    @FXML public TextField inputField;
    @FXML public ScrollPane msgScrollPane;
    @FXML public Button micBtn;

    // --- DATA FIELDS ---
    public P2PClient p2pClient;
    public UserDTO currentChatUser;
    public long activeConversationId = -1;
    public boolean isUpdatingList = false;
    public final Map<String, VBox> messageUiMap = new HashMap<>();

    // --- MANAGERS ---
    private ChatManager chatManager;
    private ContactManager contactManager;
    private CallHandler callHandler;
    private NavigationHandler navigationHandler;
    private RealTimeHandler realTimeHandler;

    @FXML
    public void initialize() {
        // 1. Khởi tạo các Manager
        this.contactManager = new ContactManager(this);
        this.chatManager = new ChatManager(this);
        this.callHandler = new CallHandler(this);
        this.navigationHandler = new NavigationHandler(this);

        // 2. Setup UI cơ bản
        conversationList.setCellFactory(param -> new FriendListCell());
        ChatUIHelper.setMainController(this);

        // 3. Load thông tin User
        UserDTO me = SessionStore.currentUser;
        if (me != null) {
            myDisplayName.setText(me.getDisplayName());
            loadMyAvatar(me.getAvatarUrl());
            startP2P();
            contactManager.loadFriendListInitial();
            registerRealTimeUpdates();
        }

        // 4. Sự kiện click vào List
        conversationList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (!isUpdatingList && newVal != null) {
                chatManager.switchChat(newVal);
            }
        });
    }

    // --- P2P & NETWORK SETUP ---
    private void startP2P() {
        p2pClient = new P2PClient(SessionStore.p2pPort, this::onMessageReceived);
        p2pClient.start();
    }

    private void registerRealTimeUpdates() {
        try {
            realTimeHandler = new RealTimeHandler(this);
            RmiClient.getAuthService().registerNotification(SessionStore.currentUser.getId(), realTimeHandler);
        } catch (Exception e) { e.printStackTrace(); }
    }

    // --- HÀM NHẬN TIN NHẮN (DISPATCHER) ---
    public void onMessageReceived(MessageDTO msg) {
        Platform.runLater(() -> {
            // Điều hướng xử lý dựa trên loại tin nhắn
            if (msg.getType() == MessageDTO.MessageType.CALL_REQ ||
                    msg.getType() == MessageDTO.MessageType.CALL_ACCEPT ||
                    msg.getType() == MessageDTO.MessageType.CALL_DENY ||
                    msg.getType() == MessageDTO.MessageType.CALL_END) {

                callHandler.handleCallSignal(msg);

            } else {
                // Tin nhắn thường, Edit, Recall
                chatManager.handleIncomingMessage(msg);
            }
        });
    }

    // --- DELEGATE METHODS (Chuyển tiếp sự kiện từ FXML sang Manager) ---

    @FXML public void handleSend() { chatManager.handleSend(); }
    @FXML public void handleSendFile() { chatManager.handleSendFile(); }
    @FXML public void startRecording(MouseEvent event) { chatManager.startRecording(event); }
    @FXML public void stopAndSendAudio(MouseEvent event) { chatManager.stopAndSendAudio(event); }

    @FXML public void handleVoiceCall() { callHandler.handleVoiceCall(); }

    @FXML public void handleCreateGroup() { navigationHandler.handleCreateGroup(); }
    @FXML public void handleAddFriend() { navigationHandler.handleAddFriend(); }
    @FXML public void handleShowRequests() { navigationHandler.handleShowRequests(); }
    @FXML public void handleOpenProfile() { navigationHandler.handleOpenProfile(); }
    @FXML public void handleToggleInfo() { navigationHandler.handleToggleInfo(); }

    // --- HELPER METHODS CHO CÁC MANAGER GỌI LẠI ---

    // Các hàm này được Manager gọi để thao tác chéo
    public void handleEditAction(MessageDTO msg) { chatManager.handleEditAction(msg); }
    public void handleRecallAction(MessageDTO msg) { chatManager.handleRecallAction(msg); }

    public void loadMyAvatar(String url) {
        if (url == null || url.isEmpty()) return;
        new Thread(() -> {
            try {
                byte[] data = RmiClient.getMessageService().downloadFile(url);
                if (data != null) {
                    Image img = new Image(new ByteArrayInputStream(data));
                    Platform.runLater(() -> {
                        myAvatarView.setImage(img);
                        double r = myAvatarView.getFitWidth() / 2;
                        Circle clip = new Circle(r, r, r);
                        myAvatarView.setClip(clip);
                    });
                }
            } catch (Exception e) {}
        }).start();
    }

    public void refreshProfileUI() {
        myDisplayName.setText(SessionStore.currentUser.getDisplayName());
        loadMyAvatar(SessionStore.currentUser.getAvatarUrl());
    }

    // Getters cho Manager
    public ContactManager getContactManager() { return contactManager; }
    public ChatManager getChatManager() { return chatManager; }
    public CallHandler getCallHandler() { return callHandler; }
    public NavigationHandler getNavigationHandler() { return navigationHandler; }

    // Hàm updateFriendInList được gọi từ RealTimeHandler
    public void updateFriendInList(UserDTO friend) { contactManager.updateFriendInList(friend); }
    public void addFriendToListDirectly(UserDTO friend) { contactManager.addFriendToListDirectly(friend); }
    public void handleEndCallSignal() { callHandler.handleEndCallSignal(); }
}