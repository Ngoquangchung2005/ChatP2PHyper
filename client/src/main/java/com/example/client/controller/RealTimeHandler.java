package com.example.client.net;

import com.example.client.controller.MainController;
import com.example.common.dto.UserDTO;
import com.example.common.service.ClientCallback;
import javafx.application.Platform;
import javafx.scene.control.Alert;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class RealTimeHandler extends UnicastRemoteObject implements ClientCallback {

    private final MainController mainController;

    public RealTimeHandler(MainController mainController) throws RemoteException {
        super();
        this.mainController = mainController;
    }

    @Override
    public void onFriendStatusChange(UserDTO friend) throws RemoteException {
        Platform.runLater(() -> mainController.updateFriendInList(friend));
    }

    @Override
    public void onNewFriendRequest(UserDTO sender) throws RemoteException {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Thông báo");
            alert.setHeaderText("Lời mời kết bạn mới!");
            alert.setContentText(sender.getDisplayName() + " muốn kết bạn.");
            alert.show();
        });
    }

    @Override
    public void onFriendRequestAccepted(UserDTO newFriend) throws RemoteException {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Tin vui");
            alert.setHeaderText(null);
            alert.setContentText(newFriend.getDisplayName() + " đã chấp nhận lời mời!");
            alert.show();

            newFriend.setOnline(true);
            mainController.addFriendToListDirectly(newFriend);
        });
    }

    @Override
    public void onAddedToGroup(UserDTO newGroup) throws RemoteException {
        Platform.runLater(() -> mainController.updateFriendInList(newGroup));
    }
}