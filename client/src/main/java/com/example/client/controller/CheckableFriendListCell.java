package com.example.client.controller;

import com.example.common.dto.UserDTO;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ListCell;

// Class này dùng cho TẠO NHÓM (Có CheckBox)
public class CheckableFriendListCell extends ListCell<UserDTO> {
    private final CheckBox checkBox = new CheckBox();

    public CheckableFriendListCell() {
        // Logic xử lý khi bấm vào CheckBox (Code cũ của bạn)
        checkBox.setOnAction(event -> {
            UserDTO item = getItem();
            if (item != null && getListView() != null) {
                if (checkBox.isSelected()) {
                    getListView().getSelectionModel().select(item);
                } else {
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
            checkBox.setText(item.getDisplayName());
            // Đồng bộ trạng thái
            if (getListView() != null) {
                boolean isSelected = getListView().getSelectionModel().getSelectedItems().contains(item);
                checkBox.setSelected(isSelected);
            }
            setGraphic(checkBox);
            setText(null);
        }
    }
}