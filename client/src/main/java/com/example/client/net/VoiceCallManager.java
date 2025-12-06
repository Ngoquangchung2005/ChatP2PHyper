package com.example.client.net;

import javax.sound.sampled.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class VoiceCallManager {
    // Cấu hình âm thanh: 8000Hz (chuẩn điện thoại), 16-bit, Mono (1 kênh)
    private static final AudioFormat FORMAT = new AudioFormat(8000.0f, 16, 1, true, true);
    private static final int PACKET_SIZE = 1024; // Kích thước gói tin UDP

    private TargetDataLine microphone; // Thu âm
    private SourceDataLine speakers;   // Phát loa
    private DatagramSocket udpSocket;
    private boolean isCalling = false;

    private String targetIp;
    private int targetPort; // Port UDP của đối phương

    // Bắt đầu cuộc gọi (Khởi tạo Mic, Loa, Socket)
    public void startCall(String targetIp, int targetPort, int myUdpPort) {
        this.targetIp = targetIp;
        this.targetPort = targetPort;
        this.isCalling = true;

        try {
            // 1. Mở UDP Socket
            udpSocket = new DatagramSocket(myUdpPort);

            // 2. Mở Microphone
            DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, FORMAT);
            microphone = (TargetDataLine) AudioSystem.getLine(micInfo);
            microphone.open(FORMAT);
            microphone.start();

            // 3. Mở Loa
            DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, FORMAT);
            speakers = (SourceDataLine) AudioSystem.getLine(speakerInfo);
            speakers.open(FORMAT);
            speakers.start();

            // 4. Chạy 2 luồng song song: Gửi và Nhận
            new Thread(this::sendAudio).start();
            new Thread(this::receiveAudio).start();

            System.out.println("Đã bắt đầu cuộc gọi Voice P2P!");

        } catch (Exception e) {
            e.printStackTrace();
            stopCall();
        }
    }

    // Luồng thu âm và gửi đi
    private void sendAudio() {
        byte[] buffer = new byte[PACKET_SIZE];
        try {
            while (isCalling) {
                // Đọc dữ liệu từ Mic
                int bytesRead = microphone.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    // Gửi qua UDP
                    InetAddress address = InetAddress.getByName(targetIp);
                    DatagramPacket packet = new DatagramPacket(buffer, bytesRead, address, targetPort);
                    udpSocket.send(packet);
                }
            }
        } catch (Exception e) {
            if (isCalling) e.printStackTrace();
        }
    }

    // Luồng nhận dữ liệu và phát ra loa
    private void receiveAudio() {
        byte[] buffer = new byte[PACKET_SIZE];
        try {
            while (isCalling) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet); // Chặn chờ nhận gói tin

                // Ghi dữ liệu ra Loa
                speakers.write(packet.getData(), 0, packet.getLength());
            }
        } catch (Exception e) {
            if (isCalling) e.printStackTrace();
        }
    }

    // Kết thúc cuộc gọi và giải phóng tài nguyên
    public void stopCall() {
        isCalling = false;
        if (udpSocket != null && !udpSocket.isClosed()) udpSocket.close();
        if (microphone != null) { microphone.close(); microphone = null; }
        if (speakers != null) { speakers.close(); speakers = null; }
        System.out.println("Đã kết thúc cuộc gọi.");
    }

    public boolean isCalling() { return isCalling; }
}