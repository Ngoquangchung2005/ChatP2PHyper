package com.example.client.controller;

import com.example.common.dto.UserDTO;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ListCell;

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
            // Cài đặt hiển thị: Checkbox + Tên hiển thị
            checkBox.setText(item.getDisplayName());

            // Đồng bộ trạng thái: Kiểm tra xem item này có đang nằm trong danh sách đã chọn không
            if (getListView() != null) {
                boolean isSelected = getListView().getSelectionModel().getSelectedItems().contains(item);
                checkBox.setSelected(isSelected);
            }

            setGraphic(checkBox); // Hiển thị Checkbox thay vì text mặc định
            setText(null);
        }
    }
}