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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class MainController {

    @FXML private Label myDisplayName;
    @FXML private ImageView myAvatarView; // [MỚI] Ảnh đại diện của tôi
    @FXML private ListView<UserDTO> conversationList;
    @FXML private VBox chatArea, welcomeArea, msgContainer;
    @FXML private Label currentChatTitle;
    @FXML private TextField inputField;
    @FXML private ScrollPane msgScrollPane;
    @FXML private Button micBtn; // Nút Micro

    // Đối tượng hỗ trợ ghi âm
    private final AudioHelper audioRecorder = new AudioHelper();
    private long recordingStartTime;

    private P2PClient p2pClient;
    private UserDTO currentChatUser;
    private long activeConversationId = -1;

    private RealTimeHandler realTimeHandler;

    // Manager xử lý thoại (Voice Call) - Giữ lại để hỗ trợ VideoController
    private final VoiceCallManager voiceCallManager = new VoiceCallManager();

    // Tham chiếu đến cửa sổ Video đang mở (nếu có)
    private VideoCallController currentVideoCallController;

    @FXML
    public void initialize() {
        conversationList.setCellFactory(param -> new FriendListCell());

        UserDTO me = SessionStore.currentUser;
        if (me != null) {
            myDisplayName.setText(me.getDisplayName());

            // [MỚI] Load Avatar của tôi
            loadMyAvatar(me.getAvatarUrl());

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
            realTimeHandler = new RealTimeHandler(this);
            RmiClient.getAuthService().registerNotification(SessionStore.currentUser.getId(), realTimeHandler);
            System.out.println("Đã đăng ký Real-time updates.");
        } catch (Exception e) {
            System.err.println("Lỗi đăng ký Real-time (Có thể Server chưa bật): " + e.getMessage());
        }
    }

    // --- CẬP NHẬT UI TỪ REAL-TIME HANDLER ---
    public void updateFriendInList(UserDTO updatedFriend) {
        boolean found = false;
        for (int i = 0; i < conversationList.getItems().size(); i++) {
            UserDTO u = conversationList.getItems().get(i);
            if (u.getId() == updatedFriend.getId()) {
                u.setOnline(updatedFriend.isOnline());
                u.setLastIp(updatedFriend.getLastIp());
                u.setLastPort(updatedFriend.getLastPort());
                // Cập nhật cả avatar/status nếu có thay đổi
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

    // --- CÁC HÀM LOGIC CỐT LÕI (LOAD LIST, START P2P, SWITCH CHAT) ---

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
                    ChatUIHelper.addMessageBubble(msgContainer, msgScrollPane, msg, isMe);
                }
            });
        } catch (Exception e) { e.printStackTrace(); }
    }

    // --- [TÍNH NĂNG] GỌI VIDEO ---

    @FXML
    public void handleVoiceCall() {
        if (currentChatUser == null) return;
        if ("GROUP".equals(currentChatUser.getUsername())) {
            Alert a = new Alert(Alert.AlertType.WARNING, "Chưa hỗ trợ gọi nhóm!");
            a.show();
            return;
        }

        // Gửi yêu cầu CALL_REQ qua đường P2P TCP
        MessageDTO msg = new MessageDTO();
        msg.setType(MessageDTO.MessageType.CALL_REQ);
        msg.setSenderId(SessionStore.currentUser.getId());
        msg.setSenderName(SessionStore.currentUser.getDisplayName());
        msg.setConversationId(activeConversationId);
        msg.setCreatedAt(LocalDateTime.now());
        msg.setContent("Đang gọi Video cho bạn...");

        sendP2PMessage(msg); // Gửi yêu cầu

        // Hiển thị thông báo chờ trên giao diện của mình
        ChatUIHelper.addMessageBubble(msgContainer, msgScrollPane, msg, true);
    }

    // Hàm mở cửa sổ Video Call
    private void openVideoCallWindow(String targetIp, int targetPort) {
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/video-call.fxml"));
                Parent root = loader.load();

                VideoCallController ctrl = loader.getController();
                this.currentVideoCallController = ctrl;

                // Truyền các dependency cần thiết
                ctrl.setDependencies(this, voiceCallManager);

                // Bắt đầu stream (Voice + Video)
                // targetPort: Port P2P TCP của đối phương
                // SessionStore.p2pPort: Port P2P TCP của mình
                ctrl.startCall(targetIp, targetPort, SessionStore.p2pPort);

                Stage stage = new Stage();
                stage.setTitle("Video Call - " + currentChatTitle.getText());
                stage.setScene(new Scene(root));

                // Khi tắt cửa sổ bằng dấu X -> Cũng gọi hàm kết thúc
                stage.setOnCloseRequest(e -> ctrl.handleEndCall());

                stage.show();

            } catch (Exception e) {
                e.printStackTrace();
                Alert a = new Alert(Alert.AlertType.ERROR, "Không thể mở Camera hoặc cửa sổ Video!");
                a.show();
            }
        });
    }

    // Hàm được VideoCallController gọi khi bấm "Kết thúc"
    public void handleEndCallSignal() {
        // Gửi tín hiệu kết thúc cho đối phương biết
        MessageDTO msg = new MessageDTO();
        msg.setType(MessageDTO.MessageType.CALL_END);
        msg.setSenderId(SessionStore.currentUser.getId());
        msg.setConversationId(activeConversationId);

        sendP2PMessage(msg);
        ChatUIHelper.addMessageBubble(msgContainer, msgScrollPane, msg, true);

        // Reset biến
        currentVideoCallController = null;
    }

    // --- [TÍNH NĂNG] GỬI FILE/ẢNH ---

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

    // --- GỬI TIN NHẮN TEXT ---
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

    // --- [TÍNH NĂNG] GHI ÂM (VOICE MESSAGE) ---
    @FXML
    public void startRecording(MouseEvent event) {
        if (currentChatUser == null) return;

        recordingStartTime = System.currentTimeMillis();
        micBtn.setStyle("-fx-text-fill: red; -fx-font-size: 20px;"); // Đổi màu đỏ báo hiệu đang ghi
        audioRecorder.startRecording();
    }

    @FXML
    public void stopAndSendAudio(MouseEvent event) {
        if (currentChatUser == null) return;

        micBtn.setStyle("-fx-text-fill: #0084ff; -fx-font-size: 20px;"); // Trả lại màu xanh

        // Kiểm tra nếu bấm quá nhanh (dưới 0.5s) thì coi như bấm nhầm -> Hủy
        if (System.currentTimeMillis() - recordingStartTime < 500) {
            audioRecorder.stopRecording(); // Dừng nhưng không gửi
            System.out.println("Ghi âm quá ngắn, đã hủy.");
            return;
        }

        // Lấy dữ liệu âm thanh
        byte[] audioData = audioRecorder.stopRecording();

        if (audioData != null && audioData.length > 0) {
            new Thread(() -> {
                try {
                    MessageDTO msg = new MessageDTO();
                    msg.setSenderId(SessionStore.currentUser.getId());
                    msg.setSenderName(SessionStore.currentUser.getDisplayName());
                    msg.setConversationId(activeConversationId);
                    msg.setCreatedAt(LocalDateTime.now());

                    // Thiết lập thông tin Audio
                    msg.setType(MessageDTO.MessageType.AUDIO);
                    msg.setFileData(audioData);
                    msg.setContent("[Tin nhắn thoại]");

                    // Gửi qua P2P
                    sendP2PMessage(msg);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }

    // --- HÀM GỬI P2P & BACKUP SERVER ---
    private void sendP2PMessage(MessageDTO msg) {
        UserDTO targetCache = currentChatUser;
        boolean isGroup = "GROUP".equals(targetCache.getUsername());

        // 1. GỬI P2P (Tốc độ cao - LAN)
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
            } else {
                System.err.println("Không tìm thấy IP bạn bè.");
            }
        }

        // 2. HIỂN THỊ UI MÌNH
        if (msg.getType() != MessageDTO.MessageType.CALL_REQ &&
                msg.getType() != MessageDTO.MessageType.CALL_ACCEPT &&
                msg.getType() != MessageDTO.MessageType.CALL_DENY &&
                msg.getType() != MessageDTO.MessageType.CALL_END) {
            ChatUIHelper.addMessageBubble(msgContainer, msgScrollPane, msg, true);
        }

        // 3. UPLOAD VÀ LƯU BACKUP SERVER
        new Thread(() -> {
            try {
                MessageDTO backupMsg = new MessageDTO();
                backupMsg.setConversationId(msg.getConversationId());
                backupMsg.setSenderId(msg.getSenderId());
                backupMsg.setCreatedAt(msg.getCreatedAt());
                backupMsg.setContent(msg.getContent());
                backupMsg.setType(msg.getType());

                // Nếu có file/audio -> Upload lên Server trước
                if (msg.getFileData() != null &&
                        (msg.getType() == MessageDTO.MessageType.IMAGE ||
                                msg.getType() == MessageDTO.MessageType.FILE ||
                                msg.getType() == MessageDTO.MessageType.AUDIO)) {

                    System.out.println("Đang upload file lên Server backup...");
                    // Tạo tên file giả định nếu null (cho Audio)
                    String fName = msg.getFileName() != null ? msg.getFileName() : "audio_" + System.currentTimeMillis() + ".wav";

                    // Gọi RMI Upload
                    String serverPath = RmiClient.getMessageService().uploadFile(msg.getFileData(), fName);

                    // Gán đường dẫn vào backupMsg
                    backupMsg.setAttachmentUrl(serverPath);
                    // Xóa fileData để không lưu blob vào DB
                    backupMsg.setFileData(null);
                }

                // Lưu Metadata vào DB
                RmiClient.getMessageService().saveMessage(backupMsg);
                RmiClient.getMessageService().markAsRead(SessionStore.currentUser.getId(), activeConversationId);
                System.out.println("Đã backup tin nhắn lên Server.");

            } catch (Exception e) {
                System.out.println("Lỗi Backup Server: " + e.getMessage());
            }
        }).start();
    }

    // --- [XỬ LÝ NHẬN TIN NHẮN P2P] ---
    public void onMessageReceived(MessageDTO msg) {
        Platform.runLater(() -> {
            // A. XỬ LÝ TÍN HIỆU CUỘC GỌI
            if (msg.getType() == MessageDTO.MessageType.CALL_REQ) {
                handleIncomingCall(msg);
                return;
            } else if (msg.getType() == MessageDTO.MessageType.CALL_ACCEPT) {
                // Người kia đồng ý -> Mở cửa sổ Video Call
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
                // Đóng cửa sổ Video nếu đang mở
                if (currentVideoCallController != null) {
                    currentVideoCallController.closeWindow();
                    currentVideoCallController = null;
                }
                // Dừng Audio manager (dự phòng)
                if (voiceCallManager.isCalling()) {
                    voiceCallManager.stopCall();
                }

                Alert a = new Alert(Alert.AlertType.INFORMATION, "Cuộc gọi đã kết thúc.");
                a.show();
                return;
            }

            // B. XỬ LÝ TIN NHẮN CHAT BÌNH THƯỜNG
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

    // --- XỬ LÝ POPUP CUỘC GỌI ĐẾN ---
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
                // 1. Gửi phản hồi CHẤP NHẬN
                MessageDTO acceptMsg = new MessageDTO();
                acceptMsg.setType(MessageDTO.MessageType.CALL_ACCEPT);
                acceptMsg.setSenderId(SessionStore.currentUser.getId());
                acceptMsg.setConversationId(msg.getConversationId());

                UserDTO caller = findUserInList(msg.getSenderId());
                if (caller != null) {
                    p2pClient.send(caller.getLastIp(), caller.getLastPort(), acceptMsg);

                    // 2. Mở cửa sổ Video Call
                    openVideoCallWindow(caller.getLastIp(), caller.getLastPort());
                }
            } else {
                // Gửi phản hồi TỪ CHỐI
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

    // --- CÁC HÀM MỞ DIALOG & PROFILE ---
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

    // --- [HỖ TRỢ HIỂN THỊ PROFILE] ---

    // Hàm load avatar (tái sử dụng được)
    public void loadMyAvatar(String url) {
        if (url == null) return;
        new Thread(() -> {
            try {
                byte[] data = RmiClient.getMessageService().downloadFile(url);
                if (data != null) {
                    Image img = new Image(new ByteArrayInputStream(data));
                    Platform.runLater(() -> {
                        myAvatarView.setImage(img);
                        // Bo tròn ảnh
                        Circle clip = new Circle(20, 20, 20);
                        myAvatarView.setClip(clip);
                    });
                }
            } catch (Exception e) {}
        }).start();
    }

    // Gọi hàm này từ ProfileController sau khi update xong để refresh ngay lập tức
    public void refreshProfileUI() {
        myDisplayName.setText(SessionStore.currentUser.getDisplayName());
        loadMyAvatar(SessionStore.currentUser.getAvatarUrl());
    }
}