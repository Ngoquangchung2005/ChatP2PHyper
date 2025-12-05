package com.example.common.service;

import com.example.common.dto.UserDTO;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ClientCallback extends Remote {
    // Báo trạng thái Online/Offline (Cũ)
    void onFriendStatusChange(UserDTO friend) throws RemoteException;

    // [MỚI] Báo khi có người gửi lời mời kết bạn
    void onNewFriendRequest(UserDTO sender) throws RemoteException;

    // [MỚI] Báo khi lời mời của mình được chấp nhận (để cập nhật list ngay)
    void onFriendRequestAccepted(UserDTO newFriend) throws RemoteException;

    // [MỚI] Hàm báo khi được thêm vào nhóm
    void onAddedToGroup(UserDTO newGroup) throws RemoteException;
}