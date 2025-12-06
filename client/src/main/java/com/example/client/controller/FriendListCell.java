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
            setStyle("-fx-background-color: transparent;"); // Giữ nền trong suốt
        } else {
            HBox hbox = new HBox(12);
            hbox.setAlignment(Pos.CENTER_LEFT);
            hbox.setPrefHeight(60);

            // 1. Avatar (Hình tròn)
            ImageView avatarView = new ImageView();
            avatarView.setFitWidth(48);
            avatarView.setFitHeight(48);
            Circle clip = new Circle(24, 24, 24);
            avatarView.setClip(clip);

            // Placeholder avatar màu xám
            avatarView.setStyle("-fx-background-color: #555;");

            // Load avatar thật
            if (item.getAvatarUrl() != null) {
                new Thread(() -> {
                    try {
                        byte[] data = RmiClient.getMessageService().downloadFile(item.getAvatarUrl());
                        if (data != null) {
                            Image img = new Image(new ByteArrayInputStream(data));
                            Platform.runLater(() -> avatarView.setImage(img));
                        }
                    } catch (Exception e) {}
                }).start();
            }

            // StackPane để chứa avatar + chấm online (nếu cần thiết kế kỹ hơn)
            // Ở đây để đơn giản ta dùng HBox

            // 2. Thông tin (Tên + Status)
            VBox infoBox = new VBox(4);
            infoBox.setAlignment(Pos.CENTER_LEFT);

            Label nameLabel = new Label(item.getDisplayName());
            nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #e4e6eb;");

            String statusText = item.getStatusMsg() != null && !item.getStatusMsg().isEmpty()
                    ? item.getStatusMsg()
                    : (item.isOnline() ? "Đang hoạt động" : "Offline");

            Label statusLabel = new Label(statusText);
            // Nếu online thì chữ xanh, không thì chữ xám
            if (item.isOnline()) {
                statusLabel.setStyle("-fx-text-fill: #31a24c; -fx-font-size: 12px;"); // Xanh lá
            } else {
                statusLabel.setStyle("-fx-text-fill: #b0b3b8; -fx-font-size: 12px;");
            }

            infoBox.getChildren().addAll(nameLabel, statusLabel);

            // 3. Spacer đẩy nội dung sang phải
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            // 4. Badge tin nhắn chưa đọc (Màu đỏ nổi bật)
            HBox badgeContainer = new HBox();
            if (item.getUnreadCount() > 0) {
                Label unreadLabel = new Label(String.valueOf(item.getUnreadCount()));
                unreadLabel.getStyleClass().add("unread-badge"); // Định nghĩa trong CSS
                badgeContainer.getChildren().add(unreadLabel);
            }

            hbox.getChildren().addAll(avatarView, infoBox, spacer, badgeContainer);

            setGraphic(hbox);
            setText(null);
        }
    }
}