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
import javafx.scene.layout.StackPane;
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
            setStyle("-fx-background-color: transparent;");
        } else {
            HBox hbox = new HBox(15);
            hbox.setAlignment(Pos.CENTER_LEFT);
            hbox.setPrefHeight(65);

            // --- AVATAR ---
            StackPane avatarStack = new StackPane();
            // Nền xám nhạt
            Circle placeholder = new Circle(25, Color.web("#e0e0e0"));
            avatarStack.getChildren().add(placeholder);

            ImageView avatarView = new ImageView();
            avatarView.setFitWidth(50);
            avatarView.setFitHeight(50);
            avatarView.setPreserveRatio(false);
            avatarView.setSmooth(true);
            Circle clip = new Circle(25, 25, 25);
            avatarView.setClip(clip);

            if (item.getAvatarUrl() != null && !item.getAvatarUrl().isEmpty()) {
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
            avatarStack.getChildren().add(avatarView);

            if (item.isOnline()) {
                Circle onlineDot = new Circle(7, Color.web("#31a24c"));
                // Viền trắng
                onlineDot.setStroke(Color.WHITE);
                onlineDot.setStrokeWidth(2.5);
                StackPane.setAlignment(onlineDot, Pos.BOTTOM_RIGHT);
                avatarStack.getChildren().add(onlineDot);
            }

            // --- INFO ---
            VBox infoBox = new VBox(4);
            infoBox.setAlignment(Pos.CENTER_LEFT);

            Label nameLabel = new Label(item.getDisplayName());
            // [QUAN TRỌNG] Tên màu đen (#333)
            nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #333;");

            String subText;
            String styleText;

            if (item.isOnline()) {
                if (item.getStatusMsg() != null && !item.getStatusMsg().isEmpty()) {
                    subText = item.getStatusMsg();
                } else {
                    subText = "Đang hoạt động";
                }
                styleText = "-fx-text-fill: #31a24c; -fx-font-size: 13px;";
            } else {
                subText = "Offline";
                // [QUAN TRỌNG] Offline màu xám (#666)
                styleText = "-fx-text-fill: #666; -fx-font-size: 13px;";
            }

            if (subText.length() > 25) subText = subText.substring(0, 22) + "...";

            Label statusLabel = new Label(subText);
            statusLabel.setStyle(styleText);

            infoBox.getChildren().addAll(nameLabel, statusLabel);

            // --- BADGE ---
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox rightBox = new HBox();
            rightBox.setAlignment(Pos.CENTER_RIGHT);

            if (item.getUnreadCount() > 0) {
                Label unreadLabel = new Label(String.valueOf(item.getUnreadCount()));
                unreadLabel.getStyleClass().add("unread-badge");
                rightBox.getChildren().add(unreadLabel);
                // Status đậm màu đen
                statusLabel.setStyle("-fx-text-fill: #333; -fx-font-weight: bold; -fx-font-size: 13px;");
            }

            hbox.getChildren().addAll(avatarStack, infoBox, spacer, rightBox);
            setGraphic(hbox);
            setText(null);
        }
    }
}