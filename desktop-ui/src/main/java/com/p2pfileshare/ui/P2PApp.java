package com.p2pfileshare.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class P2PApp extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/main.fxml"));
        Scene scene = new Scene(loader.load());
        MainController controller = loader.getController();
        scene.getStylesheets().add(getClass().getResource("/ui/dark.css").toExternalForm());
        stage.setTitle("P2P File Share");
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setOnCloseRequest(event -> controller.shutdown());
        stage.show();
    }
}
