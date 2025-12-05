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
        friendCheckList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        // SỬA DÒNG NÀY: Dùng CheckableFriendListCell
        friendCheckList.setCellFactory(param -> new CheckableFriendListCell());
        loadFriends();
    }

    private void loadFriends() {
        new Thread(() -> {
            try {
                // SỬA: Dùng FriendService để lấy danh sách bạn bè
                List<UserDTO> all = RmiClient.getFriendService().getFriendList(SessionStore.currentUser.getId());

                // Lọc bỏ nhóm (chỉ lấy người)
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
            showAlert("Vui lòng nhập tên nhóm và chọn thành viên!");
            return;
        }

        List<Long> memberIds = new ArrayList<>();
        memberIds.add(SessionStore.currentUser.getId());
        for (UserDTO u : selected) memberIds.add(u.getId());

        new Thread(() -> {
            try {
                // Gọi GroupService để tạo nhóm (giữ nguyên)
                long groupId = RmiClient.getGroupService().createGroup(name, memberIds);
                javafx.application.Platform.runLater(() -> {
                    if (groupId > 0) {
                        closeWindow();
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