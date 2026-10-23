package com.project.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class Main extends Application {

    public static CtrlConfig ctrlConfig;
    public static CtrlWait ctrlWait;
    //public static CtrlPlay ctrlPlay;

    @Override
    public void start(Stage stage) throws Exception {

        final int windowWidth = 400;
        final int windowHeight = 300;

        UtilsViews.parentContainer.setStyle("-fx-font: 14 arial;");
        UtilsViews.addView(getClass(), "ViewConfig", "/assets/viewConfig.fxml"); 
        UtilsViews.addView(getClass(), "ViewWait", "/assets/viewWait.fxml");
        UtilsViews.addView(getClass(), "ViewPlay", "/assets/viewPlay.fxml");

        ctrlConfig = (CtrlConfig) UtilsViews.getController("ViewConfig");
        ctrlWait = (CtrlWait) UtilsViews.getController("ViewWait");
        //ctrlPlay = (CtrlPlay) UtilsViews.getController("ViewPlay");

        Scene scene = new Scene(UtilsViews.parentContainer);
        
        stage.setScene(scene);
        stage.onCloseRequestProperty(); // Call close method when closing window
        stage.setTitle("JavaFX");
        stage.setMinWidth(windowWidth);
        stage.setMinHeight(windowHeight);
        stage.show();

        // Add icon only if not Mac
        if (!System.getProperty("os.name").contains("Mac")) {
            Image icon = new Image("file:/icons/icon.png");
            stage.getIcons().add(icon);
        }




        // // Carrega l'única vista de l'exemple
        // Parent root = FXMLLoader.load(getClass().getResource("/assets/game.fxml"));
        // Scene scene = new Scene(root);
// 
        // stage.setScene(scene);
        // stage.setTitle("Exemple 0606 - Tanks");
        // stage.setResizable(false);
// 
        // if (!System.getProperty("os.name").contains("Mac")) {
        //     Image icon = new Image(getClass().getResourceAsStream("/icons/icon.png"));
        //     stage.getIcons().add(icon);
        // }
// 
        // stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
