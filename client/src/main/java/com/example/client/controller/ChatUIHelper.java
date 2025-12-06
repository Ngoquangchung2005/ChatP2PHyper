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
import javafx.stage.FileChooser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;

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

            // Tạo giao diện chờ (Loading...)
            Label loadingLabel = new Label("⟳ Đang tải dữ liệu...");
            loadingLabel.setStyle("-fx-text-fill: gray; -fx-font-style: italic; -fx-font-size: 12px;");

            HBox loadingBox = new HBox(loadingLabel);
            loadingBox.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            loadingBox.setPadding(new Insets(5, 0, 5, 0));

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
                            // Báo lỗi nếu không tải được
                            Label errorLabel = new Label("❌ Lỗi: File không tồn tại trên Server");
                            errorLabel.setStyle("-fx-text-fill: red;");
                            HBox errBox = new HBox(errorLabel);
                            errBox.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                            msgContainer.getChildren().add(errBox);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

            return; // Dừng hàm tại đây, chờ tải xong sẽ gọi lại
        }

        // --- 2. XỬ LÝ HIỂN THỊ THEO LOẠI TIN NHẮN ---
        Node contentNode;

        // A. HÌNH ẢNH
        if (msg.getType() == MessageDTO.MessageType.IMAGE && msg.getFileData() != null) {
            try {
                ByteArrayInputStream bis = new ByteArrayInputStream(msg.getFileData());
                Image image = new Image(bis);
                ImageView imageView = new ImageView(image);
                imageView.setFitWidth(250);
                imageView.setPreserveRatio(true);
                imageView.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 10, 0, 0, 0);");
                contentNode = imageView;
            } catch (Exception e) {
                contentNode = new Label("[Lỗi hiển thị ảnh]");
            }
        }

        // B. TIN NHẮN THOẠI (AUDIO)
        else if (msg.getType() == MessageDTO.MessageType.AUDIO && msg.getFileData() != null) {
            Button playBtn = new Button("▶ Nghe tin nhắn thoại");
            playBtn.setStyle("-fx-background-color: #e4e6eb; -fx-text-fill: black; -fx-cursor: hand; -fx-font-weight: bold; -fx-background-radius: 15;");

            playBtn.setOnAction(e -> {
                playBtn.setText("🔊 Đang phát...");
                playBtn.setDisable(true); // Chặn bấm liên tục

                // Gọi AudioHelper để phát
                AudioHelper.playAudio(msg.getFileData());

                // Reset nút sau 3 giây (hoặc bạn có thể tính thời gian chính xác từ file audio)
                new Thread(() -> {
                    try { Thread.sleep(3000); } catch (Exception ex) {}
                    Platform.runLater(() -> {
                        playBtn.setText("▶ Nghe lại");
                        playBtn.setDisable(false);
                    });
                }).start();
            });
            contentNode = playBtn;
        }

        // C. TỆP TIN (FILE)
        else if (msg.getType() == MessageDTO.MessageType.FILE && msg.getFileData() != null) {
            String fName = msg.getFileName() != null ? msg.getFileName() : "Tài liệu";
            Button downloadBtn = new Button("📄 " + fName + " (Tải về)");
            downloadBtn.setStyle("-fx-background-color: #e4e6eb; -fx-text-fill: black; -fx-cursor: hand; -fx-background-radius: 10;");

            downloadBtn.setOnAction(event -> {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setInitialFileName(fName);
                fileChooser.setTitle("Lưu file");
                File file = fileChooser.showSaveDialog(msgContainer.getScene().getWindow());
                if (file != null) {
                    try {
                        Files.write(file.toPath(), msg.getFileData());
                        System.out.println("Đã lưu file: " + file.getAbsolutePath());
                    } catch (Exception e) { e.printStackTrace(); }
                }
            });
            contentNode = downloadBtn;
        }

        // D. VĂN BẢN (TEXT) - Mặc định
        else {
            Label label = new Label(msg.getContent());
            label.setWrapText(true);
            label.setMaxWidth(350);

            // Style riêng cho Mình (Xanh) và Bạn (Xám)
            if (isMe) {
                label.setStyle("-fx-background-color: #0084ff; -fx-text-fill: white; -fx-background-radius: 15; -fx-padding: 8 12; -fx-font-size: 14px;");
            } else {
                label.setStyle("-fx-background-color: #f0f0f0; -fx-text-fill: black; -fx-background-radius: 15; -fx-padding: 8 12; -fx-font-size: 14px;");
            }
            contentNode = label;
        }

        // --- 3. ĐÓNG GÓI VÀO CONTAINER VÀ CUỘN ---
        HBox container = new HBox(contentNode);
        container.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        container.setPadding(new Insets(5, 0, 5, 0));

        Platform.runLater(() -> {
            msgContainer.getChildren().add(container);
            // Cuộn xuống dưới cùng
            msgScrollPane.layout();
            msgScrollPane.setVvalue(1.0);
        });
    }

    // Hàm phụ: Kiểm tra xem có phải tin nhắn cần dữ liệu file không
    private static boolean isMediaMessage(MessageDTO msg) {
        return msg.getType() == MessageDTO.MessageType.IMAGE ||
                msg.getType() == MessageDTO.MessageType.FILE ||
                msg.getType() == MessageDTO.MessageType.AUDIO;
    }
}