package com.example.client.controller;

import com.example.client.net.RmiClient;
import com.example.client.store.SessionStore;
import com.example.client.util.Navigation;
import com.example.common.dto.UserDTO;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;

    @FXML
    public void initialize() {
        // Tự động kết nối Server khi mở màn hình
        RmiClient.connect();
    }
    // Thêm hàm lấy port ngẫu nhiên
    private int generateRandomPort() {
        return 6000 + (int)(Math.random() * 1000);
    }

    @FXML
    public void handleLogin(ActionEvent event) {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showAlert("Lỗi", "Vui lòng nhập đầy đủ thông tin!");
            return;
        }

        try {
//            // --- SỬA ĐOẠN NÀY ---
//            // Code cũ: String myIp = "127.0.0.1";
//
//            // Code mới: Tự động lấy IP LAN của máy đang chạy Client
//            String myIp = InetAddress.getLocalHost().getHostAddress();
//
//            System.out.println("IP của tôi là: " + myIp); // In ra để kiểm tra
            // Lấy IP thật của máy (tạm thời để localhost nếu chạy 1 máy)
            String myIp = "127.0.0.1";
            int myPort = generateRandomPort(); // <--- Tạo port mới

            // GỌI RMI SANG SERVER
            UserDTO user = RmiClient.getAuthService().login(username, password, myIp, myPort);
            if (user != null) {
                SessionStore.currentUser = user;
                SessionStore.p2pPort = myPort; // <--- Lưu lại để MainController dùng

                // Chuyển sang màn hình Chat chính
                Navigation.switchScene(event, "/view/main-view.fxml", "Hybrid Messenger");
            } else {
                showAlert("Thất bại", "Sai tên đăng nhập hoặc mật khẩu!");
            }

        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Lỗi mạng", "Không thể kết nối tới Server: " + e.getMessage());
        }
    }

    // Sửa hàm handleRegister cũ
    @FXML
    public void handleRegister(ActionEvent event) { // Thêm tham số ActionEvent event vào FXML nữa nhé
        Navigation.switchScene(event, "/view/register-view.fxml", "Đăng ký tài khoản");
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}