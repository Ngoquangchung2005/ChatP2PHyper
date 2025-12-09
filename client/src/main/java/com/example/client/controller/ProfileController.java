package com.example.client.controller;

import com.example.client.net.RmiClient;
import com.example.client.store.SessionStore;
import com.example.common.dto.UserDTO;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;

public class ProfileController {
    @FXML private ImageView avatarView;
    @FXML private TextField nameField, statusField;
    @FXML private PasswordField oldPassField, newPassField;

    private byte[] newAvatarData = null;
    private String newAvatarExt = null;
    private MainController mainController;
    // 2. [THÊM] Hàm Setter để nhận MainController từ bên ngoài
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    @FXML
    public void initialize() {
        UserDTO me = SessionStore.currentUser;
        nameField.setText(me.getDisplayName());
        statusField.setText(me.getStatusMsg() != null ? me.getStatusMsg() : "");

        // Load avatar hiện tại
        if (me.getAvatarUrl() != null) {
            new Thread(() -> {
                try {
                    byte[] data = RmiClient.getMessageService().downloadFile(me.getAvatarUrl());
                    if (data != null) {
                        Image img = new Image(new ByteArrayInputStream(data));
                        Platform.runLater(() -> {
                            avatarView.setImage(img);

                            // [QUAN TRỌNG] Đảm bảo ảnh luôn tròn khi load
                            double r = avatarView.getFitWidth() / 2;
                            javafx.scene.shape.Circle clip = new javafx.scene.shape.Circle(r, r, r);
                            avatarView.setClip(clip);
                        });
                    }
                } catch (Exception e) {}
            }).start();
        }

        // Khi chọn ảnh mới từ máy (handleChooseAvatar), cũng cần setClip tương tự:
    }
    @FXML
    public void handleChooseAvatar() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Ảnh", "*.png", "*.jpg", "*.jpeg"));
        File file = fc.showOpenDialog(nameField.getScene().getWindow());
        if (file != null) {
            try {
                newAvatarData = Files.readAllBytes(file.toPath());
                newAvatarExt = file.getName().substring(file.getName().lastIndexOf("."));

                Image img = new Image(new ByteArrayInputStream(newAvatarData));
                avatarView.setImage(img);

                // Bo tròn ảnh preview
                double r = avatarView.getFitWidth() / 2;
                javafx.scene.shape.Circle clip = new javafx.scene.shape.Circle(r, r, r);
                avatarView.setClip(clip);

            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    @FXML
    public void handleUpdateInfo() {
        String name = nameField.getText();
        String status = statusField.getText();

        new Thread(() -> {
            try {
                // 1. Gọi Server cập nhật
                boolean ok = RmiClient.getAuthService().updateProfile(
                        SessionStore.currentUser.getId(), name, status, newAvatarData, newAvatarExt
                );

                Platform.runLater(() -> {
                    if (ok) {
                        // --- [SỬA ĐOẠN NÀY] ---

                        // 2. Cập nhật thành công, nhưng ta chưa biết URL ảnh mới là gì.
                        // Hãy gọi Server lấy thông tin mới nhất của chính mình về.
                        new Thread(() -> {
                            try {
                                UserDTO myNewInfo = RmiClient.getDirectoryService().getUserInfo(SessionStore.currentUser.getId());

                                Platform.runLater(() -> {
                                    if (myNewInfo != null) {
                                        // 3. Cập nhật toàn bộ thông tin mới (bao gồm cả Avatar URL mới) vào SessionStore
                                        SessionStore.currentUser = myNewInfo;

                                        // 4. Lúc này mới gọi MainController vẽ lại -> Nó sẽ lấy được ảnh mới
                                        if (mainController != null) {
                                            mainController.refreshProfileUI();
                                        }

                                        showAlert("Thành công", "Đã cập nhật thông tin!");
                                    }
                                });
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }).start();
                        // ----------------------

                    } else {
                        showAlert("Lỗi", "Không thể cập nhật.");
                    }
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }
    @FXML
    public void handleChangePass() {
        String oldP = oldPassField.getText();
        String newP = newPassField.getText();
        if (oldP.isEmpty() || newP.isEmpty()) return;

        new Thread(() -> {
            try {
                boolean ok = RmiClient.getAuthService().changePassword(SessionStore.currentUser.getId(), oldP, newP);
                Platform.runLater(() -> {
                    if (ok) showAlert("Thành công", "Đổi mật khẩu thành công!");
                    else showAlert("Thất bại", "Mật khẩu cũ không đúng.");
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void showAlert(String title, String content) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(content);
        a.show();
    }
}