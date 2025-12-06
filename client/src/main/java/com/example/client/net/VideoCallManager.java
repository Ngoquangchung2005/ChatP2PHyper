package com.example.client.net;

import com.github.sarxos.webcam.Webcam;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class VideoCallManager {
    private DatagramSocket udpSocket;
    private boolean isCalling = false;
    private Webcam webcam;

    private String targetIp;
    private int targetPort;

    private ImageView myView;
    private ImageView partnerView;

    // 320x240 là đủ cho chat video P2P UDP
    private static final Dimension RESOLUTION = new Dimension(320, 240);

    public void setupUI(ImageView myView, ImageView partnerView) {
        this.myView = myView;
        this.partnerView = partnerView;
    }

    public void startVideo(String targetIp, int targetPort, int myVideoPort) {
        this.targetIp = targetIp;
        this.targetPort = targetPort;
        this.isCalling = true;

        // [QUAN TRỌNG] Chạy khởi tạo Webcam trong luồng riêng để không đơ UI
        new Thread(() -> {
            try {
                // 1. Mở Webcam (Hàm này rất tốn thời gian)
                webcam = Webcam.getDefault();
                if (webcam != null) {
                    try {
                        webcam.setViewSize(RESOLUTION);
                        webcam.open();
                    } catch (Exception e) {
                        System.err.println("[CẢNH BÁO] Không thể mở Webcam (có thể đang được dùng bởi App khác): " + e.getMessage());
                        webcam = null;
                    }
                } else {
                    System.err.println("Không tìm thấy Webcam!");
                }

                // 2. Mở Socket UDP
                udpSocket = new DatagramSocket(myVideoPort);

                // 3. Bắt đầu gửi video (nếu webcam mở thành công)
                if (webcam != null && webcam.isOpen()) {
                    sendVideo();
                }

            } catch (Exception e) {
                e.printStackTrace();
                stopVideo();
            }
        }).start();

        // 4. Luôn chạy luồng nhận video (để xem đối phương dù mình không có cam)
        new Thread(this::receiveVideo).start();
    }

    // Luồng gửi video đi
    private void sendVideo() {
        try {
            while (isCalling && webcam != null && webcam.isOpen()) {
                // Lấy ảnh từ webcam
                BufferedImage bImage = webcam.getImage();
                if (bImage == null) continue;

                // Hiển thị lên UI của mình (Mirror)
                Image fxImage = SwingFXUtils.toFXImage(bImage, null);
                Platform.runLater(() -> {
                    if (myView != null) myView.setImage(fxImage);
                });

                // Nén ảnh sang JPEG
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(bImage, "jpg", baos);
                byte[] data = baos.toByteArray();

                // Gửi qua UDP (Giới hạn gói tin UDP ~60KB)
                if (data.length < 60000) {
                    InetAddress address = InetAddress.getByName(targetIp);
                    DatagramPacket packet = new DatagramPacket(data, data.length, address, targetPort);
                    udpSocket.send(packet);
                }

                // Giới hạn FPS (~25 FPS)
                Thread.sleep(40);
            }
        } catch (Exception e) {
            if (isCalling) e.printStackTrace();
        }
    }

    // Luồng nhận video về
    private void receiveVideo() {
        byte[] buffer = new byte[65000]; // Buffer đủ lớn cho 1 frame ảnh
        try {
            while (isCalling) {
                if (udpSocket == null || udpSocket.isClosed()) {
                    Thread.sleep(100);
                    continue;
                }

                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet); // Chặn chờ nhận gói tin

                // Chuyển byte[] thành Image
                ByteArrayInputStream bais = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
                BufferedImage bImage = ImageIO.read(bais);

                if (bImage != null) {
                    Image fxImage = SwingFXUtils.toFXImage(bImage, null);
                    // Update lên UI (Thread an toàn)
                    Platform.runLater(() -> {
                        if (partnerView != null) partnerView.setImage(fxImage);
                    });
                }
            }
        } catch (Exception e) {
            if (isCalling) e.printStackTrace();
        }
    }

    public void stopVideo() {
        isCalling = false;
        try {
            if (webcam != null) webcam.close();
            if (udpSocket != null && !udpSocket.isClosed()) udpSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}