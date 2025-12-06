package com.example.client.controller;

import com.example.client.net.RmiClient;
import com.example.client.util.AudioHelper;
import com.example.common.dto.MessageDTO;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;

public class ChatUIHelper {

    public static void addMessageBubble(VBox msgContainer, ScrollPane msgScrollPane, MessageDTO msg, boolean isMe) {

        // Xử lý Lazy Loading (tải file) nếu cần
        if (isMediaMessage(msg) && msg.getFileData() == null && msg.getAttachmentUrl() != null) {
            handleLazyLoading(msgContainer, msgScrollPane, msg, isMe);
            return;
        }

        Node contentNode;

        // --- XỬ LÝ LOẠI TIN NHẮN ---
        if (msg.getType() == MessageDTO.MessageType.TEXT) {
            Text text = new Text(msg.getContent());
            text.getStyleClass().add(isMe ? "text-me" : "text-other");
            TextFlow textFlow = new TextFlow(text);
            textFlow.setMaxWidth(450); // Mở rộng chiều rộng tin nhắn
            contentNode = textFlow;
        }
        else if (msg.getType() == MessageDTO.MessageType.IMAGE && msg.getFileData() != null) {
            contentNode = createImageNode(msg.getFileData());
        }
        else if (msg.getType() == MessageDTO.MessageType.AUDIO && msg.getFileData() != null) {
            contentNode = createAudioNode(msg, isMe);
        }
        else if (msg.getType() == MessageDTO.MessageType.FILE && msg.getFileData() != null) {
            contentNode = createFileNode(msgContainer, msg, isMe);
        }
        else {
            contentNode = new Label(msg.getContent());
            ((Label)contentNode).getStyleClass().add(isMe ? "text-me" : "text-other");
        }

        // --- TẠO BONG BÓNG ---
        VBox bubble = new VBox(contentNode);
        // Style Class: bubble-me hoặc bubble-other (đã định nghĩa trong CSS)
        bubble.getStyleClass().add(isMe ? "bubble-me" : "bubble-other");

        // --- CONTAINER CHỨA TIN NHẮN VÀ THỜI GIAN ---
        VBox messageBlock = new VBox(3); // Cách nhau 3px
        messageBlock.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        messageBlock.getChildren().add(bubble);

        // Hiện thời gian nhỏ ở dưới
        if (msg.getCreatedAt() != null) {
            Label timeLbl = new Label(msg.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm")));
            timeLbl.getStyleClass().add("time-label");
            messageBlock.getChildren().add(timeLbl);
        }

        // --- DÒNG (ROW) ---
        HBox row = new HBox();
        row.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 10, 2, 10)); // Padding trái phải

        // Nếu là tin nhắn người khác, thêm Avatar nhỏ bên cạnh bong bóng
        if (!isMe) {
            // (Có thể thêm ImageView avatar nhỏ ở đây nếu muốn giống group chat)
            // Tạm thời để trống cho giống chat 1-1
        }

        row.getChildren().add(messageBlock);

        Platform.runLater(() -> {
            msgContainer.getChildren().add(row);
            // Tự động cuộn
            msgContainer.layout();
            msgScrollPane.layout();
            msgScrollPane.setVvalue(1.0);
        });
    }

    private static Node createImageNode(byte[] imageData) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(imageData);
            Image image = new Image(bis);
            ImageView imageView = new ImageView(image);

            imageView.setFitWidth(300); // Ảnh to hơn chút
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);

            // Bo góc ảnh (Clip)
            Rectangle clip = new Rectangle(300, 300);
            if (image.getWidth() > 0) {
                double aspect = image.getHeight() / image.getWidth();
                clip.setWidth(300);
                clip.setHeight(300 * aspect);
            }
            clip.setArcWidth(15);
            clip.setArcHeight(15);
            imageView.setClip(clip);

            return imageView;
        } catch (Exception e) { return new Label("Lỗi ảnh"); }
    }

    private static Node createAudioNode(MessageDTO msg, boolean isMe) {
        // Nút Play Audio đẹp hơn
        Button playBtn = new Button("▶  Tin nhắn thoại");
        // CSS inline cho nút Audio
        String textColor = isMe ? "white" : "#e4e6eb";
        playBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + textColor + "; -fx-font-weight: bold; -fx-cursor: hand; -fx-font-size: 14px;");

        playBtn.setOnAction(e -> {
            playBtn.setText("🔊 Đang phát...");
            playBtn.setDisable(true);
            AudioHelper.playAudio(msg.getFileData());
            new Thread(() -> {
                try { Thread.sleep(3000); } catch (Exception ex) {}
                Platform.runLater(() -> {
                    playBtn.setText("▶  Nghe lại");
                    playBtn.setDisable(false);
                });
            }).start();
        });
        return playBtn;
    }

    private static Node createFileNode(VBox container, MessageDTO msg, boolean isMe) {
        String fName = msg.getFileName() != null ? msg.getFileName() : "Tài liệu";
        Button downloadBtn = new Button("📄 " + fName);
        String textColor = isMe ? "white" : "#e4e6eb";
        downloadBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + textColor + "; -fx-cursor: hand; -fx-font-size: 14px;");

        downloadBtn.setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setInitialFileName(fName);
            File file = fileChooser.showSaveDialog(container.getScene().getWindow());
            if (file != null) {
                try { Files.write(file.toPath(), msg.getFileData()); } catch (Exception e) { e.printStackTrace(); }
            }
        });
        return downloadBtn;
    }

    // Logic Lazy Loading (Giữ nguyên)
    private static void handleLazyLoading(VBox msgContainer, ScrollPane msgScrollPane, MessageDTO msg, boolean isMe) {
        Label loadingLabel = new Label("⟳ Đang tải file...");
        loadingLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 11px;");
        HBox loadingBox = new HBox(loadingLabel);
        loadingBox.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        loadingBox.setPadding(new Insets(0, 10, 0, 10));

        Platform.runLater(() -> msgContainer.getChildren().add(loadingBox));

        new Thread(() -> {
            try {
                byte[] downloadedData = RmiClient.getMessageService().downloadFile(msg.getAttachmentUrl());
                Platform.runLater(() -> {
                    msgContainer.getChildren().remove(loadingBox);
                    if (downloadedData != null) {
                        msg.setFileData(downloadedData);
                        addMessageBubble(msgContainer, msgScrollPane, msg, isMe);
                    }
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private static boolean isMediaMessage(MessageDTO msg) {
        return msg.getType() == MessageDTO.MessageType.IMAGE ||
                msg.getType() == MessageDTO.MessageType.FILE ||
                msg.getType() == MessageDTO.MessageType.AUDIO;
    }
}