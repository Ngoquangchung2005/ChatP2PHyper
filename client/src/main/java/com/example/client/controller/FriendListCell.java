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
            // Đặt nền trong suốt khi không có dữ liệu để không bị lỗi hiển thị
            setStyle("-fx-background-color: transparent;");
        } else {
            HBox hbox = new HBox(15);
            hbox.setAlignment(Pos.CENTER_LEFT);
            hbox.setPrefHeight(65); // Tăng chiều cao một chút cho thoáng

            // --- 1. AVATAR (Có Placeholder + Chấm Online) ---
            StackPane avatarStack = new StackPane();

            // Lớp nền (Placeholder) hình tròn màu xám - Giúp avatar luôn có hình dáng kể cả khi chưa load ảnh
            Circle placeholder = new Circle(25, Color.web("#3e4042"));
            avatarStack.getChildren().add(placeholder);

            // Ảnh Avatar chính
            ImageView avatarView = new ImageView();
            avatarView.setFitWidth(50);
            avatarView.setFitHeight(50);
            avatarView.setPreserveRatio(false);
            avatarView.setSmooth(true); // Làm mịn ảnh

            // Bo tròn ảnh
            Circle clip = new Circle(25, 25, 25);
            avatarView.setClip(clip);

            // Logic tải ảnh từ Server
            if (item.getAvatarUrl() != null && !item.getAvatarUrl().isEmpty()) {
                new Thread(() -> {
                    try {
                        byte[] data = RmiClient.getMessageService().downloadFile(item.getAvatarUrl());
                        if (data != null) {
                            Image img = new Image(new ByteArrayInputStream(data));
                            Platform.runLater(() -> avatarView.setImage(img));
                        }
                    } catch (Exception e) {
                        // Nếu lỗi thì giữ nguyên placeholder
                    }
                }).start();
            }
            avatarStack.getChildren().add(avatarView);

            // Chấm trạng thái Online (Chỉ hiện nếu user đang online)
            if (item.isOnline()) {
                Circle onlineDot = new Circle(7, Color.web("#31a24c")); // Màu xanh lá
                onlineDot.setStroke(Color.web("#242526")); // Viền trùng màu nền sidebar để tạo hiệu ứng cắt
                onlineDot.setStrokeWidth(2.5);
                StackPane.setAlignment(onlineDot, Pos.BOTTOM_RIGHT);
                avatarStack.getChildren().add(onlineDot);
            }

            // --- 2. THÔNG TIN (Tên + Status) ---
            VBox infoBox = new VBox(4);
            infoBox.setAlignment(Pos.CENTER_LEFT);

            Label nameLabel = new Label(item.getDisplayName());
            nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #e4e6eb;");

            // [LOGIC MỚI] Xử lý hiển thị trạng thái chuẩn xác
            String subText;
            String styleText;

            if (item.isOnline()) {
                // Nếu Online: Ưu tiên status tùy chỉnh, nếu không thì hiện "Đang hoạt động"
                if (item.getStatusMsg() != null && !item.getStatusMsg().isEmpty()) {
                    subText = item.getStatusMsg();
                } else {
                    subText = "Đang hoạt động";
                }
                styleText = "-fx-text-fill: #31a24c; -fx-font-size: 13px;"; // Chữ xanh lá hoặc sáng
            } else {
                // Nếu Offline: Bắt buộc hiện "Offline" (hoặc thời gian truy cập cuối nếu có data)
                subText = "Offline";
                styleText = "-fx-text-fill: #b0b3b8; -fx-font-size: 13px;"; // Chữ xám tối
            }

            // Cắt ngắn nếu status quá dài để không vỡ giao diện
            if (subText.length() > 25) {
                subText = subText.substring(0, 22) + "...";
            }

            Label statusLabel = new Label(subText);
            statusLabel.setStyle(styleText);

            infoBox.getChildren().addAll(nameLabel, statusLabel);

            // --- 3. BADGE TIN NHẮN CHƯA ĐỌC ---
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS); // Đẩy phần còn lại sang phải cùng

            HBox rightBox = new HBox();
            rightBox.setAlignment(Pos.CENTER_RIGHT);

            if (item.getUnreadCount() > 0) {
                Label unreadLabel = new Label(String.valueOf(item.getUnreadCount()));
                unreadLabel.getStyleClass().add("unread-badge"); // Class định nghĩa trong CSS
                rightBox.getChildren().add(unreadLabel);

                // Nếu có tin nhắn mới, làm đậm dòng status để chú ý
                statusLabel.setStyle("-fx-text-fill: #e4e6eb; -fx-font-weight: bold; -fx-font-size: 13px;");
            }

            hbox.getChildren().addAll(avatarStack, infoBox, spacer, rightBox);
            setGraphic(hbox);
            setText(null);
        }
    }
}