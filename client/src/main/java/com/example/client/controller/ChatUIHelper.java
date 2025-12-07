package com.example.client.controller;

import com.example.client.net.RmiClient;
import com.example.client.store.SessionStore;
import com.example.client.util.AudioHelper;
import com.example.common.dto.MessageDTO;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
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

    // 1. Biến tham chiếu về MainController để gọi các hàm xử lý
    private static MainController mainController;

    public static void setMainController(MainController mc) {
        mainController = mc;
    }

    // 2. Hàm chính: Thêm tin nhắn và trả về Bong bóng chat (VBox) để quản lý
    public static VBox addMessageBubble(VBox msgContainer, ScrollPane msgScrollPane, MessageDTO msg, boolean isMe) {

        // Xử lý Lazy load (Ảnh/File/Audio chưa có data)
        if (isMediaMessage(msg) && msg.getFileData() == null && msg.getAttachmentUrl() != null) {
            return handleLazyLoading(msgContainer, msgScrollPane, msg, isMe);
        }

        Node contentNode;

        // Tạo nội dung dựa trên loại tin nhắn
        if (msg.getType() == MessageDTO.MessageType.RECALL) {
            Label lbl = new Label("🚫 Tin nhắn đã thu hồi");
            lbl.setStyle("-fx-font-style: italic; -fx-text-fill: #888888;");
            contentNode = lbl;
        }
        else if (msg.getType() == MessageDTO.MessageType.TEXT) {
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
            // Fallback cho các loại khác hoặc lỗi
            Label lbl = new Label(msg.getContent() != null ? msg.getContent() : "Tin nhắn không xác định");
            lbl.getStyleClass().add(isMe ? "text-me" : "text-other");
            contentNode = lbl;
        }

        // Đóng gói nội dung vào bong bóng
        VBox bubble = new VBox(contentNode);
        bubble.getStyleClass().add(isMe ? "bubble-me" : "bubble-other");

        // --- XỬ LÝ MENU CHUỘT PHẢI (Context Menu) ---
        // Chỉ hiện menu nếu là tin nhắn của mình, chưa bị thu hồi và controller đã được set
        if (isMe && msg.getType() != MessageDTO.MessageType.RECALL && mainController != null) {
            ContextMenu contextMenu = new ContextMenu();

            // Menu: Chỉnh sửa (Chỉ áp dụng cho tin nhắn văn bản)
            if (msg.getType() == MessageDTO.MessageType.TEXT) {
                MenuItem editItem = new MenuItem("✏ Chỉnh sửa");
                editItem.setOnAction(e -> mainController.handleEditAction(msg));
                contextMenu.getItems().add(editItem);
            }

            // Menu: Thu hồi (Áp dụng cho mọi loại tin nhắn)
            MenuItem recallItem = new MenuItem("🚫 Thu hồi");
            recallItem.setOnAction(e -> mainController.handleRecallAction(msg));
            contextMenu.getItems().add(recallItem);

            // Gắn sự kiện click chuột phải
            bubble.setOnContextMenuRequested(e ->
                    contextMenu.show(bubble, e.getScreenX(), e.getScreenY()));
        }

        // Đóng gói vào layout hàng ngang (HBox) để căn trái/phải
        VBox messageBlock = new VBox(3);
        messageBlock.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        messageBlock.getChildren().add(bubble);

        // Hiển thị thời gian
        if (msg.getCreatedAt() != null) {
            Label timeLbl = new Label(msg.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm")));
            timeLbl.getStyleClass().add("time-label");
            messageBlock.getChildren().add(timeLbl);
        }

        HBox row = new HBox();
        row.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 10, 2, 10));
        row.getChildren().add(messageBlock);

        // Thêm vào giao diện (trên luồng JavaFX)
        Platform.runLater(() -> {
            msgContainer.getChildren().add(row);
            msgContainer.layout();
            msgScrollPane.layout();
            msgScrollPane.setVvalue(1.0);
        });

        return bubble; // Trả về để lưu vào Map quản lý
    }

    // 3. Hàm cập nhật giao diện khi có sự kiện Edit/Recall
    public static void updateBubbleContent(VBox bubble, String newContent, boolean isRecall) {
        bubble.getChildren().clear();
        Label lbl = new Label(newContent);

        if (isRecall) {
            // Style cho tin nhắn thu hồi
            lbl.setStyle("-fx-font-style: italic; -fx-text-fill: #888888;");
            bubble.getStyleClass().removeAll("bubble-me", "bubble-other");
            bubble.setStyle("-fx-background-color: #f0f0f0; -fx-background-radius: 18px; -fx-padding: 10 15;");
            // Xóa menu chuột phải
            bubble.setOnContextMenuRequested(null);
        } else {
            // Style cho tin nhắn chỉnh sửa (giữ nguyên style cũ của text)
            // Kiểm tra xem bubble gốc là của ai để set class text tương ứng
            boolean isMe = bubble.getStyleClass().contains("bubble-me");
            lbl.getStyleClass().add(isMe ? "text-me" : "text-other");
        }
        bubble.getChildren().add(lbl);
    }

    // 4. Hàm xử lý Lazy Loading (Cũng trả về VBox để quản lý)
    private static VBox handleLazyLoading(VBox msgContainer, ScrollPane msgScrollPane, MessageDTO msg, boolean isMe) {
        Label loadingLabel = new Label("⟳ Đang tải...");
        loadingLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 11px;");

        VBox bubble = new VBox(loadingLabel);
        bubble.getStyleClass().add(isMe ? "bubble-me" : "bubble-other");

        // Vẫn cho phép thu hồi khi đang tải
        if (isMe && mainController != null) {
            ContextMenu contextMenu = new ContextMenu();
            MenuItem recallItem = new MenuItem("🚫 Thu hồi");
            recallItem.setOnAction(e -> mainController.handleRecallAction(msg));
            contextMenu.getItems().add(recallItem);
            bubble.setOnContextMenuRequested(e -> contextMenu.show(bubble, e.getScreenX(), e.getScreenY()));
        }

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

        // Tải file ngầm
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

        return bubble;
    }

    // [FIX] Sửa lỗi ảnh trắng bằng StackPane + Bind
    private static Node createImageNode(byte[] imageData) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(imageData);
            Image image = new Image(bis);

            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(250);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);

            StackPane container = new StackPane(imageView);
            Rectangle clip = new Rectangle();
            clip.setArcWidth(20);
            clip.setArcHeight(20);

            clip.widthProperty().bind(container.widthProperty());
            clip.heightProperty().bind(container.heightProperty());
            container.setClip(clip);

            return container;
        } catch (Exception e) {
            return new Label("❌ Lỗi ảnh");
        }
    }

    // [FIX] Lấy tên file chuẩn xác
    private static Node createFileNode(VBox container, MessageDTO msg, boolean isMe) {
        String fName = msg.getFileName();
        if (fName == null || fName.isEmpty()) {
            if (msg.getContent() != null && msg.getContent().startsWith("[Tập tin] ")) {
                fName = msg.getContent().substring(10);
            } else {
                fName = "Tài liệu";
            }
        }

        String displayName = fName.length() > 25 ? fName.substring(0, 22) + "..." : fName;
        Button downloadBtn = new Button("📄 " + displayName);
        String textColor = isMe ? "white" : "#333333";
        downloadBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + textColor + "; -fx-cursor: hand; -fx-font-size: 14px;");

        String finalName = fName;
        downloadBtn.setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setInitialFileName(finalName);
            File file = fileChooser.showSaveDialog(downloadBtn.getScene().getWindow());
            if (file != null) {
                new Thread(() -> {
                    try {
                        byte[] data = msg.getFileData();
                        if (data == null && msg.getAttachmentUrl() != null) {
                            data = RmiClient.getMessageService().downloadFile(msg.getAttachmentUrl());
                        }
                        if (data != null) Files.write(file.toPath(), data);
                    } catch (Exception e) { e.printStackTrace(); }
                }).start();
            }
        });
        return downloadBtn;
    }

    private static Node createAudioNode(MessageDTO msg, boolean isMe) {
        Button playBtn = new Button("▶  Tin nhắn thoại");
        String textColor = isMe ? "white" : "#333333";
        playBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + textColor + "; -fx-font-weight: bold; -fx-cursor: hand; -fx-font-size: 14px;");

        playBtn.setOnAction(e -> {
            playBtn.setText("🔊 Đang phát...");
            playBtn.setDisable(true);
            new Thread(() -> {
                AudioHelper.playAudio(msg.getFileData());
                try { Thread.sleep(2000); } catch (Exception ex) {}
                Platform.runLater(() -> {
                    playBtn.setText("▶  Nghe lại");
                    playBtn.setDisable(false);
                });
            }).start();
        });
        return playBtn;
    }

    private static boolean isMediaMessage(MessageDTO msg) {
        return msg.getType() == MessageDTO.MessageType.IMAGE ||
                msg.getType() == MessageDTO.MessageType.FILE ||
                msg.getType() == MessageDTO.MessageType.AUDIO;
    }
}