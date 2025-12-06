package com.example.client.controller;

import com.example.client.net.RmiClient;
import com.example.common.dto.UserDTO;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;

import java.io.ByteArrayInputStream;

public class ChatInfoController {
    @FXML private ImageView avatarView;
    @FXML private Label nameLabel;

    public void setUserInfo(UserDTO user) {
        if (user == null) return;

        // Set tên
        nameLabel.setText(user.getDisplayName());

        // Reset avatar về mặc định (tránh hiện ảnh người cũ khi mạng lag)
        avatarView.setImage(null);

        // Load avatar mới
        if (user.getAvatarUrl() != null) {
            new Thread(() -> {
                try {
                    byte[] data = RmiClient.getMessageService().downloadFile(user.getAvatarUrl());
                    if (data != null) {
                        Image img = new Image(new ByteArrayInputStream(data));
                        Platform.runLater(() -> {
                            avatarView.setImage(img);

                            // Cắt tròn ảnh
                            double r = avatarView.getFitWidth() / 2;
                            Circle clip = new Circle(r, r, r);
                            avatarView.setClip(clip);
                        });
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }).start();
        }
    }
}