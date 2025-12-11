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

        // --- [1] TẠO NỘI DUNG DỰA TRÊN LOẠI TIN NHẮN ---
        if (msg.getType() == MessageDTO.MessageType.RECALL) {
            Label lbl = new Label("🚫 Tin nhắn đã thu hồi");
            lbl.setStyle("-fx-font-style: italic; -fx-text-fill: #888888;");
            contentNode = lbl;
        }
        // [MỚI] Xử lý hiển thị thông báo (Notification)
        else if (msg.getType() == MessageDTO.MessageType.NOTIFICATION) {
            Label lbl = new Label(msg.getContent());
            // Style: Chữ xám, nghiêng, nền xám nhạt, bo tròn
            lbl.setStyle("-fx-text-fill: #888888; -fx-font-size: 12px; -fx-font-style: italic; -fx-padding: 5 10; -fx-background-color: #f0f0f0; -fx-background-radius: 10;");
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
            Label lbl = new Label(msg.getContent() != null ? msg.getContent() : "Tin nhắn không xác định");
            lbl.getStyleClass().add(isMe ? "text-me" : "text-other");
            contentNode = lbl;
        }

        // Đóng gói nội dung vào bong bóng
        VBox bubble = new VBox(contentNode);

        // [MỚI] Chỉ thêm class bong bóng chat nếu KHÔNG PHẢI là thông báo
        if (msg.getType() != MessageDTO.MessageType.NOTIFICATION) {
            bubble.getStyleClass().add(isMe ? "bubble-me" : "bubble-other");
        } else {
            // Căn giữa nội dung bên trong bong bóng thông báo
            bubble.setAlignment(Pos.CENTER);
        }

        // --- [2] TẠO HÀNG CHỨA (NÚT 3 CHẤM + BONG BÓNG) ---
        HBox contentRow = new HBox(5); // Khoảng cách 5px

        // [MỚI] Nếu là Notification thì CĂN GIỮA, ngược lại thì theo isMe
        if (msg.getType() == MessageDTO.MessageType.NOTIFICATION) {
            contentRow.setAlignment(Pos.CENTER);
            contentRow.getChildren().add(bubble);
        }
        else {
            // --- LOGIC CŨ CHO TIN NHẮN CHAT ---
            contentRow.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

            // Chỉ hiện nút 3 chấm nếu là tin nhắn CỦA MÌNH và CHƯA BỊ THU HỒI
            if (isMe && msg.getType() != MessageDTO.MessageType.RECALL && mainController != null) {

                // 1. Tạo nút 3 chấm
                Button optionsBtn = new Button("⋮");
                optionsBtn.getStyleClass().add("btn-msg-options");

                // 2. Tạo Menu
                ContextMenu contextMenu = new ContextMenu();

                // Menu: Chỉnh sửa (Chỉ cho tin nhắn văn bản)
                if (msg.getType() == MessageDTO.MessageType.TEXT) {
                    MenuItem editItem = new MenuItem("✏ Chỉnh sửa");
                    editItem.setOnAction(e -> mainController.handleEditAction(msg));
                    contextMenu.getItems().add(editItem);
                }

                // Menu: Thu hồi
                MenuItem recallItem = new MenuItem("🚫 Thu hồi");
                recallItem.setOnAction(e -> mainController.handleRecallAction(msg));
                contextMenu.getItems().add(recallItem);

                // 3. Sự kiện bấm nút 3 chấm -> Hiện menu
                optionsBtn.setOnAction(e -> {
                    contextMenu.show(optionsBtn, javafx.geometry.Side.BOTTOM, 0, 0);
                });

                // 4. Thêm vào row: [Nút 3 chấm] [Bong bóng]
                contentRow.getChildren().addAll(optionsBtn, bubble);
            } else {
                // Tin nhắn người khác hoặc đã thu hồi -> Chỉ hiện bong bóng
                contentRow.getChildren().add(bubble);
            }
        }

        // --- [3] ĐÓNG GÓI VÀO KHỐI BLOCK (CHỨA CẢ THỜI GIAN) ---
        VBox messageBlock = new VBox(3);

        // [MỚI] Căn chỉnh block tổng thể
        if (msg.getType() == MessageDTO.MessageType.NOTIFICATION) {
            messageBlock.setAlignment(Pos.CENTER);
        } else {
            messageBlock.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        }

        messageBlock.getChildren().add(contentRow);

        // Hiển thị thời gian (Notification cũng có thời gian, nhưng sẽ được căn giữa theo block)
        if (msg.getCreatedAt() != null) {
            Label timeLbl = new Label(msg.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm")));
            timeLbl.getStyleClass().add("time-label");
            messageBlock.getChildren().add(timeLbl);
        }

        // --- [4] TẠO HÀNG CUỐI CÙNG ADD VÀO CONTAINER ---
        HBox row = new HBox();

        // [MỚI] Căn chỉnh hàng trong ListView
        if (msg.getType() == MessageDTO.MessageType.NOTIFICATION) {
            row.setAlignment(Pos.CENTER);
        } else {
            row.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        }

        row.setPadding(new Insets(2, 10, 2, 10));
        row.getChildren().add(messageBlock);

        // Thêm vào giao diện
        Platform.runLater(() -> {
            msgContainer.getChildren().add(row);
            msgContainer.layout();
            msgScrollPane.layout();
            msgScrollPane.setVvalue(1.0);
        });

        return bubble;
    }
    public static void updateBubbleContent(VBox bubble, String newContent, boolean isRecall) {
        Platform.runLater(() -> {
            bubble.getChildren().clear();
            Label lbl = new Label(newContent);

            if (isRecall) {
                // Style cho tin nhắn thu hồi
                lbl.setStyle("-fx-font-style: italic; -fx-text-fill: #888888;");
                bubble.getStyleClass().removeAll("bubble-me", "bubble-other");
                bubble.setStyle("-fx-background-color: #f0f0f0; -fx-background-radius: 18px; -fx-padding: 10 15;");

                // [MỚI] Xóa nút 3 chấm nếu có
                if (bubble.getParent() instanceof HBox) {
                    HBox parentRow = (HBox) bubble.getParent();
                    // Tìm nút button trong row cha và xóa nó đi
                    parentRow.getChildren().removeIf(node -> node instanceof Button && node.getStyleClass().contains("btn-msg-options"));
                }
            } else {
                // Style cho tin nhắn chỉnh sửa
                boolean isMe = bubble.getStyleClass().contains("bubble-me");
                lbl.getStyleClass().add(isMe ? "text-me" : "text-other");
            }
            bubble.getChildren().add(lbl);
        });
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