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
import com.example.client.util.NetworkUtil; // Import mới

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
// 1. Tự động lấy IP thật của máy
            String myIp = NetworkUtil.getMyLanIp();
            int myPort = generateRandomPort();

            System.out.println("My P2P Address: " + myIp + ":" + myPort);


            // GỌI RMI SANG SERVER
            UserDTO user = RmiClient.getAuthService().login(username, password, myIp, myPort);
            if (user != null) {
                // Cập nhật lại thông tin chính xác vào Store
                user.setLastIp(myIp);
                user.setLastPort(myPort);

                SessionStore.currentUser = user;
                SessionStore.p2pPort = myPort;

                Navigation.switchScene(event, "/view/main-view.fxml", "Hybrid Messenger");
            } else {
                showAlert("Thất bại", "Sai tên đăng nhập hoặc mật khẩu!");
            }
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Lỗi mạng", "Không thể kết nối tới Server. Kiểm tra file client.properties!");
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