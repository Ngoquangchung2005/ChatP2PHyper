package com.example.client.controller;

import com.example.client.net.RmiClient;
import com.example.client.store.SessionStore;
import com.example.client.util.AudioHelper;
import com.example.common.dto.MessageDTO;
import com.example.common.dto.UserDTO;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.scene.input.MouseEvent;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;

public class ChatManager {
    private final MainController mc;
    private final AudioHelper audioRecorder = new AudioHelper();
    private long recordingStartTime;

    public ChatManager(MainController mc) {
        this.mc = mc;
    }

    public void handleIncomingMessage(MessageDTO msg) {
        // 1. Xử lý Recall / Edit
        if (msg.getType() == MessageDTO.MessageType.RECALL) {
            if (mc.messageUiMap.containsKey(msg.getUuid())) {
                VBox bubble = mc.messageUiMap.get(msg.getUuid());
                ChatUIHelper.updateBubbleContent(bubble, "🚫 Tin nhắn đã thu hồi", true);
            }
            return;
        }
        if (msg.getType() == MessageDTO.MessageType.EDIT) {
            if (mc.messageUiMap.containsKey(msg.getUuid())) {
                VBox bubble = mc.messageUiMap.get(msg.getUuid());
                ChatUIHelper.updateBubbleContent(bubble, msg.getContent(), false);
            }
            return;
        }

        // 2. Xử lý tin nhắn mới
        if (mc.activeConversationId != -1 && msg.getConversationId() == mc.activeConversationId) {
            VBox bubble = ChatUIHelper.addMessageBubble(mc.msgContainer, mc.msgScrollPane, msg, false);
            if (msg.getUuid() != null && bubble != null) {
                mc.messageUiMap.put(msg.getUuid(), bubble);
            }
            // Mark read
            new Thread(() -> {
                try {
                    RmiClient.getMessageService().markAsRead(SessionStore.currentUser.getId(), mc.activeConversationId);
                } catch (Exception e) { e.printStackTrace(); }
            }).start();
        }

        // 3. Update sidebar
        mc.getContactManager().moveUserToTop(msg);
    }

    public void sendP2PMessage(MessageDTO msg) {
        // Logic gửi tin nhắn (Bê nguyên từ MainController cũ sang)
        UserDTO targetCache = mc.currentChatUser;
        boolean isGroup = "GROUP".equals(targetCache.getUsername());

        // 1. Gửi mạng
        if (isGroup) {
            try {
                List<Long> memberIds = RmiClient.getGroupService().getGroupMemberIds(mc.activeConversationId);
                for (Long memId : memberIds) {
                    if (memId == SessionStore.currentUser.getId()) continue;
                    UserDTO memInfo = RmiClient.getDirectoryService().getUserInfo(memId);
                    if (memInfo != null && memInfo.isOnline()) {
                        mc.p2pClient.send(memInfo.getLastIp(), memInfo.getLastPort(), msg);
                    }
                }
            } catch (Exception e) { System.err.println("Lỗi gửi Group P2P: " + e.getMessage()); }
        } else {
            if (targetCache != null && targetCache.getLastIp() != null) {
                mc.p2pClient.send(targetCache.getLastIp(), targetCache.getLastPort(), msg);
            }
        }

        // 2. Cập nhật UI (Chỉ tin nhắn mới)
        if (msg.getType() != MessageDTO.MessageType.RECALL &&
                msg.getType() != MessageDTO.MessageType.EDIT &&
                msg.getType() != MessageDTO.MessageType.CALL_REQ &&
                msg.getType() != MessageDTO.MessageType.CALL_ACCEPT &&
                msg.getType() != MessageDTO.MessageType.CALL_DENY &&
                msg.getType() != MessageDTO.MessageType.CALL_END) {

            VBox bubble = ChatUIHelper.addMessageBubble(mc.msgContainer, mc.msgScrollPane, msg, true);
            if (bubble != null && msg.getUuid() != null) {
                mc.messageUiMap.put(msg.getUuid(), bubble);
            }
        }

        mc.getContactManager().moveUserToTop(msg);

        // 3. Backup Server
        if (msg.getType() == MessageDTO.MessageType.RECALL || msg.getType() == MessageDTO.MessageType.EDIT) {
            return;
        }

        new Thread(() -> {
            try {
                MessageDTO backupMsg = new MessageDTO();
                backupMsg.setConversationId(msg.getConversationId());
                backupMsg.setSenderId(msg.getSenderId());
                backupMsg.setCreatedAt(msg.getCreatedAt());
                backupMsg.setContent(msg.getContent());
                backupMsg.setType(msg.getType());
                backupMsg.setUuid(msg.getUuid());

                if (msg.getFileData() != null && msg.getType() != MessageDTO.MessageType.TEXT) {
                    String fName = msg.getFileName() != null ? msg.getFileName() : "file_" + System.currentTimeMillis();
                    String serverPath = RmiClient.getMessageService().uploadFile(msg.getFileData(), fName);
                    backupMsg.setAttachmentUrl(serverPath);
                    backupMsg.setFileData(null);
                }
                RmiClient.getMessageService().saveMessage(backupMsg);
                RmiClient.getMessageService().markAsRead(SessionStore.currentUser.getId(), mc.activeConversationId);
            } catch (Exception e) {
                System.err.println(">> Client: Lỗi Backup Server: " + e.getMessage());
            }
        }).start();
    }

    public void handleSend() {
        String text = mc.inputField.getText().trim();
        if (text.isEmpty() || mc.currentChatUser == null) return;
        MessageDTO msg = new MessageDTO();
        msg.setSenderId(SessionStore.currentUser.getId());
        msg.setSenderName(SessionStore.currentUser.getDisplayName());
        msg.setConversationId(mc.activeConversationId);
        msg.setCreatedAt(LocalDateTime.now());
        msg.setContent(text);
        msg.setType(MessageDTO.MessageType.TEXT);
        sendP2PMessage(msg);
        mc.inputField.clear();
    }

    public void switchChat(UserDTO friendOrGroup) {
        mc.currentChatUser = friendOrGroup;
        mc.welcomeArea.setVisible(false);
        mc.chatArea.setVisible(true);
        mc.currentChatTitle.setText(friendOrGroup.getDisplayName());
        mc.msgContainer.getChildren().clear();

        friendOrGroup.setUnreadCount(0);
        mc.conversationList.refresh();

        // Close sidebar if logic requires (Optional)
        // if (mc.isInfoSidebarOpen) mc.openInfoSidebar();

        new Thread(() -> {
            try {
                if ("GROUP".equals(friendOrGroup.getUsername())) {
                    mc.activeConversationId = friendOrGroup.getId();
                } else {
                    mc.activeConversationId = RmiClient.getMessageService()
                            .getPrivateConversationId(SessionStore.currentUser.getId(), friendOrGroup.getId());
                }
                RmiClient.getMessageService().markAsRead(SessionStore.currentUser.getId(), mc.activeConversationId);
                loadHistory(mc.activeConversationId);
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void loadHistory(long conversationId) {
        try {
            List<MessageDTO> history = RmiClient.getMessageService().getHistory(conversationId);
            Platform.runLater(() -> {
                mc.msgContainer.getChildren().clear();
                mc.messageUiMap.clear();

                for (MessageDTO msg : history) {
                    boolean isMe = msg.getSenderId() == SessionStore.currentUser.getId();
                    VBox bubble = ChatUIHelper.addMessageBubble(mc.msgContainer, mc.msgScrollPane, msg, isMe);
                    if (msg.getUuid() != null && bubble != null) {
                        mc.messageUiMap.put(msg.getUuid(), bubble);
                    }
                }
                scrollToBottom();
            });
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void scrollToBottom() {
        Platform.runLater(() -> {
            mc.msgContainer.layout();
            mc.msgScrollPane.layout();
            Platform.runLater(() -> mc.msgScrollPane.setVvalue(1.0));
        });
    }

    public void handleSendFile() {
        if (mc.currentChatUser == null) return;
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Tất cả file", "*.*"));
        File selectedFile = fileChooser.showOpenDialog(mc.mainBorderPane.getScene().getWindow());

        if (selectedFile != null) {
            new Thread(() -> {
                try {
                    byte[] fileBytes = Files.readAllBytes(selectedFile.toPath());
                    MessageDTO msg = new MessageDTO();
                    msg.setSenderId(SessionStore.currentUser.getId());
                    msg.setSenderName(SessionStore.currentUser.getDisplayName());
                    msg.setConversationId(mc.activeConversationId);
                    msg.setCreatedAt(LocalDateTime.now());
                    msg.setFileData(fileBytes);
                    msg.setFileName(selectedFile.getName());

                    String lowerName = selectedFile.getName().toLowerCase();
                    if (lowerName.endsWith(".png") || lowerName.endsWith(".jpg") ||
                            lowerName.endsWith(".jpeg") || lowerName.endsWith(".gif") || lowerName.endsWith(".bmp")) {
                        msg.setType(MessageDTO.MessageType.IMAGE);
                        msg.setContent("[Hình ảnh]");
                    } else {
                        msg.setType(MessageDTO.MessageType.FILE);
                        msg.setContent("[Tập tin] " + selectedFile.getName());
                    }
                    sendP2PMessage(msg);
                } catch (Exception e) { e.printStackTrace(); }
            }).start();
        }
    }

    public void startRecording(MouseEvent event) {
        if (mc.currentChatUser == null) return;
        recordingStartTime = System.currentTimeMillis();
        mc.micBtn.setStyle("-fx-text-fill: red; -fx-font-size: 20px;");
        audioRecorder.startRecording();
    }

    public void stopAndSendAudio(MouseEvent event) {
        if (mc.currentChatUser == null) return;
        mc.micBtn.setStyle("-fx-text-fill: #667eea; -fx-font-size: 20px;");
        if (System.currentTimeMillis() - recordingStartTime < 500) {
            audioRecorder.stopRecording();
            return;
        }
        byte[] audioData = audioRecorder.stopRecording();
        if (audioData != null) {
            new Thread(() -> {
                MessageDTO msg = new MessageDTO();
                msg.setSenderId(SessionStore.currentUser.getId());
                msg.setSenderName(SessionStore.currentUser.getDisplayName());
                msg.setConversationId(mc.activeConversationId);
                msg.setCreatedAt(LocalDateTime.now());
                msg.setType(MessageDTO.MessageType.AUDIO);
                msg.setFileData(audioData);
                msg.setContent("[Tin nhắn thoại]");
                sendP2PMessage(msg);
            }).start();
        }
    }

    public void handleEditAction(MessageDTO targetMsg) {
        TextInputDialog dialog = new TextInputDialog(targetMsg.getContent());
        dialog.setTitle("Chỉnh sửa tin nhắn");
        dialog.setHeaderText(null);
        dialog.setContentText("Nội dung mới:");
        dialog.showAndWait().ifPresent(newContent -> {
            MessageDTO editMsg = new MessageDTO();
            editMsg.setType(MessageDTO.MessageType.EDIT);
            editMsg.setUuid(targetMsg.getUuid());
            editMsg.setContent(newContent);
            editMsg.setConversationId(mc.activeConversationId);
            editMsg.setSenderId(SessionStore.currentUser.getId());
            sendP2PMessage(editMsg);

            if (mc.messageUiMap.containsKey(targetMsg.getUuid())) {
                ChatUIHelper.updateBubbleContent(mc.messageUiMap.get(targetMsg.getUuid()), newContent, false);
            }
            new Thread(() -> {
                try {
                    RmiClient.getMessageService().updateMessage(targetMsg.getUuid(), newContent, MessageDTO.MessageType.EDIT);
                } catch(Exception e) { e.printStackTrace(); }
            }).start();
        });
    }

    public void handleRecallAction(MessageDTO targetMsg) {
        MessageDTO recallMsg = new MessageDTO();
        recallMsg.setType(MessageDTO.MessageType.RECALL);
        recallMsg.setUuid(targetMsg.getUuid());
        recallMsg.setConversationId(mc.activeConversationId);
        recallMsg.setSenderId(SessionStore.currentUser.getId());
        sendP2PMessage(recallMsg);

        if (mc.messageUiMap.containsKey(targetMsg.getUuid())) {
            ChatUIHelper.updateBubbleContent(mc.messageUiMap.get(targetMsg.getUuid()), "🚫 Tin nhắn đã thu hồi", true);
        }
        new Thread(() -> {
            try {
                RmiClient.getMessageService().updateMessage(targetMsg.getUuid(), null, MessageDTO.MessageType.RECALL);
            } catch(Exception e) { e.printStackTrace(); }
        }).start();
    }
}