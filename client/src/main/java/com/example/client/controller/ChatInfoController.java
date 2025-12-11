package com.example.client.controller;

import com.example.client.net.RmiClient;
import com.example.client.store.SessionStore;
import com.example.common.dto.MessageDTO;
import com.example.common.dto.UserDTO;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;

public class ChatInfoController {
    @FXML private ImageView avatarView;
    @FXML private Label nameLabel;
    @FXML private Button leaveGroupBtn;

    // [MỚI] UI Thành viên & Admin
    @FXML private Button addMemberBtn;
    @FXML private ListView<UserDTO> memberListView;
    @FXML private Button editGroupBtn;      // Nút sửa tên
    @FXML private Button dissolveGroupBtn;  // Nút giải tán

    // [MỚI] Biến trạng thái Admin
    private boolean amIAdmin = false;

    private MainController mainController;
    private UserDTO currentUser; // Đây là UserDTO của NHÓM CHAT (hoặc người đang chat)

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    public void setUserInfo(UserDTO groupOrUser) {
        this.currentUser = groupOrUser;
        if (currentUser == null) return;

        nameLabel.setText(currentUser.getDisplayName());
        loadAvatar(currentUser.getAvatarUrl());

        // Nếu là NHÓM thì hiện các chức năng nhóm
        if ("GROUP".equals(currentUser.getUsername())) {
            leaveGroupBtn.setVisible(true);
            leaveGroupBtn.setManaged(true);
            addMemberBtn.setVisible(true);
            addMemberBtn.setManaged(true);
            memberListView.setVisible(true);

            // [THAY ĐỔI] Thay vì load thẳng, ta check Admin trước rồi mới load list
            checkAdminStatus();
        } else {
            // Chat 1-1 thì ẩn đi
            leaveGroupBtn.setVisible(false);
            leaveGroupBtn.setManaged(false);
            addMemberBtn.setVisible(false);
            addMemberBtn.setManaged(false);
            memberListView.setVisible(false);

            // Ẩn nút admin nếu có
            if(editGroupBtn != null) { editGroupBtn.setVisible(false); editGroupBtn.setManaged(false); }
            if(dissolveGroupBtn != null) { dissolveGroupBtn.setVisible(false); dissolveGroupBtn.setManaged(false); }
        }
    }

    private void loadAvatar(String url) {
        avatarView.setImage(null);
        if (url != null) {
            new Thread(() -> {
                try {
                    byte[] data = RmiClient.getMessageService().downloadFile(url);
                    if (data != null) {
                        Image img = new Image(new ByteArrayInputStream(data));
                        Platform.runLater(() -> {
                            avatarView.setImage(img);
                            double r = avatarView.getFitWidth() / 2;
                            Circle clip = new Circle(r, r, r);
                            avatarView.setClip(clip);
                        });
                    }
                } catch (Exception e) {}
            }).start();
        }
    }

    // --- [LOGIC MỚI] CHECK ADMIN STATUS ---
    private void checkAdminStatus() {
        new Thread(() -> {
            try {
                // Lấy danh sách thành viên để biết mình có phải admin không
                List<UserDTO> members = RmiClient.getGroupService().getGroupMembers(currentUser.getId());

                long myId = SessionStore.currentUser.getId();
                UserDTO meInGroup = members.stream().filter(u -> u.getId() == myId).findFirst().orElse(null);

                if (meInGroup != null && meInGroup.isAdmin()) {
                    amIAdmin = true;
                } else {
                    amIAdmin = false;
                }

                Platform.runLater(() -> {
                    // Update UI dựa trên quyền Admin (hiển thị nút Kick, Sửa, Xóa...)
                    updateAdminUI(members);
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    // --- [LOGIC MỚI] CẬP NHẬT UI DỰA TRÊN QUYỀN ADMIN ---
    private void updateAdminUI(List<UserDTO> members) {
        // Cập nhật List View
        memberListView.getItems().setAll(members);

        memberListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(UserDTO item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    HBox box = new HBox(10);

                    String role = item.isAdmin() ? " (Trưởng nhóm)" : "";
                    Label name = new Label(item.getDisplayName() + role);
                    if (item.isAdmin()) name.setStyle("-fx-font-weight: bold; -fx-text-fill: #0084ff;");

                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);

                    box.getChildren().add(name);
                    box.getChildren().add(spacer);

                    // Logic nút Kick: Chỉ hiện nếu MÌNH LÀ ADMIN và ITEM KHÔNG PHẢI LÀ MÌNH
                    if (amIAdmin && item.getId() != SessionStore.currentUser.getId()) {
                        Button kickBtn = new Button("❌");
                        kickBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: red; -fx-cursor: hand;");
                        kickBtn.setOnAction(e -> handleKickMember(item));
                        box.getChildren().add(kickBtn);
                    }

                    if (item.getId() == SessionStore.currentUser.getId()) {
                        name.setText(name.getText() + " (Bạn)");
                        if (!item.isAdmin()) name.setStyle("-fx-font-weight: bold;");
                    }

                    setGraphic(box);
                }
            }
        });

        // Hiện/Ẩn nút chức năng Admin
        if (editGroupBtn != null) {
            editGroupBtn.setVisible(amIAdmin);
            editGroupBtn.setManaged(amIAdmin);
        }
        if (dissolveGroupBtn != null) {
            dissolveGroupBtn.setVisible(amIAdmin);
            dissolveGroupBtn.setManaged(amIAdmin);
        }
    }

    // --- XỬ LÝ KICK THÀNH VIÊN ---
    private void handleKickMember(UserDTO target) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Mời " + target.getDisplayName() + " ra khỏi nhóm?");
        alert.showAndWait().ifPresent(type -> {
            if (type == ButtonType.OK) {
                new Thread(() -> {
                    try {
                        // Gọi hàm remove mới (3 tham số)
                        boolean ok = RmiClient.getGroupService().removeMemberFromGroup(
                                SessionStore.currentUser.getId(),
                                currentUser.getId(),
                                target.getId()
                        );
                        if (ok) {
                            sendSystemNotification("đã mời " + target.getDisplayName() + " ra khỏi nhóm.");
                            checkAdminStatus(); // Load lại list
                        } else {
                            Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Không thể mời thành viên này!").show());
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                }).start();
            }
        });
    }

    // --- XỬ LÝ THÊM THÀNH VIÊN ---
    @FXML
    public void handleAddMember() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Thêm thành viên");
        dialog.setHeaderText("Nhập chính xác Username người cần thêm:");

        dialog.showAndWait().ifPresent(username -> {
            new Thread(() -> {
                try {
                    List<UserDTO> searchResult = RmiClient.getFriendService().searchUsers(username);
                    UserDTO target = searchResult.stream().filter(u -> u.getUsername().equals(username)).findFirst().orElse(null);

                    if (target != null) {
                        boolean ok = RmiClient.getGroupService().addMemberToGroup(currentUser.getId(), target.getId());
                        Platform.runLater(() -> {
                            if (ok) {
                                sendSystemNotification("đã thêm " + target.getDisplayName() + " vào nhóm.");
                                checkAdminStatus(); // Load lại list
                                new Alert(Alert.AlertType.INFORMATION, "Thành công!").show();
                            } else {
                                new Alert(Alert.AlertType.ERROR, "Thất bại! (Có thể đã trong nhóm)").show();
                            }
                        });
                    } else {
                        Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Không tìm thấy user!").show());
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }).start();
        });
    }

    // --- [MỚI] XỬ LÝ SỬA TÊN NHÓM ---
    @FXML
    public void handleEditGroupName() {
        TextInputDialog dialog = new TextInputDialog(currentUser.getDisplayName().replace("[Nhóm] ", ""));
        dialog.setTitle("Đổi tên nhóm");
        dialog.setHeaderText("Nhập tên nhóm mới:");
        dialog.showAndWait().ifPresent(newName -> {
            new Thread(() -> {
                try {
                    boolean ok = RmiClient.getGroupService().updateGroupInfo(
                            SessionStore.currentUser.getId(), currentUser.getId(), newName, null
                    );
                    if (ok) {
                        sendSystemNotification("đã đổi tên nhóm thành: " + newName);
                        Platform.runLater(() -> nameLabel.setText("[Nhóm] " + newName));
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }).start();
        });
    }

    // --- [MỚI] XỬ LÝ ĐỔI ẢNH NHÓM ---
    @FXML
    public void handleChangeGroupAvatar() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Ảnh", "*.jpg", "*.png"));
        File file = fc.showOpenDialog(null);
        if (file != null) {
            new Thread(() -> {
                try {
                    byte[] fileData = Files.readAllBytes(file.toPath());
                    String serverPath = RmiClient.getMessageService().uploadFile(fileData, file.getName());

                    boolean ok = RmiClient.getGroupService().updateGroupInfo(
                            SessionStore.currentUser.getId(), currentUser.getId(), null, serverPath
                    );

                    if (ok) {
                        sendSystemNotification("đã thay đổi ảnh nhóm.");
                        loadAvatar(serverPath);
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }).start();
        }
    }

    // --- [MỚI] XỬ LÝ GIẢI TÁN NHÓM ---
    @FXML
    public void handleDissolveGroup() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Bạn có chắc muốn giải tán nhóm? Hành động này không thể hoàn tác.");
        alert.showAndWait().ifPresent(type -> {
            if (type == ButtonType.OK) {
                new Thread(() -> {
                    try {
                        sendSystemNotification("đã giải tán nhóm.");
                        boolean ok = RmiClient.getGroupService().dissolveGroup(SessionStore.currentUser.getId(), currentUser.getId());

                        Platform.runLater(() -> {
                            if (ok) {
                                mainController.handleGroupLeft(currentUser.getId());
                            }
                        });
                    } catch (Exception e) { e.printStackTrace(); }
                }).start();
            }
        });
    }

    // Hàm gửi tin nhắn hệ thống (NOTIFICATION)
    private void sendSystemNotification(String actionText) {
        if (mainController == null) return;
        MessageDTO msg = new MessageDTO();
        msg.setType(MessageDTO.MessageType.NOTIFICATION);
        msg.setSenderId(SessionStore.currentUser.getId());
        msg.setConversationId(currentUser.getId());
        msg.setCreatedAt(LocalDateTime.now());
        msg.setContent(SessionStore.currentUser.getDisplayName() + " " + actionText);

        mainController.getChatManager().sendP2PMessage(msg);
    }

    @FXML
    public void handleLeaveGroup() {
        if (currentUser == null) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Rời nhóm");
        alert.setHeaderText("Bạn có chắc muốn rời khỏi nhóm này?");
        alert.setContentText("Bạn sẽ không nhận được tin nhắn từ nhóm này nữa.");

        alert.showAndWait().ifPresent(type -> {
            if (type == ButtonType.OK) {
                new Thread(() -> {
                    try {
                        long myId = SessionStore.currentUser.getId();
                        long groupId = currentUser.getId();

                        sendSystemNotification("đã rời khỏi nhóm.");

                        boolean ok = RmiClient.getGroupService().leaveGroup(myId, groupId);

                        Platform.runLater(() -> {
                            if (ok) {
                                if (mainController != null) {
                                    mainController.handleGroupLeft(groupId);
                                }
                            } else {
                                new Alert(Alert.AlertType.ERROR, "Lỗi: Không thể rời nhóm!").show();
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
            }
        });
    }
}