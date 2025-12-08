package com.example.client.controller;

import com.example.client.net.VoiceCallManager;
import com.example.client.store.SessionStore;
import com.example.common.dto.MessageDTO;
import com.example.common.dto.UserDTO;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import java.time.LocalDateTime;

public class CallHandler {
    private final MainController mc;
    private final VoiceCallManager voiceCallManager = new VoiceCallManager();
    private VideoCallController currentVideoCallController;

    public CallHandler(MainController mc) {
        this.mc = mc;
    }

    public void handleCallSignal(MessageDTO msg) {
        if (msg.getType() == MessageDTO.MessageType.CALL_REQ) {
            handleIncomingCall(msg);
        } else if (msg.getType() == MessageDTO.MessageType.CALL_ACCEPT) {
            UserDTO partner = mc.getContactManager().findUserInList(msg.getSenderId());
            if (partner != null) openVideoCallWindow(partner.getLastIp(), partner.getLastPort());
        } else if (msg.getType() == MessageDTO.MessageType.CALL_DENY) {
            Alert a = new Alert(Alert.AlertType.ERROR, "Người kia đã từ chối cuộc gọi.");
            a.show();
        } else if (msg.getType() == MessageDTO.MessageType.CALL_END) {
            if (currentVideoCallController != null) {
                currentVideoCallController.closeWindow();
                currentVideoCallController = null;
            }
            if (voiceCallManager.isCalling()) voiceCallManager.stopCall();
            Alert a = new Alert(Alert.AlertType.INFORMATION, "Cuộc gọi đã kết thúc.");
            a.show();
        }
    }

    public void handleVoiceCall() {
        if (mc.currentChatUser == null) return;
        if ("GROUP".equals(mc.currentChatUser.getUsername())) {
            new Alert(Alert.AlertType.WARNING, "Chưa hỗ trợ gọi nhóm!").show();
            return;
        }
        MessageDTO msg = new MessageDTO();
        msg.setType(MessageDTO.MessageType.CALL_REQ);
        msg.setSenderId(SessionStore.currentUser.getId());
        msg.setSenderName(SessionStore.currentUser.getDisplayName());
        msg.setConversationId(mc.activeConversationId);
        msg.setCreatedAt(LocalDateTime.now());
        msg.setContent("Đang gọi Video cho bạn...");
        mc.getChatManager().sendP2PMessage(msg);
    }

    private void handleIncomingCall(MessageDTO msg) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Cuộc gọi đến");
        alert.setHeaderText(msg.getSenderName() + " đang gọi Video cho bạn!");
        alert.setContentText("Bạn có muốn nghe máy không?");
        ButtonType btnYes = new ButtonType("Nghe");
        ButtonType btnNo = new ButtonType("Từ chối", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(btnYes, btnNo);
        alert.showAndWait().ifPresent(type -> {
            if (type == btnYes) {
                MessageDTO acceptMsg = new MessageDTO();
                acceptMsg.setType(MessageDTO.MessageType.CALL_ACCEPT);
                acceptMsg.setSenderId(SessionStore.currentUser.getId());
                acceptMsg.setConversationId(msg.getConversationId());
                UserDTO caller = mc.getContactManager().findUserInList(msg.getSenderId());
                if (caller != null) {
                    mc.p2pClient.send(caller.getLastIp(), caller.getLastPort(), acceptMsg);
                    openVideoCallWindow(caller.getLastIp(), caller.getLastPort());
                }
            } else {
                MessageDTO denyMsg = new MessageDTO();
                denyMsg.setType(MessageDTO.MessageType.CALL_DENY);
                denyMsg.setSenderId(SessionStore.currentUser.getId());
                UserDTO caller = mc.getContactManager().findUserInList(msg.getSenderId());
                if (caller != null) mc.p2pClient.send(caller.getLastIp(), caller.getLastPort(), denyMsg);
            }
        });
    }

    private void openVideoCallWindow(String targetIp, int targetPort) {
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/video-call.fxml"));
                Parent root = loader.load();
                VideoCallController ctrl = loader.getController();
                this.currentVideoCallController = ctrl;
                ctrl.setDependencies(mc, voiceCallManager);
                ctrl.startCall(targetIp, targetPort, SessionStore.p2pPort);
                Stage stage = new Stage();
                stage.setTitle("Video Call");
                stage.setScene(new Scene(root));
                stage.setOnCloseRequest(e -> ctrl.handleEndCall());
                stage.show();
            } catch (Exception e) {}
        });
    }

    public void handleEndCallSignal() {
        MessageDTO msg = new MessageDTO();
        msg.setType(MessageDTO.MessageType.CALL_END);
        msg.setSenderId(SessionStore.currentUser.getId());
        msg.setConversationId(mc.activeConversationId);
        mc.getChatManager().sendP2PMessage(msg);
        currentVideoCallController = null;
    }
}