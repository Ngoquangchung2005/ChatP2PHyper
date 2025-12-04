package com.example.common.service;

import com.example.common.dto.UserDTO;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface DirectoryService extends Remote {
    UserDTO getUserInfo(long userId) throws RemoteException;
}