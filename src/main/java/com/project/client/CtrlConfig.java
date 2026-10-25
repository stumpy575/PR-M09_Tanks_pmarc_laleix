package com.project.client;

import java.net.URL;
import java.util.ResourceBundle;

import org.json.JSONArray;
import org.json.JSONObject;
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

        // Cuando se conecte correctamente
        Main.wsClient.onOpen(message -> {

            // Enviar al servidor el nombre elegido por el jugador
            JSONObject nameMessage = new JSONObject()
                .put("type", "setName")
                .put("value", Main.playerName);

            Main.wsClient.safeSend(nameMessage.toString());
        
            Platform.runLater(() -> {
            
                txtMessage.setText("Conectado correctamente");
            
                UtilsViews.setView("ViewWait");
            
            });
        
        });

        // Cuando se reciba un mensaje del servidor
        Main.wsClient.onMessage(message -> {
        
            try {
                JSONObject obj = new JSONObject(message);
            
                // Procesar los datos de los jugadores
                if ("serverData".equals(obj.optString("type"))) {
                
                    JSONArray playersArray = obj.optJSONArray("players");
                
                    if (playersArray != null) {
                    
                        String[] players = new String[playersArray.length()];
                    
                        for (int i = 0; i < playersArray.length(); i++) {
                            players[i] = playersArray.optString(i);
                        }
                    
                        if (Main.ctrlWait != null) {
                            Main.ctrlWait.updatePlayers(players);
                        }
                    }
                } 
                // Procesar la cuenta atrás
                else if ("countdown".equals(obj.optString("type"))) {

                    int seconds = obj.optInt("value");

                    if (Main.ctrlWait != null) {
                        Main.ctrlWait.updateCountdown(seconds);
                    }

                    // Si veníamos de una partida, volver a la sala de espera
                    Platform.runLater(() -> {
                        if ("ViewPlay".equals(UtilsViews.getActiveView())) {
                            UtilsViews.setView("ViewWait");
                        }
                    });
                }

                // Estado de la partida calculado por el servidor
                else if ("state".equals(obj.optString("type"))) {
                    Main.ctrlPlay.applyState(obj);
                }

                // Fin de la partida
                else if ("gameOver".equals(obj.optString("type"))) {
                    Main.ctrlPlay.showGameOver(obj.optString("winner"));
                }

                // Cuando el servidor indique que empieza la partida
                else if ("gameStart".equals(obj.optString("type"))) {
                
                    int playerNumber = obj.optInt("playerNumber", -1);

                    System.out.println(
                        "DEBUG: gameStart recibido. playerNumber = " + playerNumber
                    );
                
                    JSONArray walls = obj.optJSONArray("walls");
                    JSONArray obstacles = obj.optJSONArray("obstacles");

                    Platform.runLater(() -> {
                        Main.ctrlPlay.startGame(playerNumber, walls, obstacles);
                        UtilsViews.setView("ViewPlay");
                    });
                }
            
            } catch (Exception e) {
                System.out.println("Error procesando mensaje del servidor: "
                        + e.getMessage());
            }
        
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

        // Conectar al servidor después de registrar los callbacks
        Main.wsClient.connect();
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