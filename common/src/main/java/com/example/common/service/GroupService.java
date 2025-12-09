package com.example.common.service;

import com.example.common.dto.UserDTO;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface GroupService extends Remote {
    // Tạo nhóm mới
    long createGroup(String groupName, List<Long> memberIds) throws RemoteException;

    // Lấy danh sách ID thành viên (để Client gửi P2P)
    List<Long> getGroupMemberIds(long conversationId) throws RemoteException;

    // Lấy danh sách các nhóm mà user đang tham gia (Chuyển từ ChatService qua đây luôn cho chuẩn)
    List<UserDTO> getMyGroups(long userId) throws RemoteException;
    // --- [THÊM MỚI] ---
    boolean leaveGroup(long userId, long groupId) throws RemoteException;
}