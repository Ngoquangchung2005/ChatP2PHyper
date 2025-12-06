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
import java.util.HashMap;
import java.util.Map;

public class FriendListCell extends ListCell<UserDTO> {

    // [TỐI ƯU] Cache để lưu ảnh đã tải, tránh tải lại khi cuộn
    private static final Map<String, Image> avatarCache = new HashMap<>();

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

            // --- 1. AVATAR ---
            StackPane avatarStack = new StackPane();
            // Nền xám nhạt (placeholder)
            Circle placeholder = new Circle(25, Color.web("#e0e0e0"));
            avatarStack.getChildren().add(placeholder);

            ImageView avatarView = new ImageView();
            avatarView.setFitWidth(50);
            avatarView.setFitHeight(50);
            avatarView.setPreserveRatio(false);
            avatarView.setSmooth(true);

            // Bo tròn ảnh
            Circle clip = new Circle(25, 25, 25);
            avatarView.setClip(clip);

            // Logic tải ảnh (có cache)
            if (item.getAvatarUrl() != null && !item.getAvatarUrl().isEmpty()) {
                String url = item.getAvatarUrl();

                if (avatarCache.containsKey(url)) {
                    // Nếu đã có trong cache thì dùng luôn
                    avatarView.setImage(avatarCache.get(url));
                } else {
                    // Nếu chưa có thì tải ngầm
                    new Thread(() -> {
                        try {
                            byte[] data = RmiClient.getMessageService().downloadFile(url);
                            if (data != null) {
                                Image img = new Image(new ByteArrayInputStream(data));
                                // Lưu vào cache
                                avatarCache.put(url, img);

                                Platform.runLater(() -> {
                                    // Kiểm tra lại xem cell này còn giữ đúng user không (tránh hiện nhầm khi cuộn)
                                    if (getItem() != null && getItem().getId() == item.getId()) {
                                        avatarView.setImage(img);
                                    }
                                });
                            }
                        } catch (Exception e) {
                            // Bỏ qua lỗi tải ảnh
                        }
                    }).start();
                }
            }
            avatarStack.getChildren().add(avatarView);

            // Chấm xanh online
            if (item.isOnline()) {
                Circle onlineDot = new Circle(7, Color.web("#31a24c"));
                onlineDot.setStroke(Color.WHITE);
                onlineDot.setStrokeWidth(2.5);
                StackPane.setAlignment(onlineDot, Pos.BOTTOM_RIGHT);
                avatarStack.getChildren().add(onlineDot);
            }

            // --- 2. THÔNG TIN (Tên + Status) ---
            VBox infoBox = new VBox(4);
            infoBox.setAlignment(Pos.CENTER_LEFT);

            Label nameLabel = new Label(item.getDisplayName());
            nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #333;");

            String subText;
            String styleText;

            if (item.isOnline()) {
                subText = (item.getStatusMsg() != null && !item.getStatusMsg().isEmpty())
                        ? item.getStatusMsg() : "Đang hoạt động";
                styleText = "-fx-text-fill: #31a24c; -fx-font-size: 13px;";
            } else {
                subText = "Offline";
                styleText = "-fx-text-fill: #666; -fx-font-size: 13px;";
            }

            // Cắt bớt nếu status quá dài
            if (subText.length() > 25) subText = subText.substring(0, 22) + "...";

            Label statusLabel = new Label(subText);
            statusLabel.setStyle(styleText);

            infoBox.getChildren().addAll(nameLabel, statusLabel);

            // --- 3. BADGE (Số tin nhắn chưa đọc) ---
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox rightBox = new HBox();
            rightBox.setAlignment(Pos.CENTER_RIGHT);

            if (item.getUnreadCount() > 0) {
                Label unreadLabel = new Label(String.valueOf(item.getUnreadCount()));
                unreadLabel.getStyleClass().add("unread-badge"); // Style trong css
                rightBox.getChildren().add(unreadLabel);

                // Khi có tin nhắn mới, làm đậm status
                statusLabel.setStyle("-fx-text-fill: #333; -fx-font-weight: bold; -fx-font-size: 13px;");
            }

            hbox.getChildren().addAll(avatarStack, infoBox, spacer, rightBox);
            setGraphic(hbox);
            setText(null);
        }
    }
}