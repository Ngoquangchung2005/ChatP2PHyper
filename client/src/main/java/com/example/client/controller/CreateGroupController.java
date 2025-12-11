package com.example.client.controller;

import com.example.client.net.RmiClient;
import com.example.client.store.SessionStore;
import com.example.common.dto.UserDTO;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class CreateGroupController {
    @FXML private TextField groupNameField;
    @FXML private ListView<UserDTO> friendCheckList;
    @FXML private ImageView groupAvatarView; // [MỚI] Ảnh đại diện nhóm

    private File selectedFile; // [MỚI] Biến lưu file ảnh đã chọn

    @FXML
    public void initialize() {
        // [LOGIC CŨ] Giữ nguyên cách setup list view
        friendCheckList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        friendCheckList.setCellFactory(param -> new CheckableFriendListCell());
        loadFriends();
    }

    // [LOGIC CŨ] Giữ nguyên hàm load bạn bè
    private void loadFriends() {
        new Thread(() -> {
            try {
                List<UserDTO> all = RmiClient.getFriendService().getFriendList(SessionStore.currentUser.getId());
                // Lọc bỏ nhóm, chỉ lấy người
                List<UserDTO> onlyFriends = all.stream()
                        .filter(u -> !"GROUP".equals(u.getUsername()))
                        .toList();

                Platform.runLater(() -> {
                    friendCheckList.getItems().addAll(onlyFriends);
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    // --- [MỚI] HÀM XỬ LÝ CHỌN ẢNH TỪ MÁY TÍNH ---
    @FXML
    public void handleChooseImage() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Ảnh", "*.jpg", "*.png", "*.jpeg"));
        File file = fc.showOpenDialog(groupNameField.getScene().getWindow());

        if (file != null) {
            this.selectedFile = file;
            try {
                // Hiển thị ảnh xem trước (Preview)
                Image img = new Image(file.toURI().toString());
                groupAvatarView.setImage(img);

                // Bo tròn ảnh cho đẹp
                double r = groupAvatarView.getFitWidth() / 2;
                Circle clip = new Circle(r, r, r);
                groupAvatarView.setClip(clip);

            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    @FXML
    public void handleCreate() {
        // [LOGIC CŨ] Validate dữ liệu đầu vào
        String name = groupNameField.getText().trim();
        List<UserDTO> selected = friendCheckList.getSelectionModel().getSelectedItems();

        if (name.isEmpty()) {
            showAlert("Vui lòng nhập tên nhóm!");
            return;
        }
        if (selected.isEmpty()) {
            showAlert("Vui lòng chọn ít nhất 1 thành viên!");
            return;
        }

        // Tạo danh sách ID thành viên
        List<Long> memberIds = new ArrayList<>();
        memberIds.add(SessionStore.currentUser.getId()); // Admin (mình) luôn ở đầu
        for (UserDTO u : selected) memberIds.add(u.getId());

        new Thread(() -> {
            try {
                // --- [MỚI] BƯỚC 1: UPLOAD ẢNH (NẾU CÓ) ---
                String avatarUrl = null;
                if (selectedFile != null) {
                    byte[] fileData = Files.readAllBytes(selectedFile.toPath());
                    // Gọi MessageService để upload file và nhận về đường dẫn
                    avatarUrl = RmiClient.getMessageService().uploadFile(fileData, selectedFile.getName());
                }

                // --- [CẬP NHẬT] BƯỚC 2: GỌI SERVER TẠO NHÓM (TRUYỀN THÊM AVATAR_URL) ---
                // Lưu ý: Bạn cần chắc chắn hàm createGroup bên Common và Server đã thêm tham số thứ 3 là String avatarUrl
                long groupId = RmiClient.getGroupService().createGroup(name, memberIds, avatarUrl);

                Platform.runLater(() -> {
                    if (groupId > 0) {
                        closeWindow();
                        // [LOGIC CŨ] Thông báo thành công (Có thể dùng AlertType.INFORMATION thay vì WARNING cho đúng nghĩa)
                        Alert a = new Alert(Alert.AlertType.INFORMATION, "Tạo nhóm thành công!");
                        a.show();
                    } else {
                        showAlert("Lỗi tạo nhóm!");
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> showAlert("Lỗi kết nối: " + e.getMessage()));
            }
        }).start();
    }

    // [LOGIC CŨ] Các hàm tiện ích giữ nguyên
    @FXML void handleCancel() { closeWindow(); }

    private void closeWindow() {
        Stage stage = (Stage) groupNameField.getScene().getWindow();
        stage.close();
    }

    private void showAlert(String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setContentText(msg);
        a.show();
    }
}