package com.example.client.controller;

import com.example.client.net.RmiClient;
import com.example.client.store.SessionStore;
import com.example.common.dto.UserDTO;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;

import java.io.ByteArrayInputStream;

public class ChatInfoController {
    @FXML private ImageView avatarView;
    @FXML private Label nameLabel;
    @FXML private Button leaveGroupBtn; // Nút mới thêm
    private MainController mainController;
    private UserDTO currentUser;

    // Hàm nhận MainController để gọi ngược lại
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    public void setUserInfo(UserDTO user) {
        if (user == null) return;

        // Set tên
        nameLabel.setText(user.getDisplayName());

        // Reset avatar về mặc định (tránh hiện ảnh người cũ khi mạng lag)
        avatarView.setImage(null);

        // Load avatar mới
        if (user.getAvatarUrl() != null) {
            new Thread(() -> {
                try {
                    byte[] data = RmiClient.getMessageService().downloadFile(user.getAvatarUrl());
                    if (data != null) {
                        Image img = new Image(new ByteArrayInputStream(data));
                        Platform.runLater(() -> {
                            avatarView.setImage(img);

                            // Cắt tròn ảnh
                            double r = avatarView.getFitWidth() / 2;
                            Circle clip = new Circle(r, r, r);
                            avatarView.setClip(clip);
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }// [LOGIC MỚI] Hiển thị nút Rời nhóm nếu đây là Nhóm
        if ("GROUP".equals(user.getUsername())) {
            leaveGroupBtn.setVisible(true);
            leaveGroupBtn.setManaged(true);
        } else {
            leaveGroupBtn.setVisible(false);
            leaveGroupBtn.setManaged(false);
        }
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

                        // 1. Gọi Server
                        boolean ok = RmiClient.getGroupService().leaveGroup(myId, groupId);

                        Platform.runLater(() -> {
                            if (ok) {
                                // 2. Báo cho MainController xử lý giao diện
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