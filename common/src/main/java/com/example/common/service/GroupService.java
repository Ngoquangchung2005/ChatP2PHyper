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

    // Lấy danh sách các nhóm mà user đang tham gia
    List<UserDTO> getMyGroups(long userId) throws RemoteException;

    // Rời nhóm (Tự mình rời đi)
    boolean leaveGroup(long userId, long groupId) throws RemoteException;

    // 1. Lấy danh sách thành viên đầy đủ
    List<UserDTO> getGroupMembers(long groupId) throws RemoteException;

    // 2. Thêm thành viên vào nhóm
    boolean addMemberToGroup(long groupId, long newMemberId) throws RemoteException;

    // 3. Mời thành viên ra khỏi nhóm (Kick) - [ĐÃ SỬA DÒNG NÀY: THÊM requesterId]
    boolean removeMemberFromGroup(long requesterId, long groupId, long targetId) throws RemoteException;

    // [MỚI] Cập nhật thông tin nhóm (Tên, Avatar)
    boolean updateGroupInfo(long requesterId, long groupId, String newName, String avatarUrl) throws RemoteException;

    // [MỚI] Giải tán nhóm
    boolean dissolveGroup(long requesterId, long groupId) throws RemoteException;
}