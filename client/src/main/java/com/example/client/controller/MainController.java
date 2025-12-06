package com.example.client.controller;

import com.example.client.net.P2PClient;
import com.example.client.net.RealTimeHandler;
import com.example.client.net.RmiClient;
import com.example.client.net.VoiceCallManager;
import com.example.client.store.SessionStore;
import com.example.client.util.AudioHelper;
import com.example.common.dto.MessageDTO;
import com.example.common.dto.UserDTO;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.layout.BorderPane; // Nhớ import cái này
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class MainController {

    @FXML private Label myDisplayName;
    @FXML private ImageView myAvatarView;
    @FXML private ListView<UserDTO> conversationList;
    @FXML private VBox chatArea, welcomeArea, msgContainer;
    @FXML private Label currentChatTitle;
    @FXML private TextField inputField;
    @FXML private ScrollPane msgScrollPane;
    @FXML private Button micBtn;
    // [MỚI] Thêm fx:id cho BorderPane tổng (xem bước 5 để thêm vào FXML)
    @FXML private BorderPane mainBorderPane;

    private final AudioHelper audioRecorder = new AudioHelper();
    private long recordingStartTime;
    private P2PClient p2pClient;
    private UserDTO currentChatUser;
    private long activeConversationId = -1;
    private RealTimeHandler realTimeHandler;
    private final VoiceCallManager voiceCallManager = new VoiceCallManager();
    private VideoCallController currentVideoCallController;
    private boolean isInfoSidebarOpen = false; // Trạng thái sidebar

    @FXML
    public void initialize() {
        conversationList.setCellFactory(param -> new FriendListCell());

        UserDTO me = SessionStore.currentUser;
        if (me != null) {
            myDisplayName.setText(me.getDisplayName());

            // Load Avatar của tôi
            loadMyAvatar(me.getAvatarUrl());

            startP2P();
            loadFriendListInitial();
            registerRealTimeUpdates();
        }

        conversationList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) switchChat(newVal);
        });
    }

    // [HÀM NÀY ĐÃ ĐƯỢC CẬP NHẬT ĐỂ SỬA LỖI AVATAR]
    public void loadMyAvatar(String url) {
        if (url == null || url.isEmpty()) return;
        new Thread(() -> {
            try {
                byte[] data = RmiClient.getMessageService().downloadFile(url);
                if (data != null) {
                    Image img = new Image(new ByteArrayInputStream(data));
                    Platform.runLater(() -> {
                        myAvatarView.setImage(img);

                        // Cắt ảnh hình tròn ngay khi set ảnh
                        double r = myAvatarView.getFitWidth() / 2;
                        Circle clip = new Circle(r, r, r);
                        myAvatarView.setClip(clip);
                    });
                }
            } catch (Exception e) {}
        }).start();
    }

    // ... (Toàn bộ phần còn lại của file giữ nguyên như cũ) ...
    // Hãy copy lại các hàm khác từ file MainController cũ của bạn vào đây nếu cần,
    // hoặc chỉ thay thế hàm loadMyAvatar và initialize ở trên.

    private void registerRealTimeUpdates() {
        try {
            realTimeHandler = new RealTimeHandler(this);
            RmiClient.getAuthService().registerNotification(SessionStore.currentUser.getId(), realTimeHandler);
            System.out.println("Đã đăng ký Real-time updates.");
        } catch (Exception e) {
            System.err.println("Lỗi đăng ký Real-time: " + e.getMessage());
        }
    }

    public void updateFriendInList(UserDTO updatedFriend) {
        boolean found = false;
        for (int i = 0; i < conversationList.getItems().size(); i++) {
            UserDTO u = conversationList.getItems().get(i);
            if (u.getId() == updatedFriend.getId()) {
                u.setOnline(updatedFriend.isOnline());
                u.setLastIp(updatedFriend.getLastIp());
                u.setLastPort(updatedFriend.getLastPort());
                if (updatedFriend.getAvatarUrl() != null) u.setAvatarUrl(updatedFriend.getAvatarUrl());
                if (updatedFriend.getStatusMsg() != null) u.setStatusMsg(updatedFriend.getStatusMsg());

                found = true;
                conversationList.getItems().set(i, u);

                if (currentChatUser != null && currentChatUser.getId() == updatedFriend.getId()) {
                    currentChatUser = u;
                }
                break;
            }
        }
        if (!found) conversationList.getItems().add(0, updatedFriend);
        conversationList.refresh();
    }

    public void addFriendToListDirectly(UserDTO newFriend) {
        Platform.runLater(() -> updateFriendInList(newFriend));
    }

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

    private void startP2P() {
        p2pClient = new P2PClient(SessionStore.p2pPort, this::onMessageReceived);
        p2pClient.start();
    }

    private void switchChat(UserDTO friendOrGroup) {
        // 1. Cập nhật biến user hiện tại
        this.currentChatUser = friendOrGroup;

        // 2. Cập nhật giao diện cơ bản
        welcomeArea.setVisible(false);
        chatArea.setVisible(true);
        currentChatTitle.setText(friendOrGroup.getDisplayName());
        msgContainer.getChildren().clear();

        friendOrGroup.setUnreadCount(0);
        conversationList.refresh();

        // [CHÈN VÀO ĐÂY] ----------------------------------------------------
        // Nếu sidebar thông tin đang mở, ta cần load lại thông tin của người mới
        if (isInfoSidebarOpen) {
            openInfoSidebar();
        }
        // -------------------------------------------------------------------

        // 3. Chạy luồng lấy dữ liệu tin nhắn (Backend)
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
                    ChatUIHelper.addMessageBubble(msgContainer, msgScrollPane, msg, isMe);
                }
            });
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML
    public void handleVoiceCall() {
        if (currentChatUser == null) return;
        if ("GROUP".equals(currentChatUser.getUsername())) {
            Alert a = new Alert(Alert.AlertType.WARNING, "Chưa hỗ trợ gọi nhóm!");
            a.show();
            return;
        }

        MessageDTO msg = new MessageDTO();
        msg.setType(MessageDTO.MessageType.CALL_REQ);
        msg.setSenderId(SessionStore.currentUser.getId());
        msg.setSenderName(SessionStore.currentUser.getDisplayName());
        msg.setConversationId(activeConversationId);
        msg.setCreatedAt(LocalDateTime.now());
        msg.setContent("Đang gọi Video cho bạn...");

        sendP2PMessage(msg);
        ChatUIHelper.addMessageBubble(msgContainer, msgScrollPane, msg, true);
    }

    private void openVideoCallWindow(String targetIp, int targetPort) {
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/video-call.fxml"));
                Parent root = loader.load();

                VideoCallController ctrl = loader.getController();
                this.currentVideoCallController = ctrl;

                ctrl.setDependencies(this, voiceCallManager);
                ctrl.startCall(targetIp, targetPort, SessionStore.p2pPort);

                Stage stage = new Stage();
                stage.setTitle("Video Call - " + currentChatTitle.getText());
                stage.setScene(new Scene(root));
                stage.setOnCloseRequest(e -> ctrl.handleEndCall());
                stage.show();

            } catch (Exception e) {
                e.printStackTrace();
                Alert a = new Alert(Alert.AlertType.ERROR, "Không thể mở Camera hoặc cửa sổ Video!");
                a.show();
            }
        });
    }

    public void handleEndCallSignal() {
        MessageDTO msg = new MessageDTO();
        msg.setType(MessageDTO.MessageType.CALL_END);
        msg.setSenderId(SessionStore.currentUser.getId());
        msg.setConversationId(activeConversationId);

        sendP2PMessage(msg);
        ChatUIHelper.addMessageBubble(msgContainer, msgScrollPane, msg, true);
        currentVideoCallController = null;
    }

    @FXML
    public void handleSendFile() {
        if (currentChatUser == null) return;
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn file gửi");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Tất cả", "*.*"),
                new FileChooser.ExtensionFilter("Ảnh", "*.png", "*.jpg", "*.jpeg", "*.gif")
        );
        File selectedFile = fileChooser.showOpenDialog(inputField.getScene().getWindow());

        if (selectedFile != null) {
            if (selectedFile.length() > 20 * 1024 * 1024) {
                Alert a = new Alert(Alert.AlertType.WARNING, "File quá lớn! Vui lòng gửi dưới 20MB.");
                a.show();
                return;
            }

            new Thread(() -> {
                try {
                    byte[] fileBytes = Files.readAllBytes(selectedFile.toPath());
                    String fileName = selectedFile.getName();

                    MessageDTO msg = new MessageDTO();
                    msg.setSenderId(SessionStore.currentUser.getId());
                    msg.setSenderName(SessionStore.currentUser.getDisplayName());
                    msg.setConversationId(activeConversationId);
                    msg.setCreatedAt(LocalDateTime.now());
                    msg.setFileData(fileBytes);
                    msg.setFileName(fileName);

                    String lowerName = fileName.toLowerCase();
                    if (lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
                        msg.setType(MessageDTO.MessageType.IMAGE);
                        msg.setContent("[Hình ảnh]");
                    } else {
                        msg.setType(MessageDTO.MessageType.FILE);
                        msg.setContent("[File: " + fileName + "]");
                    }

                    sendP2PMessage(msg);

                } catch (Exception e) { e.printStackTrace(); }
            }).start();
        }
    }
    // [MỚI] Hàm xử lý khi bấm nút "i"
    @FXML
    public void handleToggleInfo() {
        if (currentChatUser == null) return;

        if (isInfoSidebarOpen) {
            // Nếu đang mở -> Đóng lại (Set Right = null)
            mainBorderPane.setRight(null);
            isInfoSidebarOpen = false;
        } else {
            // Nếu đang đóng -> Mở ra
            openInfoSidebar();
        }
    }

    private void openInfoSidebar() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/chat-info.fxml"));
            Parent infoNode = loader.load();

            // Lấy controller của sidebar để truyền dữ liệu user vào
            ChatInfoController infoCtrl = loader.getController();
            infoCtrl.setUserInfo(currentChatUser);

            // Gắn vào bên phải của BorderPane
            mainBorderPane.setRight(infoNode);
            isInfoSidebarOpen = true;

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @FXML
    public void handleSend() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || currentChatUser == null) return;

        MessageDTO msg = new MessageDTO();
        msg.setSenderId(SessionStore.currentUser.getId());
        msg.setSenderName(SessionStore.currentUser.getDisplayName());
        msg.setConversationId(activeConversationId);
        msg.setCreatedAt(LocalDateTime.now());
        msg.setContent(text);
        msg.setType(MessageDTO.MessageType.TEXT);

        sendP2PMessage(msg);
        inputField.clear();
    }

    @FXML
    public void startRecording(MouseEvent event) {
        if (currentChatUser == null) return;
        recordingStartTime = System.currentTimeMillis();
        micBtn.setStyle("-fx-text-fill: red; -fx-font-size: 20px;");
        audioRecorder.startRecording();
    }

    @FXML
    public void stopAndSendAudio(MouseEvent event) {
        if (currentChatUser == null) return;
        micBtn.setStyle("-fx-text-fill: #0084ff; -fx-font-size: 20px;");

        if (System.currentTimeMillis() - recordingStartTime < 500) {
            audioRecorder.stopRecording();
            return;
        }

        byte[] audioData = audioRecorder.stopRecording();
        if (audioData != null && audioData.length > 0) {
            new Thread(() -> {
                try {
                    MessageDTO msg = new MessageDTO();
                    msg.setSenderId(SessionStore.currentUser.getId());
                    msg.setSenderName(SessionStore.currentUser.getDisplayName());
                    msg.setConversationId(activeConversationId);
                    msg.setCreatedAt(LocalDateTime.now());
                    msg.setType(MessageDTO.MessageType.AUDIO);
                    msg.setFileData(audioData);
                    msg.setContent("[Tin nhắn thoại]");
                    sendP2PMessage(msg);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }

    private void sendP2PMessage(MessageDTO msg) {
        UserDTO targetCache = currentChatUser;
        boolean isGroup = "GROUP".equals(targetCache.getUsername());

        if (isGroup) {
            try {
                List<Long> memberIds = RmiClient.getGroupService().getGroupMemberIds(activeConversationId);
                for (Long memId : memberIds) {
                    if (memId == SessionStore.currentUser.getId()) continue;
                    UserDTO memInfo = RmiClient.getDirectoryService().getUserInfo(memId);
                    if (memInfo != null && memInfo.isOnline()) {
                        p2pClient.send(memInfo.getLastIp(), memInfo.getLastPort(), msg);
                    }
                }
            } catch (Exception e) { System.err.println("Lỗi gửi Group P2P: " + e.getMessage()); }
        } else {
            if (targetCache != null && targetCache.getLastIp() != null) {
                p2pClient.send(targetCache.getLastIp(), targetCache.getLastPort(), msg);
            }
        }

        if (msg.getType() != MessageDTO.MessageType.CALL_REQ &&
                msg.getType() != MessageDTO.MessageType.CALL_ACCEPT &&
                msg.getType() != MessageDTO.MessageType.CALL_DENY &&
                msg.getType() != MessageDTO.MessageType.CALL_END) {
            ChatUIHelper.addMessageBubble(msgContainer, msgScrollPane, msg, true);
        }

        new Thread(() -> {
            try {
                MessageDTO backupMsg = new MessageDTO();
                backupMsg.setConversationId(msg.getConversationId());
                backupMsg.setSenderId(msg.getSenderId());
                backupMsg.setCreatedAt(msg.getCreatedAt());
                backupMsg.setContent(msg.getContent());
                backupMsg.setType(msg.getType());

                if (msg.getFileData() != null &&
                        (msg.getType() == MessageDTO.MessageType.IMAGE ||
                                msg.getType() == MessageDTO.MessageType.FILE ||
                                msg.getType() == MessageDTO.MessageType.AUDIO)) {

                    String fName = msg.getFileName() != null ? msg.getFileName() : "audio_" + System.currentTimeMillis() + ".wav";
                    String serverPath = RmiClient.getMessageService().uploadFile(msg.getFileData(), fName);
                    backupMsg.setAttachmentUrl(serverPath);
                    backupMsg.setFileData(null);
                }

                RmiClient.getMessageService().saveMessage(backupMsg);
                RmiClient.getMessageService().markAsRead(SessionStore.currentUser.getId(), activeConversationId);

            } catch (Exception e) {
                System.out.println("Lỗi Backup Server: " + e.getMessage());
            }
        }).start();
    }

    public void onMessageReceived(MessageDTO msg) {
        Platform.runLater(() -> {
            if (msg.getType() == MessageDTO.MessageType.CALL_REQ) {
                handleIncomingCall(msg);
                return;
            } else if (msg.getType() == MessageDTO.MessageType.CALL_ACCEPT) {
                UserDTO partner = findUserInList(msg.getSenderId());
                if (partner != null) {
                    openVideoCallWindow(partner.getLastIp(), partner.getLastPort());
                }
                return;
            } else if (msg.getType() == MessageDTO.MessageType.CALL_DENY) {
                Alert a = new Alert(Alert.AlertType.ERROR, "Người kia đã từ chối cuộc gọi.");
                a.show();
                return;
            } else if (msg.getType() == MessageDTO.MessageType.CALL_END) {
                if (currentVideoCallController != null) {
                    currentVideoCallController.closeWindow();
                    currentVideoCallController = null;
                }
                if (voiceCallManager.isCalling()) {
                    voiceCallManager.stopCall();
                }
                Alert a = new Alert(Alert.AlertType.INFORMATION, "Cuộc gọi đã kết thúc.");
                a.show();
                return;
            }

            if (activeConversationId != -1 && msg.getConversationId() == activeConversationId) {
                ChatUIHelper.addMessageBubble(msgContainer, msgScrollPane, msg, false);
                new Thread(() -> {
                    try { RmiClient.getMessageService().markAsRead(SessionStore.currentUser.getId(), activeConversationId); } catch (Exception e) {}
                }).start();
            } else {
                increaseUnreadCountLocal(msg);
            }
        });
    }

    private void handleIncomingCall(MessageDTO msg) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Cuộc gọi đến");
        alert.setHeaderText(msg.getSenderName() + " đang gọi Video cho bạn!");
        alert.setContentText("Bạn có muốn nghe máy không?");

        ButtonType btnYes = new ButtonType("Nghe");
        ButtonType btnNo = new ButtonType("Từ chối", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(btnYes, btnNo);

        alert.showAndWait().ifPresent(type -> {
            if (type == btnYes) {
                MessageDTO acceptMsg = new MessageDTO();
                acceptMsg.setType(MessageDTO.MessageType.CALL_ACCEPT);
                acceptMsg.setSenderId(SessionStore.currentUser.getId());
                acceptMsg.setConversationId(msg.getConversationId());

                UserDTO caller = findUserInList(msg.getSenderId());
                if (caller != null) {
                    p2pClient.send(caller.getLastIp(), caller.getLastPort(), acceptMsg);
                    openVideoCallWindow(caller.getLastIp(), caller.getLastPort());
                }
            } else {
                MessageDTO denyMsg = new MessageDTO();
                denyMsg.setType(MessageDTO.MessageType.CALL_DENY);
                denyMsg.setSenderId(SessionStore.currentUser.getId());

                UserDTO caller = findUserInList(msg.getSenderId());
                if (caller != null) {
                    p2pClient.send(caller.getLastIp(), caller.getLastPort(), denyMsg);
                }
            }
        });
    }

    private UserDTO findUserInList(long userId) {
        for (UserDTO u : conversationList.getItems()) {
            if (u.getId() == userId) return u;
        }
        return null;
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

    @FXML
    public void handleOpenProfile() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/profile-view.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("Hồ sơ cá nhân");
            stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) { e.printStackTrace(); }
    }

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

    public void refreshProfileUI() {
        myDisplayName.setText(SessionStore.currentUser.getDisplayName());
        loadMyAvatar(SessionStore.currentUser.getAvatarUrl());
    }
}