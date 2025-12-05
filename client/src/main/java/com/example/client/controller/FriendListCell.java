package com.example.client.controller;

import com.example.common.dto.UserDTO;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

// Class này dùng cho MÀN HÌNH CHÍNH (Online/Offline + Badge)
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

            // 1. Dấu chấm Online/Offline
            Circle statusDot = new Circle(5);
            if (item.isOnline()) {
                statusDot.setFill(Color.LIMEGREEN); // Online: Xanh
            } else {
                statusDot.setFill(Color.GRAY);      // Offline: Xám (hoặc Đỏ tùy bạn)
            }

            // 2. Tên hiển thị
            Label nameLabel = new Label(item.getDisplayName());
            nameLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: black;");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            // 3. Số tin nhắn chưa đọc (Badge)
            Label unreadLabel = null;
            if (item.getUnreadCount() > 0) {
                unreadLabel = new Label("(" + item.getUnreadCount() + ")");
                unreadLabel.setTextFill(Color.RED);
                unreadLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));

                // In đậm tên nếu có tin nhắn mới
                nameLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
            }

            if (unreadLabel != null) {
                hbox.getChildren().addAll(statusDot, nameLabel, spacer, unreadLabel);
            } else {
                hbox.getChildren().addAll(statusDot, nameLabel);
            }

            setGraphic(hbox);
            setText(null);
        }
    }
}