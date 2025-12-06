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
import javafx.scene.layout.StackPane; // Import thêm StackPane
import javafx.scene.layout.Region;    // Import thêm Region
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

        // 1. Xử lý Lazy Loading (Nếu có link ảnh nhưng chưa có dữ liệu)
        if (isMediaMessage(msg) && msg.getFileData() == null && msg.getAttachmentUrl() != null) {
            handleLazyLoading(msgContainer, msgScrollPane, msg, isMe);
            return;
        }

        Node contentNode;

        // 2. Tạo nội dung tin nhắn theo loại
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
            Label lbl = new Label(msg.getContent());
            lbl.getStyleClass().add(isMe ? "text-me" : "text-other");
            contentNode = lbl;
        }

        // 3. Đóng gói vào Bong bóng (Bubble)
        VBox bubble = new VBox(contentNode);
        bubble.getStyleClass().add(isMe ? "bubble-me" : "bubble-other");

        // 4. Thêm thời gian & Căn chỉnh
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

        // 5. Thêm vào giao diện
        Platform.runLater(() -> {
            msgContainer.getChildren().add(row);
            msgContainer.layout();
            msgScrollPane.layout();
            msgScrollPane.setVvalue(1.0);
        });
    }

    // --- [HÀM SỬA LỖI] TẠO NODE ẢNH ---
    private static Node createImageNode(byte[] imageData) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(imageData);
            Image image = new Image(bis);
            ImageView imageView = new ImageView(image);

            // Thiết lập kích thước hiển thị
            imageView.setFitWidth(280);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);

            // [FIX LỖI TRẮNG ẢNH]
            // Thay vì tính toán clip thủ công (dễ lỗi 0x0), ta dùng StackPane làm container
            // và Bind kích thước Clip theo kích thước thật của StackPane.
            StackPane container = new StackPane(imageView);

            // Đảm bảo container ôm sát ảnh
            container.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            container.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
            container.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

            // Tạo khung bo tròn
            Rectangle clip = new Rectangle();
            clip.setArcWidth(20);
            clip.setArcHeight(20);

            // Ràng buộc (Bind) kích thước clip luôn bằng kích thước container
            clip.widthProperty().bind(container.widthProperty());
            clip.heightProperty().bind(container.heightProperty());

            container.setClip(clip);

            return container;
        } catch (Exception e) {
            e.printStackTrace();
            return new Label("❌ Lỗi hiển thị ảnh");
        }
    }

    // --- CÁC NODE KHÁC (Audio, File) ---
    private static Node createAudioNode(MessageDTO msg, boolean isMe) {
        Button playBtn = new Button("▶  Tin nhắn thoại");
        String textColor = isMe ? "white" : "#333333";
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
        String textColor = isMe ? "white" : "#333333";
        downloadBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + textColor + "; -fx-cursor: hand; -fx-font-size: 14px;");

        downloadBtn.setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setInitialFileName(fName);
            File file = fileChooser.showSaveDialog(downloadBtn.getScene().getWindow());
            if (file != null) {
                try { Files.write(file.toPath(), msg.getFileData()); } catch (Exception e) { e.printStackTrace(); }
            }
        });
        return downloadBtn;
    }

    // --- LAZY LOADING (Tải ngầm) ---
    private static void handleLazyLoading(VBox msgContainer, ScrollPane msgScrollPane, MessageDTO msg, boolean isMe) {
        // Tạo placeholder giữ chỗ
        Label loadingLabel = new Label("⟳ Đang tải...");
        loadingLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 11px;");

        // Đóng gói vào bong bóng giống hệt tin nhắn thật để giữ chỗ layout
        VBox bubble = new VBox(loadingLabel);
        bubble.getStyleClass().add(isMe ? "bubble-me" : "bubble-other");

        VBox messageBlock = new VBox(3);
        messageBlock.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        messageBlock.getChildren().add(bubble);

        HBox row = new HBox(messageBlock);
        row.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 10, 2, 10));

        Platform.runLater(() -> {
            msgContainer.getChildren().add(row);
            msgContainer.layout();
            msgScrollPane.setVvalue(1.0);
        });

        // Tải dữ liệu thật
        new Thread(() -> {
            try {
                byte[] downloadedData = RmiClient.getMessageService().downloadFile(msg.getAttachmentUrl());
                Platform.runLater(() -> {
                    if (downloadedData != null) {
                        msg.setFileData(downloadedData);
                        Node realNode;

                        // Tạo nội dung thật
                        if (msg.getType() == MessageDTO.MessageType.IMAGE) realNode = createImageNode(downloadedData);
                        else if (msg.getType() == MessageDTO.MessageType.AUDIO) realNode = createAudioNode(msg, isMe);
                        else realNode = createFileNode(msgContainer, msg, isMe);

                        // Thay thế nội dung trong bong bóng cũ
                        bubble.getChildren().setAll(realNode);

                        // Ép cuộn xuống lại vì kích thước thay đổi
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