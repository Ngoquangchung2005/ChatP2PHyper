package com.example.common.service;

import com.example.common.dto.UserDTO;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface AuthService extends Remote {
    UserDTO login(String username, String password, String clientIp, int p2pPort) throws RemoteException;
    boolean register(String username, String password, String displayName, String email) throws RemoteException;
    void logout(long userId) throws RemoteException;
    void registerNotification(long userId, ClientCallback callback) throws RemoteException;

    // --- [MỚI] ---
    // Đổi mật khẩu
    boolean changePassword(long userId, String oldPassword, String newPassword) throws RemoteException;

    // Cập nhật thông tin cá nhân (Tên, Status, Avatar)
    // avatarData: Nếu null thì không đổi ảnh, nếu có byte[] thì upload ảnh mới
    boolean updateProfile(long userId, String displayName, String statusMsg, byte[] avatarData, String fileExtension) throws RemoteException;

    // Hàm cũ resetPassword (bạn có thể giữ hoặc bỏ)
    boolean resetPassword(String username, String email, String newPassword) throws RemoteException;
}