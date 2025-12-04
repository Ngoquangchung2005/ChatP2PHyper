package com.example.common.service;

import com.example.common.dto.MessageDTO;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface MessageService extends Remote {
    void saveMessage(MessageDTO msg) throws RemoteException;
    List<MessageDTO> getHistory(long conversationId) throws RemoteException;
    long getPrivateConversationId(long user1, long user2) throws RemoteException;
}