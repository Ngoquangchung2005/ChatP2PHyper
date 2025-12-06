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

        // Lazy load nếu có URL mà chưa có data
        if (isMediaMessage(msg) && msg.getFileData() == null && msg.getAttachmentUrl() != null) {
            handleLazyLoading(msgContainer, msgScrollPane, msg, isMe);
            return;
        }

        Node contentNode;

        if (msg.getType() == MessageDTO.MessageType.TEXT) {
            Text text = new Text(msg.getContent());
            text.getStyleClass().add(isMe ? "text-me" : "text-other");
            TextFlow textFlow = new TextFlow(text);
            textFlow.setMaxWidth(450);
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
            // Fallback
            Label lbl = new Label(msg.getContent());
            lbl.getStyleClass().add(isMe ? "text-me" : "text-other");
            contentNode = lbl;
        }

        VBox bubble = new VBox(contentNode);
        bubble.getStyleClass().add(isMe ? "bubble-me" : "bubble-other");

        VBox messageBlock = new VBox(3);
        messageBlock.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        messageBlock.getChildren().add(bubble);

        if (msg.getCreatedAt() != null) {
            Label timeLbl = new Label(msg.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm")));
            timeLbl.getStyleClass().add("time-label");
            messageBlock.getChildren().add(timeLbl);
        }

        HBox row = new HBox();
        row.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 10, 2, 10));
        row.getChildren().add(messageBlock);

        Platform.runLater(() -> {
            msgContainer.getChildren().add(row);
            msgContainer.layout();
            msgScrollPane.layout();
            msgScrollPane.setVvalue(1.0);
        });
    }

    // --- SỬA LỖI HIỂN THỊ ẢNH TRẮNG ---
    private static Node createImageNode(byte[] imageData) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(imageData);
            Image image = new Image(bis);
            ImageView imageView = new ImageView(image);

            imageView.setFitWidth(280);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);

            // Bo tròn 20px
            Rectangle clip = new Rectangle();
            clip.setArcWidth(20);
            clip.setArcHeight(20);

            // [FIX] Cập nhật clip theo kích thước thật của ảnh khi layout thay đổi
            clip.widthProperty().bind(imageView.fitWidthProperty());
            imageView.layoutBoundsProperty().addListener((obs, oldVal, newVal) -> {
                clip.setWidth(newVal.getWidth());
                clip.setHeight(newVal.getHeight());
            });

            imageView.setClip(clip);
            return imageView;

        } catch (Exception e) {
            e.printStackTrace();
            return new Label("❌ Lỗi hiển thị ảnh");
        }
    }

    // --- XỬ LÝ NÚT PLAY VOICE ---
    private static Node createAudioNode(MessageDTO msg, boolean isMe) {
        Button playBtn = new Button("▶  Tin nhắn thoại");
        String textColor = isMe ? "white" : "#333333";
        playBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + textColor + "; -fx-font-weight: bold; -fx-cursor: hand; -fx-font-size: 14px;");

        playBtn.setOnAction(e -> {
            playBtn.setText("🔊 Đang phát...");
            playBtn.setDisable(true); // Disable để tránh spam click

            // Chạy trong Thread riêng để không đơ UI
            new Thread(() -> {
                AudioHelper.playAudio(msg.getFileData());
                try {
                    // Ước lượng thời gian chờ hoặc chờ AudioHelper xong
                    Thread.sleep(2000);
                } catch (Exception ex) {}

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
        // Nếu tên file quá dài thì cắt bớt
        if (fName.length() > 25) fName = fName.substring(0, 22) + "...";

        Button downloadBtn = new Button("📄 " + fName);
        String textColor = isMe ? "white" : "#333333";
        downloadBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + textColor + "; -fx-cursor: hand; -fx-font-size: 14px;");

        // Cần biến final để dùng trong lambda
        String finalName = msg.getFileName() != null ? msg.getFileName() : "Tai_lieu";

        downloadBtn.setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setInitialFileName(finalName);
            File file = fileChooser.showSaveDialog(downloadBtn.getScene().getWindow());
            if (file != null) {
                try { Files.write(file.toPath(), msg.getFileData()); } catch (Exception e) { e.printStackTrace(); }
            }
        });
        return downloadBtn;
    }

    private static void handleLazyLoading(VBox msgContainer, ScrollPane msgScrollPane, MessageDTO msg, boolean isMe) {
        Label loadingLabel = new Label("⟳ Đang tải...");
        loadingLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 11px;");

        VBox bubble = new VBox(loadingLabel);
        bubble.getStyleClass().add(isMe ? "bubble-me" : "bubble-other");

        VBox messageBlock = new VBox(bubble);
        messageBlock.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        HBox row = new HBox(messageBlock);
        row.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 10, 2, 10));

        Platform.runLater(() -> {
            msgContainer.getChildren().add(row);
            msgContainer.layout();
            msgScrollPane.setVvalue(1.0);
        });

        new Thread(() -> {
            try {
                byte[] downloadedData = RmiClient.getMessageService().downloadFile(msg.getAttachmentUrl());
                Platform.runLater(() -> {
                    if (downloadedData != null) {
                        msg.setFileData(downloadedData);
                        Node realNode;

                        if (msg.getType() == MessageDTO.MessageType.IMAGE) realNode = createImageNode(downloadedData);
                        else if (msg.getType() == MessageDTO.MessageType.AUDIO) realNode = createAudioNode(msg, isMe);
                        else realNode = createFileNode(msgContainer, msg, isMe);

                        bubble.getChildren().setAll(realNode);
                        msgContainer.layout();
                        msgScrollPane.layout();
                        msgScrollPane.setVvalue(1.0);
                    } else {
                        loadingLabel.setText("❌ Lỗi tải");
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