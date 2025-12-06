package com.example.client.controller;

import com.example.client.net.P2PClient;
import com.example.client.net.RealTimeHandler;
import com.example.client.net.RmiClient;
import com.example.client.net.VoiceCallManager; // Import Manager xử lý thoại
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
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
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

    private RealTimeHandler realTimeHandler;

    // Manager xử lý thoại (Voice Call)
    private final VoiceCallManager voiceCallManager = new VoiceCallManager();

    @FXML
    public void initialize() {
        conversationList.setCellFactory(param -> new FriendListCell());

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

    // --- [1. TÍNH NĂNG] GỌI THOẠI (VOICE CALL) ---

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
        msg.setContent("Đang gọi cho bạn...");

        sendP2PMessage(msg); // Gửi yêu cầu

        // Hiển thị thông báo chờ trên giao diện của mình
        ChatUIHelper.addMessageBubble(msgContainer, msgScrollPane, msg, true);
    }

    // Nút kết thúc cuộc gọi (cần thêm Button vào UI nếu muốn dùng)
    public void handleEndCall() {
        if (voiceCallManager.isCalling()) {
            voiceCallManager.stopCall();

            // Gửi tín hiệu kết thúc
            MessageDTO msg = new MessageDTO();
            msg.setType(MessageDTO.MessageType.CALL_END);
            msg.setSenderId(SessionStore.currentUser.getId());
            msg.setConversationId(activeConversationId);
            sendP2PMessage(msg);

            ChatUIHelper.addMessageBubble(msgContainer, msgScrollPane, msg, true);
        }
    }

    // --- [2. TÍNH NĂNG] GỬI FILE/ẢNH ---

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

    // --- HÀM GỬI P2P CHUNG (CHO CẢ TEXT, FILE, VOICE SIGNAL) ---
    private void sendP2PMessage(MessageDTO msg) {
        UserDTO targetCache = currentChatUser;
        boolean isGroup = "GROUP".equals(targetCache.getUsername());

        // 1. GỬI P2P
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
            } catch (Exception e) {
                System.err.println("Lỗi gửi Group P2P: " + e.getMessage());
            }
        } else {
            // Chat 1-1: Sử dụng Cache
            if (targetCache != null && targetCache.getLastIp() != null) {
                p2pClient.send(targetCache.getLastIp(), targetCache.getLastPort(), msg);
            } else {
                System.err.println("Không tìm thấy IP bạn bè.");
            }
        }

        // 2. HIỂN THỊ UI MÌNH (Trừ các gói tin tín hiệu gọi điện thì không cần hiển thị bubble chat thường)
        if (msg.getType() != MessageDTO.MessageType.CALL_REQ &&
                msg.getType() != MessageDTO.MessageType.CALL_ACCEPT &&
                msg.getType() != MessageDTO.MessageType.CALL_DENY &&
                msg.getType() != MessageDTO.MessageType.CALL_END) {
            ChatUIHelper.addMessageBubble(msgContainer, msgScrollPane, msg, true);
        }

        // 3. LƯU BACKUP SERVER (Trừ các tin gọi điện và file nặng)
        try {
            if (msg.getType() == MessageDTO.MessageType.TEXT) {
                RmiClient.getMessageService().saveMessage(msg);
            } else if (msg.getType() == MessageDTO.MessageType.IMAGE || msg.getType() == MessageDTO.MessageType.FILE) {
                MessageDTO backupMsg = new MessageDTO();
                backupMsg.setConversationId(msg.getConversationId());
                backupMsg.setSenderId(msg.getSenderId());
                backupMsg.setCreatedAt(msg.getCreatedAt());
                backupMsg.setContent(msg.getContent());
                backupMsg.setType(msg.getType());
                RmiClient.getMessageService().saveMessage(backupMsg);
            }
            RmiClient.getMessageService().markAsRead(SessionStore.currentUser.getId(), activeConversationId);
        } catch (Exception e) {
            System.out.println("Server Offline: Tin nhắn P2P đã gửi nhưng chưa lưu backup.");
        }
    }

    // --- [XỬ LÝ NHẬN TIN NHẮN P2P] ---
    public void onMessageReceived(MessageDTO msg) {
        Platform.runLater(() -> {
            // A. XỬ LÝ TÍN HIỆU CUỘC GỌI
            if (msg.getType() == MessageDTO.MessageType.CALL_REQ) {
                handleIncomingCall(msg);
                return;
            } else if (msg.getType() == MessageDTO.MessageType.CALL_ACCEPT) {
                startVoiceStream(msg.getSenderId()); // Người mời nhận được chấp nhận -> Bắt đầu stream
                Alert a = new Alert(Alert.AlertType.INFORMATION, "Đối phương đã nghe máy! Bắt đầu cuộc gọi.");
                a.show();
                return;
            } else if (msg.getType() == MessageDTO.MessageType.CALL_DENY) {
                Alert a = new Alert(Alert.AlertType.ERROR, "Người kia đã từ chối cuộc gọi.");
                a.show();
                return;
            } else if (msg.getType() == MessageDTO.MessageType.CALL_END) {
                if (voiceCallManager.isCalling()) {
                    voiceCallManager.stopCall();
                    Alert a = new Alert(Alert.AlertType.INFORMATION, "Cuộc gọi đã kết thúc.");
                    a.show();
                }
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
        alert.setHeaderText(msg.getSenderName() + " đang gọi cho bạn!");
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
                    // 2. Bắt đầu luồng Voice của mình (Người nhận)
                    startVoiceStream(msg.getSenderId());
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

    private void startVoiceStream(long partnerId) {
        UserDTO partner = findUserInList(partnerId);
        if (partner != null) {
            // Quy ước đơn giản: UDP Port = TCP Port + 1
            int myUdpPort = SessionStore.p2pPort + 1;
            int partnerUdpPort = partner.getLastPort() + 1;

            // Bắt đầu luồng gửi/nhận âm thanh UDP
            voiceCallManager.startCall(partner.getLastIp(), partnerUdpPort, myUdpPort);
        } else {
            System.err.println("Lỗi: Không tìm thấy thông tin kết nối của đối phương để gọi thoại.");
        }
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

    // --- CÁC HÀM MỞ DIALOG ---
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
}