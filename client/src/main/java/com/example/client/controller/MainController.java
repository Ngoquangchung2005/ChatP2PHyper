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

    // Giữ tham chiếu Callback để tránh bị Garbage Collector dọn mất
    private ClientCallback myCallback;

    @FXML
    public void initialize() {
        UserDTO me = SessionStore.currentUser;
        if (me != null) {
            myDisplayName.setText(me.getDisplayName());

            // 1. Khởi động P2P Server (Lắng nghe tin nhắn trực tiếp)
            startP2P();

            // 2. Tải danh sách bạn bè/nhóm lần đầu
            loadFriendListInitial();

            // 3. Đăng ký nhận thông báo Real-time (Cơ chế Callback)
            registerRealTimeUpdates();
        }

        // Sự kiện chọn bạn để chat
        conversationList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) switchChat(newVal);
        });
    }

    // --- CƠ CHẾ REAL-TIME: SERVER GỌI VỀ CLIENT ---
    private void registerRealTimeUpdates() {
        try {
            // Định nghĩa hành động khi Server báo tin: Cập nhật giao diện
            myCallback = new ClientCallback() {
                @Override
                public void onFriendStatusChange(UserDTO friend) throws RemoteException {
                    // Cập nhật UI bắt buộc phải chạy trên luồng JavaFX
                    Platform.runLater(() -> updateFriendInList(friend));
                }
            };

            // Export object này ra cổng ngẫu nhiên (0) để Server có thể gọi tới
            UnicastRemoteObject.exportObject(myCallback, 0);

            // Gửi "cái loa" này lên Server
            RmiClient.getAuthService().registerNotification(SessionStore.currentUser.getId(), myCallback);
            System.out.println("Đã đăng ký nhận thông báo Real-time với Server.");

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Lỗi đăng ký Real-time: " + e.getMessage());
        }
    }

    // Hàm cập nhật danh sách thông minh (Không load lại toàn bộ gây lag)
    private void updateFriendInList(UserDTO updatedFriend) {
        boolean found = false;

        // 1. Tìm xem người này (hoặc nhóm này) có trong list chưa
        for (UserDTO u : conversationList.getItems()) {
            if (u.getId() == updatedFriend.getId()) {
                // Có rồi -> Cập nhật trạng thái
                u.setOnline(updatedFriend.isOnline());
                u.setLastIp(updatedFriend.getLastIp());
                u.setLastPort(updatedFriend.getLastPort());
                // Nếu là nhóm thì có thể cập nhật tên nhóm nếu đổi (tùy logic Server)
                found = true;
                break;
            }
        }

        // 2. Nếu chưa có -> Thêm vào ĐẦU danh sách
        if (!found) {
            conversationList.getItems().add(0, updatedFriend);
        }

        // 3. Refresh lại ListView để hiển thị (chấm xanh/xám)
        conversationList.refresh();
    }

    // --- CÁC CHỨC NĂNG CHÍNH ---

    private void loadFriendListInitial() {
        new Thread(() -> {
            try {
                // Gọi RMI lấy danh sách bạn bè và nhóm
                List<UserDTO> friends = RmiClient.getChatService().getFriendList(SessionStore.currentUser.getId());

                Platform.runLater(() -> {
                    conversationList.getItems().clear();
                    conversationList.getItems().addAll(friends);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void startP2P() {
        p2pClient = new P2PClient(SessionStore.p2pPort, this::onMessageReceived);
        p2pClient.start();
    }

    private void switchChat(UserDTO friend) {
        this.currentChatUser = friend;
        welcomeArea.setVisible(false);
        chatArea.setVisible(true);
        currentChatTitle.setText(friend.getDisplayName());
        msgContainer.getChildren().clear();

        loadHistory();
    }

    private void loadHistory() {
        new Thread(() -> {
            try {
                // Tạm thời fix conversation ID = 1 nếu chưa xử lý logic chọn conversation chuẩn
                // Trong thực tế: conversationId nên được lấy từ UserDTO (nếu là nhóm) hoặc tìm trong DB (nếu là chat đôi)
                // Ở đây ta giả định currentChatUser.getId() chính là conversationId cho Nhóm,
                // còn Chat đôi thì cần logic getConversationId(userId1, userId2).
                // ĐỂ TEST ĐƠN GIẢN: Ta dùng luôn id của bạn bè làm conversationId tạm thời.
                long conversationId = currentChatUser.getId();

                List<MessageDTO> history = RmiClient.getChatService().getHistory(conversationId);

                Platform.runLater(() -> {
                    for (MessageDTO msg : history) {
                        boolean isMe = msg.getSenderId() == SessionStore.currentUser.getId();
                        addMessageBubble(msg.getContent(), isMe);
                    }
                    msgScrollPane.setVvalue(1.0); // Cuộn xuống cuối
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    @FXML
    public void handleCreateGroup() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/create-group.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("Tạo Nhóm");
            stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML
    public void handleSend() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || currentChatUser == null) return;

        MessageDTO msg = new MessageDTO();
        msg.setSenderId(SessionStore.currentUser.getId());
        msg.setContent(text);
        msg.setCreatedAt(LocalDateTime.now());
        msg.setSenderName(SessionStore.currentUser.getDisplayName());

        // Quan trọng: Gán ID hội thoại
        msg.setConversationId(currentChatUser.getId());

        // Kiểm tra xem đang gửi cho NHÓM hay NGƯỜI
        // (Server trả về nhóm với username="GROUP")
        boolean isGroup = "GROUP".equals(currentChatUser.getUsername());

        // 1. GỬI P2P (HYBRID)
        new Thread(() -> {
            try {
                if (isGroup) {
                    // -- CHAT NHÓM --
                    // Lấy danh sách thành viên
                    List<Long> memberIds = RmiClient.getChatService().getGroupMemberIds(currentChatUser.getId());

                    for (Long memId : memberIds) {
                        if (memId == SessionStore.currentUser.getId()) continue; // Bỏ qua chính mình

                        // Lấy IP/Port từng thành viên
                        UserDTO memInfo = RmiClient.getChatService().getUserInfo(memId);
                        if (memInfo != null && memInfo.isOnline()) {
                            p2pClient.send(memInfo.getLastIp(), memInfo.getLastPort(), msg);
                        }
                    }
                } else {
                    // -- CHAT 1-1 --
                    // Lấy thông tin mới nhất của bạn (phòng khi đổi IP)
                    UserDTO target = RmiClient.getChatService().getUserInfo(currentChatUser.getId());
                    if (target != null && target.isOnline()) {
                        p2pClient.send(target.getLastIp(), target.getLastPort(), msg);
                    } else {
                        System.out.println("Bạn đang offline, tin nhắn sẽ lưu trên Server.");
                    }
                }

                // 2. LƯU LỊCH SỬ VÀO SERVER
                RmiClient.getChatService().saveMessage(msg);

            } catch (Exception e) { e.printStackTrace(); }
        }).start();

        // 3. HIỆN UI MÌNH
        addMessageBubble(text, true);
        inputField.clear();
    }

    // Callback nhận tin P2P
    public void onMessageReceived(MessageDTO msg) {
        // Chỉ hiện tin nhắn nếu đang mở đúng cửa sổ chat đó
        // (Logic kiểm tra ID này cần tinh chỉnh tùy thuộc vào bạn dùng ConversationID hay UserID)
        // Tạm thời hiện luôn để test
        Platform.runLater(() -> addMessageBubble(msg.getContent(), false));
    }

    private void addMessageBubble(String text, boolean isMe) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(350);

        // CSS Style trực tiếp (hoặc dùng styleClass nếu đã định nghĩa trong css)
        label.setStyle(isMe
                ? "-fx-background-color: #0084ff; -fx-text-fill: white; -fx-background-radius: 15; -fx-padding: 8 12;"
                : "-fx-background-color: #f0f0f0; -fx-text-fill: black; -fx-background-radius: 15; -fx-padding: 8 12;");

        HBox container = new HBox(label);
        container.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        msgContainer.getChildren().add(container);
        // Tự động cuộn xuống
        Platform.runLater(() -> msgScrollPane.setVvalue(1.0));
    }
}