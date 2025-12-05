package com.example.client.controller;

import com.example.client.net.RmiClient;
import com.example.client.store.SessionStore;
import com.example.common.dto.UserDTO;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.util.List;

public class FriendRequestController {
    @FXML private ListView<UserDTO> requestList;
    private MainController mainController;

    // Hàm để MainController truyền chính nó vào đây
    public void setMainController(MainController mc) {
        this.mainController = mc;
    }

    @FXML
    public void initialize() {
        requestList.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(UserDTO item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    HBox box = new HBox(10);
                    Label name = new Label(item.getDisplayName());
                    Button btnAccept = new Button("Chấp nhận");
                    btnAccept.setOnAction(e -> accept(item));
                    box.getChildren().addAll(name, btnAccept);
                    setGraphic(box);
                }
            }
        });
        loadRequests();
    }

    private void loadRequests() {
        new Thread(() -> {
            try {
                // Gọi FriendService
                List<UserDTO> list = RmiClient.getFriendService().getPendingRequests(SessionStore.currentUser.getId());
                Platform.runLater(() -> requestList.getItems().setAll(list));
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void accept(UserDTO sender) {
        new Thread(() -> {
            try {
                // Gọi FriendService
                boolean ok = RmiClient.getFriendService().acceptFriendRequest(SessionStore.currentUser.getId(), sender.getId());
                Platform.runLater(() -> {
                    if (ok) {
                        requestList.getItems().remove(sender);

                        // CẬP NHẬT LIST CHÍNH NGAY LẬP TỨC MÀ KHÔNG CẦN RELOAD
                        if (mainController != null) {
                            sender.setOnline(true);
                            mainController.addFriendToListDirectly(sender);
                        }

                        Alert a = new Alert(Alert.AlertType.INFORMATION, "Đã chấp nhận kết bạn!");
                        a.show();
                    }
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    @FXML void handleClose() {
        ((Stage) requestList.getScene().getWindow()).close();
    }
}