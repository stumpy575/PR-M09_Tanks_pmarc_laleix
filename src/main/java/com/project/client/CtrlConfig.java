package com.project.client;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.application.Platform;

public class CtrlConfig implements Initializable {

    @FXML
    public TextField txtProtocol;

    @FXML
    public TextField txtHost;

    @FXML
    public TextField txtPort;

    @FXML
    public TextField txtPlayerName;

    @FXML
    public Label txtMessage;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
    }

    @FXML
    private void connectToServer() {

        // Obtener los datos introducidos
        String protocol = txtProtocol.getText().trim();
        String host = txtHost.getText().trim();
        String port = txtPort.getText().trim();
        String name = txtPlayerName.getText().trim();

        // Comprobar que el jugador ha escrito su nombre
        if (name.isEmpty()) {
            txtMessage.setText("Introduce tu nombre");
            return;
        }

        // Comprobar los datos del servidor
        if (protocol.isEmpty() || host.isEmpty() || port.isEmpty()) {
            txtMessage.setText("Completa los datos del servidor");
            return;
        }

        // Guardar el nombre del jugador
        Main.playerName = name;

        // Construir la dirección WebSocket
        String url = protocol + "://" + host + ":" + port;

        // Mostrar mensaje
        txtMessage.setText("Conectando...");

        // Crear la conexión utilizando UtilsWS
        Main.wsClient = UtilsWS.getSharedInstance(url);

        // Conectar al servidor
        Main.wsClient.connect();

        // Cuando se conecte correctamente
        Main.wsClient.onOpen(message -> {

            Platform.runLater(() -> {

                txtMessage.setText("Conectado correctamente");

                UtilsViews.setView("ViewWait");

            });

        });

        // Cuando ocurra un error
        Main.wsClient.onError(message -> {

            Platform.runLater(() -> {

                txtMessage.setText("Error de conexión: " + message);

            });

        });

        // Cuando se cierre la conexión
        Main.wsClient.onClose(message -> {

            Platform.runLater(() -> {

                txtMessage.setText("Conexión cerrada");

            });

        });
    }

    @FXML
    private void setConfigLocal() {
        txtProtocol.setText("ws");
        txtHost.setText("localhost");
        txtPort.setText("3000");
    }

    @FXML
    private void setConfigProxmox() {
        txtProtocol.setText("wss");
        txtHost.setText("user.ieti.site");
        txtPort.setText("443");
    }
}