package com.example.client.controller;

import com.example.common.dto.UserDTO;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

// Class này kế thừa ListCell để tùy biến giao diện dòng trong ListView
public class FriendListCell extends ListCell<UserDTO> {
    private final CheckBox checkBox = new CheckBox();

    public FriendListCell() {
        // Xử lý sự kiện khi bấm vào ô CheckBox
        checkBox.setOnAction(event -> {
            UserDTO item = getItem();
            if (item != null && getListView() != null) {
                if (checkBox.isSelected()) {
                    // Nếu tick vào -> Chọn dòng đó trong ListView
                    getListView().getSelectionModel().select(item);
                } else {
                    // Nếu bỏ tick -> Bỏ chọn dòng đó
                    // getIndex() lấy vị trí hiện tại của dòng này
                    getListView().getSelectionModel().clearSelection(getIndex());
                }
            }
        });
    }

    @Override
    protected void updateItem(UserDTO item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setGraphic(null);
            setText(null);
        } else {
            HBox hbox = new HBox(10);
            hbox.setAlignment(Pos.CENTER_LEFT);

            // 1. Trạng thái Online (Chấm xanh)
            Circle statusDot = new Circle(5);
            if (item.isOnline()) {
                statusDot.setFill(Color.LIMEGREEN);
            } else {
                statusDot.setFill(Color.GRAY);
            }

            // 2. Tên hiển thị
            Label nameLabel = new Label(item.getDisplayName());
            nameLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: black;");

            // Khoảng trắng đẩy badge sang phải
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            // 3. [MỚI] Badge Tin nhắn chưa đọc
            Label badge = null;
            if (item.getUnreadCount() > 0) {
                badge = new Label(String.valueOf(item.getUnreadCount()));
                badge.getStyleClass().add("unread-badge");
            }

            if (badge != null) {
                hbox.getChildren().addAll(statusDot, nameLabel, spacer, badge);
            } else {
                hbox.getChildren().addAll(statusDot, nameLabel);
            }

            setGraphic(hbox);
            setText(null);
        }
    }
}