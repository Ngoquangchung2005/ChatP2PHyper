package com.example.common.service;

import com.example.common.dto.UserDTO;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface FriendService extends Remote {
    List<UserDTO> getFriendList(long userId) throws RemoteException;
    List<UserDTO> searchUsers(String query) throws RemoteException;
    boolean addFriend(long userId, long friendId) throws RemoteException;
}