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
import javafx.scene.layout.VBox; // Nhớ import VBox
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

    // UI Thành viên & Admin
    @FXML private Button addMemberBtn;
    @FXML private ListView<UserDTO> memberListView;
    @FXML private Button editGroupBtn;
    @FXML private Button dissolveGroupBtn;
    @FXML private Button changeAvatarBtn; // [ĐÃ THÊM] Nút đổi ảnh nhóm

    // Hộp chứa nút Trang cá nhân
    @FXML private VBox personalProfileBox;

    private boolean amIAdmin = false;
    private MainController mainController;
    private UserDTO currentUser;

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    public void setUserInfo(UserDTO groupOrUser) {
        this.currentUser = groupOrUser;
        if (currentUser == null) return;

        nameLabel.setText(currentUser.getDisplayName());
        loadAvatar(currentUser.getAvatarUrl());

        if ("GROUP".equals(currentUser.getUsername())) {
            // --- LOGIC CHO NHÓM ---

            // 1. Ẩn nút "Trang cá nhân" (Vì nhóm không có)
            if (personalProfileBox != null) {
                personalProfileBox.setVisible(false);
                personalProfileBox.setManaged(false);
            }

            // 2. Hiện các chức năng nhóm
            leaveGroupBtn.setVisible(true);
            leaveGroupBtn.setManaged(true);
            addMemberBtn.setVisible(true);
            addMemberBtn.setManaged(true);
            memberListView.setVisible(true);

            // Check Admin để hiện nút sửa/xóa
            checkAdminStatus();
        } else {
            // --- LOGIC CHO CHAT 1-1 ---

            // 1. Hiện nút "Trang cá nhân"
            if (personalProfileBox != null) {
                personalProfileBox.setVisible(true);
                personalProfileBox.setManaged(true);
            }

            // 2. Ẩn các chức năng nhóm
            leaveGroupBtn.setVisible(false);
            leaveGroupBtn.setManaged(false);
            addMemberBtn.setVisible(false);
            addMemberBtn.setManaged(false);
            memberListView.setVisible(false);

            // Ẩn nút admin
            if(editGroupBtn != null) { editGroupBtn.setVisible(false); editGroupBtn.setManaged(false); }
            if(dissolveGroupBtn != null) { dissolveGroupBtn.setVisible(false); dissolveGroupBtn.setManaged(false); }
            if(changeAvatarBtn != null) { changeAvatarBtn.setVisible(false); } // [ĐÃ THÊM] Ẩn nút đổi ảnh
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

    private void checkAdminStatus() {
        new Thread(() -> {
            try {
                List<UserDTO> members = RmiClient.getGroupService().getGroupMembers(currentUser.getId());
                long myId = SessionStore.currentUser.getId();
                UserDTO meInGroup = members.stream().filter(u -> u.getId() == myId).findFirst().orElse(null);
                if (meInGroup != null && meInGroup.isAdmin()) {
                    amIAdmin = true;
                } else {
                    amIAdmin = false;
                }
                Platform.runLater(() -> updateAdminUI(members));
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void updateAdminUI(List<UserDTO> members) {
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
                    box.getChildren().addAll(name, spacer);

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

        if (editGroupBtn != null) {
            editGroupBtn.setVisible(amIAdmin);
            editGroupBtn.setManaged(amIAdmin);
        }
        if (dissolveGroupBtn != null) {
            dissolveGroupBtn.setVisible(amIAdmin);
            dissolveGroupBtn.setManaged(amIAdmin);
        }
        // [ĐÃ THÊM] Hiển thị nút đổi ảnh nếu là Admin
        if (changeAvatarBtn != null) {
            changeAvatarBtn.setVisible(amIAdmin);
        }
    }

    private void handleKickMember(UserDTO target) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Mời " + target.getDisplayName() + " ra khỏi nhóm?");
        alert.showAndWait().ifPresent(type -> {
            if (type == ButtonType.OK) {
                new Thread(() -> {
                    try {
                        boolean ok = RmiClient.getGroupService().removeMemberFromGroup(
                                SessionStore.currentUser.getId(),
                                currentUser.getId(),
                                target.getId()
                        );
                        if (ok) {
                            sendSystemNotification("đã mời " + target.getDisplayName() + " ra khỏi nhóm.");
                            checkAdminStatus();
                        } else {
                            Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Không thể mời thành viên này!").show());
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                }).start();
            }
        });
    }

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
                                checkAdminStatus();
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
                    } catch (Exception e) { e.printStackTrace(); }
                }).start();
            }
        });
    }
}