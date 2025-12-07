package com.example.common.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

public class MessageDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    // Thêm các trạng thái cuộc gọi vào Enum
    public enum MessageType {
        TEXT, IMAGE, FILE,
        AUDIO,       // <--- THÊM LOẠI NÀY (Tin nhắn thoại)
        CALL_REQ,    // Yêu cầu gọi
        CALL_ACCEPT, // Chấp nhận
        CALL_DENY,   // Từ chối
        CALL_END ,    // Kết thúc cuộc gọi
        EDIT, RECALL // <--- THÊM 2 LOẠI NÀY
    }

    private long id;
    private long conversationId;
    private long senderId;
    private String senderName;
    private String content;
    private LocalDateTime createdAt;

    // --- CÁC TRƯỜNG MỚI ---
    private MessageType type = MessageType.TEXT; // Mặc định là Text
    private byte[] fileData;  // Dữ liệu file (ảnh/tài liệu)
    private String fileName;  // Tên file gốc (ví dụ: hinh_anh.jpg)
    // --- [MỚI] TRƯỜNG LƯU ĐƯỜNG DẪN FILE TRÊN SERVER ---
    private String attachmentUrl;
    // --- THÊM TRƯỜNG MỚI ---
    private String uuid; // Định danh duy nhất do Client tạo

    public MessageDTO() {
        this.uuid = UUID.randomUUID().toString();
    }

    // --- Getters & Setters ---
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getConversationId() { return conversationId; }
    public void setConversationId(long conversationId) { this.conversationId = conversationId; }
    public long getSenderId() { return senderId; }
    public void setSenderId(long senderId) { this.senderId = senderId; }
    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    // Getter/Setter cho trường mới
    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }
    public byte[] getFileData() { return fileData; }
    public void setFileData(byte[] fileData) { this.fileData = fileData; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    // [MỚI] Getter & Setter cho attachmentUrl
    public String getAttachmentUrl() { return attachmentUrl; }
    public void setAttachmentUrl(String attachmentUrl) { this.attachmentUrl = attachmentUrl; }
    // Getter & Setter cho uuid
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
}