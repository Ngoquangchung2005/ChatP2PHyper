package com.example.client.controller;

import com.example.client.net.RmiClient;
import com.example.client.store.SessionStore;
import com.example.common.dto.UserDTO;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.util.List;

public class AddFriendController {
    @FXML private TextField searchField;
    @FXML private ListView<UserDTO> resultList;

    @FXML
    public void initialize() {
        resultList.setCellFactory(new Callback<>() {
            @Override
            public ListCell<UserDTO> call(ListView<UserDTO> param) {
                return new ListCell<>() {
                    @Override
                    protected void updateItem(UserDTO item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setGraphic(null);
                        } else {
                            HBox box = new HBox(10);
                            Label nameLabel = new Label(item.getDisplayName() + " (" + item.getUsername() + ")");
                            Button addButton = new Button("Kết bạn");
                            addButton.setOnAction(e -> addFriend(item));
                            box.getChildren().addAll(nameLabel, addButton);
                            setGraphic(box);
                        }
                    }
                };
            }
        });
    }

    @FXML
    public void handleSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) return;

        new Thread(() -> {
            try {
                // SỬA: Dùng FriendService để tìm kiếm
                List<UserDTO> results = RmiClient.getFriendService().searchUsers(query);

                long myId = SessionStore.currentUser.getId();
                results.removeIf(u -> u.getId() == myId);

                javafx.application.Platform.runLater(() -> {
                    resultList.getItems().clear();
                    resultList.getItems().addAll(results);
                    if (results.isEmpty()) {
                        Alert a = new Alert(Alert.AlertType.INFORMATION, "Không tìm thấy ai!");
                        a.show();
                    }
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void addFriend(UserDTO target) {
        new Thread(() -> {
            try {
                long myId = SessionStore.currentUser.getId();
                // SỬA: Dùng FriendService để kết bạn
                boolean ok = RmiClient.getFriendService().addFriend(myId, target.getId());

                javafx.application.Platform.runLater(() -> {
                    if (ok) {
                        Alert a = new Alert(Alert.AlertType.INFORMATION, "Đã kết bạn với " + target.getDisplayName());
                        a.show();
                        resultList.getItems().remove(target);
                    } else {
                        Alert a = new Alert(Alert.AlertType.ERROR, "Lỗi hoặc đã là bạn bè rồi!");
                        a.show();
                    }
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    @FXML void handleClose() {
        ((Stage) searchField.getScene().getWindow()).close();
    }
}