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
    // [MỚI] Lấy danh sách số tin nhắn chưa đọc (Key: ID của Bạn bè/Nhóm, Value: Số lượng)
    Map<Long, Integer> getUnreadCounts(long userId) throws RemoteException;

    // [MỚI] Đánh dấu đã đọc toàn bộ tin nhắn trong hội thoại
    void markAsRead(long userId, long conversationId) throws RemoteException;
}