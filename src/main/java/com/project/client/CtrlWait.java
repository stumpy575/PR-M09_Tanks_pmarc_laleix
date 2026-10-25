package com.project.client;

import java.net.URL;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;

public class CtrlWait implements Initializable {

    @FXML
    public Label txtTitle;

    @FXML
    public Label txtPlayer0;

    @FXML
    public Label txtPlayer1;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        txtPlayer0.setText("Esperando jugador 1...");
        txtPlayer1.setText("Esperando jugador 2...");
    }

    public void updatePlayers(String[] players) {
        Platform.runLater(() -> {
            txtPlayer0.setText(
                    players.length > 0 ? players[0] : "Esperando jugador 1..."
            );

            txtPlayer1.setText(
                    players.length > 1 ? players[1] : "Esperando jugador 2..."
            );
        });
    }

    public void updateCountdown(int seconds) {
        Platform.runLater(() -> {
            if (seconds > 0) {
                txtTitle.setText("La partida empieza en " + seconds + "...");
            } else {
                txtTitle.setText("¡Empieza la partida!");
            }
        });
    }
}