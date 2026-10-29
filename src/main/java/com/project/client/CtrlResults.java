package com.project.client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class CtrlResults {

    @FXML
    public Label txtResult;

    public void showResults(String winner, String loser) {
        Platform.runLater(() -> {
            if (winner == null || winner.isBlank()) {
                txtResult.setText("La partida ha terminado.");
                return;
            }

            if (loser == null || loser.isBlank()) {
                txtResult.setText("¡Ha ganado " + winner + "!");
            } else {
                txtResult.setText(
                    "¡Ha ganado " + winner + "!\n\n" +
                    "Perdedor: " + loser
                );
            }

            UtilsViews.setView("ViewResults");
        });
    }

    @FXML
    private void connectToServer() {
        Main.ctrlConfig.connectToServer();
    }
}