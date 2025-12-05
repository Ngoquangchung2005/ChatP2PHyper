package com.example.client;

import com.example.client.net.RmiClient;
import com.example.client.store.SessionStore;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ClientApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        // Tải màn hình đăng nhập
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/view/login-view.fxml"));
        Parent root = fxmlLoader.load();

        Scene scene = new Scene(root);
        stage.setTitle("Hybrid Messenger");
        stage.setScene(scene);
        stage.show();
    }

    // --- SỬA LỖI: HÀM NÀY CHẠY KHI BẠN BẤM DẤU X ĐỂ TẮT APP ---
    @Override
    public void stop() throws Exception {
        System.out.println("Ứng dụng đang tắt...");

        // Nếu đã đăng nhập thì gọi Logout để Server biết
        if (SessionStore.currentUser != null) {
            try {
                // Gọi hàm logout trên Server
                RmiClient.getAuthService().logout(SessionStore.currentUser.getId());
                System.out.println("Đã đăng xuất thành công!");
            } catch (Exception e) {
                System.err.println("Lỗi khi đăng xuất: " + e.getMessage());
            }
        }

        // Tắt hẳn chương trình (bao gồm các luồng P2P đang chạy ngầm)
        System.exit(0);
    }

    public static void main(String[] args) {
        launch();
    }
}