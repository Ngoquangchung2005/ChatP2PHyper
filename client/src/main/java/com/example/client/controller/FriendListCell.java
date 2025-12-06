package com.example.client.controller;

import com.example.client.net.RmiClient;
import com.example.common.dto.UserDTO;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.io.ByteArrayInputStream;

public class FriendListCell extends ListCell<UserDTO> {

    @Override
    protected void updateItem(UserDTO item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setGraphic(null);
            setText(null);
        } else {
            HBox hbox = new HBox(10);
            hbox.setAlignment(Pos.CENTER_LEFT);

            // 1. Avatar (Hình tròn)
            ImageView avatarView = new ImageView();
            avatarView.setFitWidth(40);
            avatarView.setFitHeight(40);
            // Tạo khung tròn cho ảnh
            Circle clip = new Circle(20, 20, 20);
            avatarView.setClip(clip);

            // Load ảnh mặc định trước
            // (Bạn nên có file user.png trong resources, nếu không thì dùng màu nền)
            avatarView.setImage(null);
            avatarView.setStyle("-fx-background-color: #ccc;");

            // Tải ảnh từ Server nếu có URL
            if (item.getAvatarUrl() != null) {
                new Thread(() -> {
                    try {
                        // Tải file avatar về (Dùng hàm downloadFile của MessageService)
                        byte[] data = RmiClient.getMessageService().downloadFile(item.getAvatarUrl());
                        if (data != null) {
                            Image img = new Image(new ByteArrayInputStream(data));
                            Platform.runLater(() -> avatarView.setImage(img));
                        }
                    } catch (Exception e) {}
                }).start();
            }

            // 2. Thông tin (Tên + Status)
            VBox infoBox = new VBox(2);
            Label nameLabel = new Label(item.getDisplayName());
            nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

            String status = item.getStatusMsg() != null ? item.getStatusMsg() : (item.isOnline() ? "Online" : "Offline");
            Label statusLabel = new Label(status);
            statusLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 11px;");

            infoBox.getChildren().addAll(nameLabel, statusLabel);

            // 3. Dấu chấm Online/Offline
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Circle statusDot = new Circle(5);
            statusDot.setFill(item.isOnline() ? Color.LIMEGREEN : Color.GRAY);

            // 4. Badge tin nhắn chưa đọc
            Label unreadLabel = null;
            if (item.getUnreadCount() > 0) {
                unreadLabel = new Label(String.valueOf(item.getUnreadCount()));
                unreadLabel.setStyle("-fx-background-color: red; -fx-text-fill: white; -fx-background-radius: 10; -fx-padding: 0 5; -fx-font-size: 10px;");
            }

            hbox.getChildren().addAll(avatarView, infoBox, spacer);
            if (unreadLabel != null) hbox.getChildren().add(unreadLabel);
            hbox.getChildren().add(statusDot);

            setGraphic(hbox);
        }
    }
}