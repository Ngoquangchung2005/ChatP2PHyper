package com.example.client.controller;

import com.example.common.dto.MessageDTO;
import javafx.application.Platform;
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

    // SỬA: Thay tham số String text -> MessageDTO msg
    public static void addMessageBubble(VBox msgContainer, ScrollPane msgScrollPane, MessageDTO msg, boolean isMe) {
        Node contentNode;

        if (msg.getType() == MessageDTO.MessageType.IMAGE && msg.getFileData() != null) {
            // --- XỬ LÝ HIỂN THỊ ẢNH ---
            try {
                ByteArrayInputStream bis = new ByteArrayInputStream(msg.getFileData());
                Image image = new Image(bis);
                ImageView imageView = new ImageView(image);
                imageView.setFitWidth(250); // Giới hạn chiều rộng
                imageView.setPreserveRatio(true);
                imageView.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 10, 0, 0, 0);");
                contentNode = imageView;
            } catch (Exception e) {
                contentNode = new Label("[Lỗi hiển thị ảnh]");
            }

        } else if (msg.getType() == MessageDTO.MessageType.FILE && msg.getFileData() != null) {
            // --- XỬ LÝ HIỂN THỊ FILE (Nút tải về) ---
            Button downloadBtn = new Button("📄 " + msg.getFileName() + " (Tải về)");
            downloadBtn.setStyle("-fx-background-color: #e4e6eb; -fx-text-fill: black; -fx-cursor: hand;");

            // Xử lý khi bấm vào nút file -> Lưu xuống máy
            downloadBtn.setOnAction(event -> {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setInitialFileName(msg.getFileName());
                File file = fileChooser.showSaveDialog(msgContainer.getScene().getWindow());
                if (file != null) {
                    try {
                        Files.write(file.toPath(), msg.getFileData());
                        System.out.println("Đã lưu file: " + file.getAbsolutePath());
                    } catch (Exception e) { e.printStackTrace(); }
                }
            });
            contentNode = downloadBtn;

        } else {
            // --- XỬ LÝ TEXT (Như cũ) ---
            Label label = new Label(msg.getContent());
            label.setWrapText(true);
            label.setMaxWidth(350);
            label.setStyle(isMe
                    ? "-fx-background-color: #0084ff; -fx-text-fill: white; -fx-background-radius: 15; -fx-padding: 8 12;"
                    : "-fx-background-color: #f0f0f0; -fx-text-fill: black; -fx-background-radius: 15; -fx-padding: 8 12;");
            contentNode = label;
        }

        HBox container = new HBox(contentNode);
        container.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        Platform.runLater(() -> {
            msgContainer.getChildren().add(container);
            msgScrollPane.layout();
            msgScrollPane.setVvalue(1.0);
        });
    }
}