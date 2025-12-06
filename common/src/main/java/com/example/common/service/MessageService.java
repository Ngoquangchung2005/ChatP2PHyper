package com.example.common.service;

import com.example.common.dto.MessageDTO;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

public interface MessageService extends Remote {
    void saveMessage(MessageDTO msg) throws RemoteException;
    List<MessageDTO> getHistory(long conversationId) throws RemoteException;
    long getPrivateConversationId(long user1, long user2) throws RemoteException;
    Map<Long, Integer> getUnreadCounts(long userId) throws RemoteException;
    void markAsRead(long userId, long conversationId) throws RemoteException;

    // --- [MỚI] UPLOAD VÀ DOWNLOAD FILE ---
    // Trả về đường dẫn file trên server
    String uploadFile(byte[] fileData, String fileNameExtension) throws RemoteException;

    // Tải file từ server về client
    byte[] downloadFile(String serverPath) throws RemoteException;
}