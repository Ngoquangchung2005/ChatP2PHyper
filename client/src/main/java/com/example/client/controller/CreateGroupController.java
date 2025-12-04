package com.example.client.controller;

import com.example.client.net.RmiClient;
import com.example.client.store.SessionStore;
import com.example.common.dto.UserDTO;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class CreateGroupController {
    @FXML private TextField groupNameField;
    @FXML private ListView<UserDTO> friendCheckList;

    @FXML
    public void initialize() {
        // Load danh sách bạn bè để chọn (Lấy từ MainController hoặc gọi lại RMI)
        friendCheckList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        // --- THÊM ĐOẠN NÀY: Tùy biến giao diện dòng để có CheckBox ---
        friendCheckList.setCellFactory(param -> new FriendListCell());
        // -------------------------------------------------------------
        loadFriends();
    }


    private void loadFriends() {
        new Thread(() -> {
            try {
                // Lấy list bạn bè (lọc bỏ các nhóm)
                List<UserDTO> all = RmiClient.getChatService().getFriendList(SessionStore.currentUser.getId());
                List<UserDTO> onlyFriends = all.stream()
                        .filter(u -> !"GROUP".equals(u.getUsername()))
                        .toList();

                javafx.application.Platform.runLater(() -> {
                    friendCheckList.getItems().addAll(onlyFriends);
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    @FXML
    public void handleCreate() {
        String name = groupNameField.getText();
        List<UserDTO> selected = friendCheckList.getSelectionModel().getSelectedItems();

        if (name.isEmpty() || selected.isEmpty()) {
            showAlert("Vui lòng nhập tên nhóm và chọn ít nhất 1 thành viên!");
            return;
        }

        List<Long> memberIds = new ArrayList<>();
        memberIds.add(SessionStore.currentUser.getId()); // Thêm chính mình vào nhóm
        for (UserDTO u : selected) memberIds.add(u.getId());

        new Thread(() -> {
            try {
                long groupId = RmiClient.getGroupService().createGroup(name, memberIds);
                javafx.application.Platform.runLater(() -> {
                    if (groupId > 0) {
                        closeWindow();
                        System.out.println("Tạo nhóm thành công ID: " + groupId);
                    } else {
                        showAlert("Lỗi tạo nhóm!");
                    }
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

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