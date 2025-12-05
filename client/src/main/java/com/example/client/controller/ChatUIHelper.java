package com.example.client.controller;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class ChatUIHelper {

    // Hàm static để vẽ bong bóng chat
    public static void addMessageBubble(VBox msgContainer, ScrollPane msgScrollPane, String text, boolean isMe) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(350);

        // CSS Style
        label.setStyle(isMe
                ? "-fx-background-color: #0084ff; -fx-text-fill: white; -fx-background-radius: 15; -fx-padding: 8 12;"
                : "-fx-background-color: #f0f0f0; -fx-text-fill: black; -fx-background-radius: 15; -fx-padding: 8 12;");

        HBox container = new HBox(label);
        container.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        // Cập nhật UI phải ở trên luồng JavaFX
        Platform.runLater(() -> {
            msgContainer.getChildren().add(container);
            // Tự động cuộn xuống dưới cùng
            msgScrollPane.layout();
            msgScrollPane.setVvalue(1.0);
        });
    }
}