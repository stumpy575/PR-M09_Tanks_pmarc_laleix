package com.project.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;

public class UtilsWS {

    private static UtilsWS sharedInstance = null;
    private WebSocketClient client;
    private Consumer<String> onOpenCallBack = null;
    private Consumer<String> onMessageCallBack = null;
    private Consumer<String> onCloseCallBack = null;
    private Consumer<String> onErrorCallBack = null;
    private String location = "";
    private static AtomicBoolean exitRequested = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean reconnectEnabled = true;

    private UtilsWS(String location) {
        this.location = location;
    }

    private void createNewWebSocketClient() {
        try {
            this.client = new WebSocketClient(new URI(location), new Draft_6455()) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    String message = "WS connected to: " + getURI();
                    System.out.println(message);
                    if (onOpenCallBack != null) {
                        onOpenCallBack.accept(message);
                    }
                }

                @Override
                public void onMessage(String message) {
                    if (onMessageCallBack != null) {
                        onMessageCallBack.accept(message);
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    String message = "WebSocket cerrado. Código: "
                            + code + ", motivo: " + reason;
                
                    System.out.println(message);
                
                    if (onCloseCallBack != null) {
                        onCloseCallBack.accept(message);
                    }
                
                    // El servidor rechaza al cliente porque la partida está llena.
                    if (code == 1013 || "Partida plena".equalsIgnoreCase(reason)) {
                        reconnectEnabled = false;
                        exitRequested.set(true);
                        scheduler.shutdownNow();
                        return;
                    }
                
                    if (remote && reconnectEnabled && !exitRequested.get()) {
                        scheduleReconnect();
                    }
                }

                @Override
                public void onError(Exception e) {
                    String errorMessage = "WS connection error: "
                            + (e.getMessage() != null ? e.getMessage() : "Error desconocido");
                
                    System.out.println(errorMessage);
                
                    if (onErrorCallBack != null) {
                        onErrorCallBack.accept(errorMessage);
                    }
                
                    if (!reconnectEnabled || exitRequested.get()) {
                        return;
                    }
                
                    // Solo reintentar automáticamente ante errores de conexión
                    // que puedan ser temporales.
                    String details = e.getMessage();
                
                    if (details != null
                            && (details.contains("Connection refused")
                                || details.contains("Connection reset"))) {
                        scheduleReconnect();
                    }
                }
            };
            this.client.connect();
        } catch (URISyntaxException e) {
            e.printStackTrace();
            System.out.println("WS Error, " + location + " is not a valid URI");
        }
    }

    private void scheduleReconnect() {
        if (reconnectEnabled && !exitRequested.get()) {
            scheduler.schedule(this::reconnect, 5, TimeUnit.SECONDS);
        }
    }

    private void reconnect() {
        if (exitRequested.get() || !reconnectEnabled) {
            return;
        }

        System.out.println("WS reconnecting to: " + this.location);

        if (client != null && !client.isClosed()) {
            client.close();
        }

        // Comprobar otra vez antes de crear una conexión nueva.
        if (exitRequested.get() || !reconnectEnabled) {
            return;
        }

        createNewWebSocketClient();
    }

    public static UtilsWS getSharedInstance(String location) {
        if (sharedInstance == null) {
            sharedInstance = new UtilsWS(location);
        }
        return sharedInstance;
    }

    public void onOpen(Consumer<String> callBack) {
        this.onOpenCallBack = callBack;
    }

    public void onMessage(Consumer<String> callBack) {
        this.onMessageCallBack = callBack;
    }

    public void onClose(Consumer<String> callBack) {
        this.onCloseCallBack = callBack;
    }

    public void onError(Consumer<String> callBack) {
        this.onErrorCallBack = callBack;
    }

    public void safeSend(String text) {
        try {
            if (client != null && client.isOpen()) {
                client.send(text);
            } else {
                System.out.println("WS Error: Client is not connected.");
            
                if (reconnectEnabled && !exitRequested.get()) {
                    scheduleReconnect();
                }
            }
        } catch (Exception e) {
            System.out.println("WS Error sending message: " + e.getMessage());
        }
    }

    public void forceExit() {
        System.out.println("WS Closing ...");
        exitRequested.set(true);
        try {
            if (client != null && !client.isClosed()) {
                client.closeBlocking();
            }
        } catch (Exception e) {
            System.out.println("WS Interrupted while closing WebSocket connection: " + e.getMessage());
            Thread.currentThread().interrupt();
        } finally {
            scheduler.shutdownNow();
        }
    }

    public boolean isOpen() {
        return client != null && client.isOpen();
    }

    public void connect() {
        reconnectEnabled = true;
        createNewWebSocketClient();
    }

    public void disconnectIntentional() {
        reconnectEnabled = false;

        if (client != null && client.isOpen()) {
            client.close(1000, "Partida terminada");
        }
    }
}