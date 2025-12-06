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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class MainController {

    @FXML private BorderPane mainBorderPane;
    @FXML private Label myDisplayName;
    @FXML private ImageView myAvatarView;
    @FXML private ListView<UserDTO> conversationList;
    @FXML private VBox chatArea, welcomeArea, msgContainer;
    @FXML private Label currentChatTitle;
    @FXML private TextField inputField;
    @FXML private ScrollPane msgScrollPane;
    @FXML private Button micBtn;

    private final AudioHelper audioRecorder = new AudioHelper();
    private long recordingStartTime;
    private P2PClient p2pClient;
    private UserDTO currentChatUser;
    private long activeConversationId = -1;
    private RealTimeHandler realTimeHandler;
    private final VoiceCallManager voiceCallManager = new VoiceCallManager();
    private VideoCallController currentVideoCallController;
    private boolean isInfoSidebarOpen = false;

    @FXML
    public void initialize() {
        conversationList.setCellFactory(param -> new FriendListCell());

        UserDTO me = SessionStore.currentUser;
        if (me != null) {
            myDisplayName.setText(me.getDisplayName());
            loadMyAvatar(me.getAvatarUrl());
            startP2P();
            loadFriendListInitial();
            registerRealTimeUpdates();
        }

        conversationList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) switchChat(newVal);
        });
    }

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

    private void registerRealTimeUpdates() {
        try {
            // Khởi tạo handler để nhận thông báo từ server
            realTimeHandler = new RealTimeHandler(this);
            RmiClient.getAuthService().registerNotification(SessionStore.currentUser.getId(), realTimeHandler);
            System.out.println("Đã đăng ký Real-time updates.");
        } catch (Exception e) {
            System.err.println("Lỗi đăng ký Real-time: " + e.getMessage());
        }
    }

    // [HÀM QUAN TRỌNG] Cập nhật danh sách khi có sự thay đổi (Online/Offline/Nhóm mới)
    public void updateFriendInList(UserDTO updatedFriend) {
        boolean found = false;
        for (int i = 0; i < conversationList.getItems().size(); i++) {
            UserDTO u = conversationList.getItems().get(i);
            // Kiểm tra trùng ID
            if (u.getId() == updatedFriend.getId()) {
                // Cập nhật trạng thái
                u.setOnline(updatedFriend.isOnline());
                u.setLastIp(updatedFriend.getLastIp());
                u.setLastPort(updatedFriend.getLastPort());
                if (updatedFriend.getAvatarUrl() != null) u.setAvatarUrl(updatedFriend.getAvatarUrl());
                if (updatedFriend.getStatusMsg() != null) u.setStatusMsg(updatedFriend.getStatusMsg());

                found = true;
                conversationList.getItems().set(i, u);

                // Nếu đang chat với người này, cập nhật biến currentChatUser
                if (currentChatUser != null && currentChatUser.getId() == updatedFriend.getId()) {
                    currentChatUser = u;
                }
                break;
            }
        }
        // Nếu chưa có trong list (ví dụ Nhóm mới tạo), thêm vào đầu
        if (!found) {
            conversationList.getItems().add(0, updatedFriend);
        }
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
        this.currentChatUser = friendOrGroup;
        welcomeArea.setVisible(false);
        chatArea.setVisible(true);
        currentChatTitle.setText(friendOrGroup.getDisplayName());
        msgContainer.getChildren().clear();

        // Reset số tin nhắn chưa đọc khi bấm vào
        friendOrGroup.setUnreadCount(0);
        conversationList.refresh();

        if (isInfoSidebarOpen) {
            openInfoSidebar();
        }

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
                scrollToBottom();
            });
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void scrollToBottom() {
        msgContainer.applyCss();
        msgContainer.layout();
        Platform.runLater(() -> {
            msgScrollPane.layout();
            msgScrollPane.setVvalue(1.0);
            Platform.runLater(() -> msgScrollPane.setVvalue(1.0));
        });
    }

    // --- XỬ LÝ NHẬN TIN NHẮN (SỬA LỖI THÔNG BÁO) ---
    public void onMessageReceived(MessageDTO msg) {
        Platform.runLater(() -> {
            if (msg.getType() == MessageDTO.MessageType.CALL_REQ) {
                handleIncomingCall(msg);
                return;
            } else if (msg.getType() == MessageDTO.MessageType.CALL_ACCEPT) {
                UserDTO partner = findUserInList(msg.getSenderId());
                if (partner != null) openVideoCallWindow(partner.getLastIp(), partner.getLastPort());
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
                if (voiceCallManager.isCalling()) voiceCallManager.stopCall();
                Alert a = new Alert(Alert.AlertType.INFORMATION, "Cuộc gọi đã kết thúc.");
                a.show();
                return;
            }

            // Xử lý tin nhắn chat
            if (activeConversationId != -1 && msg.getConversationId() == activeConversationId) {
                // Đang chat với người này -> Hiện tin nhắn luôn
                ChatUIHelper.addMessageBubble(msgContainer, msgScrollPane, msg, false);
                new Thread(() -> {
                    try { RmiClient.getMessageService().markAsRead(SessionStore.currentUser.getId(), activeConversationId); } catch (Exception e) {}
                }).start();
            } else {
                // Không chat -> Chỉ cần cập nhật list bên trái (logic nằm ở moveUserToTop)
            }

            // [QUAN TRỌNG] Gọi hàm cập nhật vị trí và số lượng chưa đọc
            moveUserToTop(msg);
        });
    }

    // [SỬA LỖI] Hàm đẩy user lên đầu và tăng số tin nhắn chưa đọc
    private void moveUserToTop(MessageDTO msg) {
        for (int i = 0; i < conversationList.getItems().size(); i++) {
            UserDTO u = conversationList.getItems().get(i);
            boolean isMatch = false;

            // Tìm đúng người hoặc nhóm trong danh sách
            if ("GROUP".equals(u.getUsername())) {
                if (u.getId() == msg.getConversationId()) isMatch = true;
            } else {
                if (u.getId() == msg.getSenderId()) isMatch = true;
            }

            if (isMatch) {
                // Logic kiểm tra xem có đang chat với người này không
                boolean isChattingWithThis = false;
                if (currentChatUser != null) {
                    // So sánh ID cẩn thận
                    if (currentChatUser.getId() == u.getId()) {
                        isChattingWithThis = true;
                    }
                }

                // Nếu KHÔNG đang chat với người này thì mới tăng số chưa đọc
                if (!isChattingWithThis) {
                    u.setUnreadCount(u.getUnreadCount() + 1);
                }

                // Đẩy lên đầu danh sách
                conversationList.getItems().remove(i);
                conversationList.getItems().add(0, u);
                conversationList.refresh();
                break;
            }
        }
    }

    // --- CÁC HÀM GỬI TIN & BACKUP (Giữ nguyên) ---
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

                if (msg.getFileData() != null && msg.getType() != MessageDTO.MessageType.TEXT) {
                    String fName = msg.getFileName() != null ? msg.getFileName() : "file_" + System.currentTimeMillis();
                    String serverPath = RmiClient.getMessageService().uploadFile(msg.getFileData(), fName);
                    backupMsg.setAttachmentUrl(serverPath);
                    backupMsg.setFileData(null);
                }

                RmiClient.getMessageService().saveMessage(backupMsg);
                RmiClient.getMessageService().markAsRead(SessionStore.currentUser.getId(), activeConversationId);

            } catch (Exception e) {
                System.err.println(">> Client: Lỗi Backup Server: " + e.getMessage());
            }
        }).start();

        moveUserToTop(msg);
    }

    @FXML public void handleVoiceCall() {
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
                if (caller != null) p2pClient.send(caller.getLastIp(), caller.getLastPort(), denyMsg);
            }
        });
    }

    private UserDTO findUserInList(long userId) {
        for (UserDTO u : conversationList.getItems()) {
            if (u.getId() == userId) return u;
        }
        return null;
    }

    // Các hàm Upload file, Ghi âm, Video Call, UI
    @FXML public void handleSendFile() {
        if (currentChatUser == null) return;
        FileChooser fileChooser = new FileChooser();
        File selectedFile = fileChooser.showOpenDialog(inputField.getScene().getWindow());
        if (selectedFile != null) {
            new Thread(() -> {
                try {
                    byte[] fileBytes = Files.readAllBytes(selectedFile.toPath());
                    MessageDTO msg = new MessageDTO();
                    msg.setSenderId(SessionStore.currentUser.getId());
                    msg.setSenderName(SessionStore.currentUser.getDisplayName());
                    msg.setConversationId(activeConversationId);
                    msg.setCreatedAt(LocalDateTime.now());
                    msg.setFileData(fileBytes);
                    msg.setFileName(selectedFile.getName());
                    String lower = selectedFile.getName().toLowerCase();
                    if (lower.endsWith(".png") || lower.endsWith(".jpg")) msg.setType(MessageDTO.MessageType.IMAGE);
                    else msg.setType(MessageDTO.MessageType.FILE);
                    sendP2PMessage(msg);
                } catch (Exception e) {}
            }).start();
        }
    }

    @FXML public void handleSend() {
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

    @FXML public void startRecording(MouseEvent event) {
        if (currentChatUser == null) return;
        recordingStartTime = System.currentTimeMillis();
        micBtn.setStyle("-fx-text-fill: red; -fx-font-size: 20px;");
        audioRecorder.startRecording();
    }

    @FXML public void stopAndSendAudio(MouseEvent event) {
        if (currentChatUser == null) return;
        micBtn.setStyle("-fx-text-fill: #667eea; -fx-font-size: 20px;");
        if (System.currentTimeMillis() - recordingStartTime < 500) {
            audioRecorder.stopRecording(); return;
        }
        byte[] audioData = audioRecorder.stopRecording();
        if (audioData != null) {
            new Thread(() -> {
                MessageDTO msg = new MessageDTO();
                msg.setSenderId(SessionStore.currentUser.getId());
                msg.setSenderName(SessionStore.currentUser.getDisplayName());
                msg.setConversationId(activeConversationId);
                msg.setCreatedAt(LocalDateTime.now());
                msg.setType(MessageDTO.MessageType.AUDIO);
                msg.setFileData(audioData);
                sendP2PMessage(msg);
            }).start();
        }
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
                stage.setTitle("Video Call");
                stage.setScene(new Scene(root));
                stage.setOnCloseRequest(e -> ctrl.handleEndCall());
                stage.show();
            } catch (Exception e) {}
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

    @FXML public void handleCreateGroup() { openDialog("/view/create-group.fxml", "Tạo Nhóm"); }
    @FXML public void handleAddFriend() { openDialog("/view/add-friend.fxml", "Thêm bạn bè"); }
    @FXML public void handleShowRequests() { openDialog("/view/friend-requests.fxml", "Lời mời"); }
    @FXML public void handleOpenProfile() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/profile-view.fxml"));
            Stage stage = new Stage();
            stage.setScene(new Scene(loader.load()));
            stage.show();
        } catch (Exception e) {}
    }
    @FXML public void handleToggleInfo() {
        if (currentChatUser == null) return;
        if (isInfoSidebarOpen) { mainBorderPane.setRight(null); isInfoSidebarOpen = false; }
        else openInfoSidebar();
    }
    private void openInfoSidebar() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/chat-info.fxml"));
            Parent infoNode = loader.load();
            ChatInfoController infoCtrl = loader.getController();
            infoCtrl.setUserInfo(currentChatUser);
            mainBorderPane.setRight(infoNode);
            isInfoSidebarOpen = true;
        } catch (Exception e) {}
    }
    private void openDialog(String fxml, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Parent root = loader.load();
            if (loader.getController() instanceof FriendRequestController) {
                ((FriendRequestController) loader.getController()).setMainController(this);
            }
            Stage stage = new Stage();
            stage.setTitle(title);
            stage.setScene(new Scene(root));
            stage.showAndWait();
            loadFriendListInitial();
        } catch (Exception e) {}
    }
    public void refreshProfileUI() {
        myDisplayName.setText(SessionStore.currentUser.getDisplayName());
        loadMyAvatar(SessionStore.currentUser.getAvatarUrl());
    }
}