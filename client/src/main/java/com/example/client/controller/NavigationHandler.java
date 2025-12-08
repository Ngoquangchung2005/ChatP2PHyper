package com.example.client.controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class NavigationHandler {
    private final MainController mc;
    private boolean isInfoSidebarOpen = false;

    public NavigationHandler(MainController mc) {
        this.mc = mc;
    }

    public void handleCreateGroup() { openDialog("/view/create-group.fxml", "Tạo Nhóm"); }
    public void handleAddFriend() { openDialog("/view/add-friend.fxml", "Thêm bạn bè"); }
    public void handleShowRequests() { openDialog("/view/friend-requests.fxml", "Lời mời"); }

    public void handleOpenProfile() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/profile-view.fxml"));
            Stage stage = new Stage();
            stage.setScene(new Scene(loader.load()));
            stage.show();
        } catch (Exception e) {}
    }

    public void handleToggleInfo() {
        if (mc.currentChatUser == null) return;
        if (isInfoSidebarOpen) {
            mc.mainBorderPane.setRight(null);
            isInfoSidebarOpen = false;
        } else {
            openInfoSidebar();
        }
    }

    private void openInfoSidebar() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/chat-info.fxml"));
            Parent infoNode = loader.load();
            ChatInfoController infoCtrl = loader.getController();
            infoCtrl.setUserInfo(mc.currentChatUser);
            mc.mainBorderPane.setRight(infoNode);
            isInfoSidebarOpen = true;
        } catch (Exception e) {}
    }

    private void openDialog(String fxml, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Parent root = loader.load();
            if (loader.getController() instanceof FriendRequestController) {
                ((FriendRequestController) loader.getController()).setMainController(mc);
            }
            Stage stage = new Stage();
            stage.setTitle(title);
            stage.setScene(new Scene(root));
            stage.showAndWait();
            mc.getContactManager().loadFriendListInitial();
        } catch (Exception e) {}
    }
}