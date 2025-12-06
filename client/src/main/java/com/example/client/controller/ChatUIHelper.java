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

    /**
     * Hàm thêm bong bóng chat vào giao diện.
     * Tự động xử lý Text, Ảnh, File, Audio và tải dữ liệu từ Server nếu cần.
     *
     * @param msgContainer   VBox chứa danh sách tin nhắn
     * @param msgScrollPane  ScrollPane để cuộn xuống dưới
     * @param msg            Đối tượng tin nhắn
     * @param isMe           True nếu là tin nhắn của mình, False nếu là của bạn
     */
    public static void addMessageBubble(VBox msgContainer, ScrollPane msgScrollPane, MessageDTO msg, boolean isMe) {

        // --- 1. CƠ CHẾ LAZY LOADING (Tải file từ Server nếu thiếu dữ liệu) ---
        // Điều kiện: Là tin nhắn đa phương tiện + Không có dữ liệu byte[] + Có link Server
        if (isMediaMessage(msg) && msg.getFileData() == null && msg.getAttachmentUrl() != null) {
            handleLazyLoading(msgContainer, msgScrollPane, msg, isMe);
            return;
        }

        // --- 2. XỬ LÝ HIỂN THỊ THEO LOẠI TIN NHẮN ---
        Node contentNode;

        // A. VĂN BẢN (TEXT)
        if (msg.getType() == MessageDTO.MessageType.TEXT) {
            Text text = new Text(msg.getContent());
            text.getStyleClass().add(isMe ? "text-me" : "text-other"); // Class CSS: text màu trắng hoặc xám nhạt

            TextFlow textFlow = new TextFlow(text);
            textFlow.setMaxWidth(350); // Giới hạn chiều rộng tin nhắn để text tự xuống dòng
            contentNode = textFlow;
        }
        // B. HÌNH ẢNH
        else if (msg.getType() == MessageDTO.MessageType.IMAGE && msg.getFileData() != null) {
            contentNode = createImageNode(msg.getFileData());
        }
        // C. TIN NHẮN THOẠI (AUDIO)
        else if (msg.getType() == MessageDTO.MessageType.AUDIO && msg.getFileData() != null) {
            contentNode = createAudioNode(msg);
        }
        // D. TỆP TIN (FILE)
        else if (msg.getType() == MessageDTO.MessageType.FILE && msg.getFileData() != null) {
            contentNode = createFileNode(msgContainer, msg);
        }
        // Fallback
        else {
            contentNode = new Label(msg.getContent());
        }

        // --- 3. ĐÓNG GÓI VÀO BONG BÓNG (BUBBLE) ---
        VBox bubble = new VBox(contentNode);
        // Áp dụng class CSS: bubble-me (Xanh) hoặc bubble-other (Xám tối)
        bubble.getStyleClass().add(isMe ? "bubble-me" : "bubble-other");

        // --- 4. HIỂN THỊ THỜI GIAN (Tùy chọn) ---
        VBox messageBlock = new VBox(2); // Container chứa Bubble + Time
        messageBlock.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        messageBlock.getChildren().add(bubble);

        if (msg.getCreatedAt() != null) {
            Label timeLbl = new Label(msg.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm")));
            timeLbl.getStyleClass().add("time-label"); // CSS: chữ nhỏ, màu xám
            messageBlock.getChildren().add(timeLbl);
        }

        // --- 5. LAYOUT CHÍNH (HBOX) ---
        HBox row = new HBox(messageBlock);
        row.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 0, 2, 0)); // Khoảng cách giữa các tin nhắn

        Platform.runLater(() -> {
            msgContainer.getChildren().add(row);
            // Cuộn xuống dưới cùng sau khi layout xong
            msgContainer.layout();
            msgScrollPane.layout();
            msgScrollPane.setVvalue(1.0);
        });
    }

    // --- CÁC HÀM TẠO NODE CON ---

    private static Node createImageNode(byte[] imageData) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(imageData);
            Image image = new Image(bis);
            ImageView imageView = new ImageView(image);

            imageView.setFitWidth(250); // Kích thước hiển thị tối đa
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);

            // Bo tròn góc ảnh
            Rectangle clip = new Rectangle(250, 250); // Kích thước placeholder
            // Cập nhật kích thước clip theo ảnh thật sau khi load
            if (image.getWidth() > 0) {
                double aspect = image.getHeight() / image.getWidth();
                clip.setWidth(250);
                clip.setHeight(250 * aspect);
            }
            clip.setArcWidth(20);
            clip.setArcHeight(20);
            imageView.setClip(clip);

            return imageView;
        } catch (Exception e) {
            return new Label("[Ảnh lỗi]");
        }
    }

    private static Node createAudioNode(MessageDTO msg) {
        Button playBtn = new Button("▶  Tin nhắn thoại");
        // Style nút Audio cho hợp Dark Mode
        playBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: inherit; -fx-font-weight: bold; -fx-cursor: hand; -fx-alignment: CENTER_LEFT;");

        playBtn.setOnAction(e -> {
            playBtn.setText("🔊 Đang phát...");
            playBtn.setDisable(true); // Chặn bấm liên tục

            AudioHelper.playAudio(msg.getFileData());

            // Reset nút sau 3 giây (giả lập thời gian phát)
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

    private static Node createFileNode(VBox msgContainer, MessageDTO msg) {
        String fName = msg.getFileName() != null ? msg.getFileName() : "Tài liệu";
        Button downloadBtn = new Button("📄 " + fName);
        downloadBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: inherit; -fx-cursor: hand; -fx-alignment: CENTER_LEFT;");

        downloadBtn.setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setInitialFileName(fName);
            fileChooser.setTitle("Lưu file");
            File file = fileChooser.showSaveDialog(msgContainer.getScene().getWindow());
            if (file != null) {
                try {
                    Files.write(file.toPath(), msg.getFileData());
                } catch (Exception e) { e.printStackTrace(); }
            }
        });
        return downloadBtn;
    }

    // --- LOGIC LAZY LOADING ---

    private static void handleLazyLoading(VBox msgContainer, ScrollPane msgScrollPane, MessageDTO msg, boolean isMe) {
        // Tạo giao diện chờ (Loading...)
        Label loadingLabel = new Label("⟳ Đang tải...");
        loadingLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 11px; -fx-padding: 5;");

        HBox loadingBox = new HBox(loadingLabel);
        loadingBox.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        Platform.runLater(() -> msgContainer.getChildren().add(loadingBox));

        // Chạy luồng tải file ngầm
        new Thread(() -> {
            try {
                // Gọi RMI để tải file từ Server
                byte[] downloadedData = RmiClient.getMessageService().downloadFile(msg.getAttachmentUrl());

                Platform.runLater(() -> {
                    // Xóa dòng "Đang tải..."
                    msgContainer.getChildren().remove(loadingBox);

                    if (downloadedData != null) {
                        // Cập nhật dữ liệu vào tin nhắn và vẽ lại giao diện chuẩn
                        msg.setFileData(downloadedData);
                        addMessageBubble(msgContainer, msgScrollPane, msg, isMe);
                    } else {
                        // Báo lỗi nhẹ nhàng
                        Label errorLabel = new Label("❌ Lỗi tải file");
                        errorLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11px;");
                        HBox errBox = new HBox(errorLabel);
                        errBox.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                        msgContainer.getChildren().add(errBox);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // --- UTILS ---
    private static boolean isMediaMessage(MessageDTO msg) {
        return msg.getType() == MessageDTO.MessageType.IMAGE ||
                msg.getType() == MessageDTO.MessageType.FILE ||
                msg.getType() == MessageDTO.MessageType.AUDIO;
    }
}