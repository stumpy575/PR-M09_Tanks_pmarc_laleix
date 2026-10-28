package com.project.server;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.java_websocket.WebSocket;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONArray;
import org.json.JSONObject;

public class Main extends WebSocketServer {

    public static final int DEFAULT_PORT = 3000;

    private static final List<String> PLAYER_NAMES = Arrays.asList(
            "Jugador 1", "Jugador 2"
    );

    private static final List<String> PLAYER_COLORS = Arrays.asList(
            "BLUE", "RED"
    );

    private static final int REQUIRED_CLIENTS = 2;

    private static final int TICK_FPS = 60;

    private static final String K_TYPE = "type";
    private static final String K_VALUE = "value";
    private static final String K_CLIENT_NAME = "clientName";
    private static final String K_PLAYERS = "players";
    private static final String K_WINNER = "winner";

    // Màxim de segons que pot avançar la simulació en un sol tick
    private static final double MAX_DT = 0.05;

    private static final String T_INPUT = "input";              // de client a server
    private static final String T_SHOOT = "shoot";              // de client a server
    private static final String T_SERVER_DATA = "serverData";   // de server a client
    private static final String T_STATE = "state";              // de server a client
    private static final String T_COUNTDOWN = "countdown";       // de server a client
    private static final String T_GAME_START = "gameStart";     // de server a client
    private static final String T_GAME_OVER = "gameOver";       // de server a client

    private final ClientRegistry clients;

    private final GameState game = new GameState();

    private volatile boolean countdownRunning = false;

    private final ScheduledExecutorService ticker;

    private volatile int countdownGeneration = 0;

    private volatile boolean endingGame = false;

    private final Set<WebSocket> endingSockets = new HashSet<>();

    private long lastTickNanos = 0;

    public Main(InetSocketAddress address) {
        super(address);
        this.clients = new ClientRegistry(PLAYER_NAMES);

        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "ServerTicker");
            t.setDaemon(true);
            return t;
        };
        this.ticker = Executors.newSingleThreadScheduledExecutor(tf);
    }

    private String getColorForSocket(WebSocket socket) {
        int playerNumber = clients.playerNumber(socket);

        if (playerNumber < 0 || playerNumber >= PLAYER_COLORS.size()) {
            return "GRAY";
        }

        return PLAYER_COLORS.get(playerNumber);
    }

    // compte enrere que inicia la partida
    private void sendCountdown() {
        final int generation;
    
        synchronized (this) {
            if (endingGame || countdownRunning) {
                return;
            }
        
            if (clients.snapshot().size() != REQUIRED_CLIENTS) {
                return;
            }
        
            countdownRunning = true;
            generation = ++countdownGeneration;
        }
    
        new Thread(() -> {
            boolean completed = false;
        
            try {
                for (int i = 5; i >= 0; i--) {
                    synchronized (this) {
                        if (generation != countdownGeneration
                                || endingGame
                                || clients.snapshot().size() != REQUIRED_CLIENTS) {
                            return;
                        }
                    }
                
                    sendCountdownToAll(i);
                
                    if (i > 0) {
                        Thread.sleep(750);
                    }
                }
            
                List<Map.Entry<WebSocket, String>> connectedPlayers =
                        new ArrayList<>(clients.snapshot().entrySet());
            
                if (connectedPlayers.size() != REQUIRED_CLIENTS) {
                    return;
                }
            
                connectedPlayers.sort((a, b) -> Integer.compare(
                        clients.playerNumber(a.getKey()),
                        clients.playerNumber(b.getKey())
                ));
            
                List<String> names = new ArrayList<>();
                Map<String, String> colorsByName = new HashMap<>();
            
                for (Map.Entry<WebSocket, String> player : connectedPlayers) {
                    WebSocket socket = player.getKey();
                    String name = player.getValue();
                
                    if (socket == null || !socket.isOpen()
                            || name == null || name.isBlank()) {
                        return;
                    }
                
                    names.add(name);
                    colorsByName.put(name, getColorForSocket(socket));
                }
            
                synchronized (this) {
                    if (generation != countdownGeneration
                            || endingGame
                            || clients.snapshot().size() != REQUIRED_CLIENTS) {
                        return;
                    }
                
                    game.start(names, name -> colorsByName.getOrDefault(name, "GRAY"));
                    broadcastGameStart();
                    completed = true;
                }
            
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            
            } catch (Exception e) {
                e.printStackTrace();
            
            } finally {
                synchronized (this) {
                    if (generation == countdownGeneration) {
                        countdownRunning = false;
                    }
                }
            }
        
            if (!completed) {
                System.out.println("Compte enrere cancel·lat o partida no iniciada.");
            }
        
        }, "CountdownThread").start();
    }

    private static JSONObject msg(String type) {
        return new JSONObject().put(K_TYPE, type);
    }

    private void sendSafe(WebSocket to, String payload) {
        if (to == null) {
            return;
        }
        try {
            to.send(payload);
        } catch (WebsocketNotConnectedException e) {
            String name = clients.cleanupDisconnected(to);
            if (name != null) {
                game.removePlayer(name);
            }
            System.out.println("Client desconnectat durant send: " + name);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void broadcastExcept(WebSocket sender, String payload) {
        for (Map.Entry<WebSocket, String> e : clients.snapshot().entrySet()) {
            WebSocket conn = e.getKey();
            if (!Objects.equals(conn, sender)) {
                sendSafe(conn, payload);
            }
        }
    }

    private void sendCountdownToAll(int n) {
        broadcastExcept(null, msg(T_COUNTDOWN).put(K_VALUE, n).toString());
    }

    private void broadcastGameStart() {
        System.out.println("DEBUG: broadcastGameStart() ejecutado");

        // Parets i obstacles els decideix el servidor: tots els clients veuen el mateix mapa
        JSONObject layout = game.layoutToJSON();

        for (Map.Entry<WebSocket, String> player : clients.snapshot().entrySet()) {
            WebSocket socket = player.getKey();

            JSONObject rst = msg(T_GAME_START)
                    .put("playerNumber", clients.playerNumber(socket))
                    .put("walls", layout.optJSONArray("walls"))
                    .put("obstacles", layout.optJSONArray("obstacles"));

            sendSafe(socket, rst.toString());
        }
    }

    private void broadcastLobby() {
        JSONObject rst = msg(T_SERVER_DATA);
        List<String> names = new ArrayList<>(clients.snapshot().values());
        Collections.sort(names);
        rst.put(K_PLAYERS, new JSONArray(names));
        for (WebSocket conn : clients.snapshot().keySet()) {
            rst.put(K_CLIENT_NAME, clients.nameBySocket(conn));
            sendSafe(conn, rst.toString());
        }
    }

    private void broadcastState(JSONObject snapshot) {
        snapshot.put(K_TYPE, T_STATE);
        broadcastExcept(null, snapshot.toString());
    }

    private void broadcastGameOver(String winner) {
        synchronized (this) {
            if (endingGame) {
                return;
            }

            endingGame = true;
            countdownGeneration++;
            countdownRunning = false;
        }

        String loser = game.getLoser(winner);

        JSONObject result = msg(T_GAME_OVER)
                .put(K_WINNER, winner)
                .put("loser", loser);

        String message = result.toString();

        // Guardem les connexions d'aquesta partida.
        List<WebSocket> socketsToClose =
                new ArrayList<>(clients.snapshot().keySet());

        synchronized (this) {
            endingSockets.clear();
            endingSockets.addAll(socketsToClose);
        }

        for (WebSocket socket : socketsToClose) {
            sendSafe(socket, message);
        }

        System.out.println("Partida terminada.");
        System.out.println("Ganador: " + winner);
        System.out.println("Perdedor: " + loser);

        ticker.schedule(() -> {
            for (WebSocket socket : socketsToClose) {
                if (socket != null && socket.isOpen()) {
                    socket.close(1000, "Partida terminada");
                }
            }
        }, 500, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        if (clients.snapshot().size() >= REQUIRED_CLIENTS) {
            System.out.println("Connexió rebutjada: partida plena");
            conn.close(1013, "Partida plena");
            return;
        }

        String name = clients.add(conn);
        System.out.println("WebSocket client connected: " + name + " (" + getColorForSocket(conn) + ")");
        broadcastLobby();
        sendCountdown();
    }


    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        // Si el socket no estaba registrado, era una conexión rechazada
        // o una conexión que ya había sido eliminada.
        String name = clients.remove(conn);

        if (name == null) {
            System.out.println(
                    "Conexión no registrada cerrada. No se modifica la partida."
            );
            return;
        }

        synchronized (this) {
            countdownGeneration++;
            countdownRunning = false;

            // Solo se modifica el seguimiento del final de partida
            // si esta conexión pertenecía a la partida que terminaba.
            if (endingSockets.remove(conn)) {
                if (endingSockets.isEmpty()) {
                    endingGame = false;
                }
            }
        }

        game.removePlayer(name);

        System.out.println("WebSocket client disconnected: " + name);
        broadcastLobby();

        // No iniciamos el contador desde onClose().
        // El contador se inicia cuando se conecta el segundo jugador
        // válido, mediante onOpen() y sendCountdown().
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        JSONObject obj;
        try {
            obj = new JSONObject(message);
        } catch (Exception ex) {
            return;
        }

        String name = clients.nameBySocket(conn);
        if (name == null) {
            return;
        }

        switch (obj.optString(K_TYPE, "")) {
        
            case "setName" -> {
                String newName = obj.optString(K_VALUE, "").trim();

                if (newName.isEmpty()) {
                    return;
                }

                boolean renamed = clients.rename(conn, newName);

                if (renamed) {
                    System.out.println("Jugador registrado con el nombre: " + newName);
                    broadcastLobby();
                } else {
                    System.out.println("No se pudo registrar el nombre: " + newName);
                }
            }

            case T_INPUT -> {
                JSONObject value = obj.optJSONObject(K_VALUE);
                if (value != null) {
                    game.setInput(name,
                            value.optInt("dx", 0), value.optInt("dy", 0),
                            value.optDouble("aimX"), value.optDouble("aimY"));
                }
            }

            case T_SHOOT -> {
                JSONObject value = obj.optJSONObject(K_VALUE);
                double aimX = value != null ? value.optDouble("aimX") : Double.NaN;
                double aimY = value != null ? value.optDouble("aimY") : Double.NaN;
                game.fire(name, aimX, aimY);
            }

            default -> {
            }
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("WebSocket server started on port: " + getPort());
        setConnectionLostTimeout(100);
        startTicker();
    }

    private void startTicker() {
        long periodNanos = 1_000_000_000L / TICK_FPS;
        ticker.scheduleAtFixedRate(() -> {
            try {
                long now = System.nanoTime();
                double dt = lastTickNanos == 0 ? 1.0 / TICK_FPS : (now - lastTickNanos) / 1_000_000_000.0;
                lastTickNanos = now;
                dt = Math.min(dt, MAX_DT);

                // el servidor simula la partida
                game.tick(dt);

                // envia l'estat als clients
                JSONObject snapshot = game.snapshot();
                if (snapshot != null) {
                    broadcastState(snapshot);
                }

                // Si alguien gana, termina la partida
                String winner = game.consumeWinner();

                if (winner != null) {
                    broadcastGameOver(winner);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, periodNanos, TimeUnit.NANOSECONDS);
    }

    private void stopTicker() {
        try {
            ticker.shutdownNow();
            ticker.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static void registerShutdownHook(Main server) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Aturant servidor (shutdown hook)...");
            try {
                server.stopTicker();
                server.stop(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
            System.out.println("Servidor aturat.");
        }));
    }

    private static void awaitForever() {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            latch.await();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        Main server = new Main(new InetSocketAddress(DEFAULT_PORT));
        server.start();
        registerShutdownHook(server);

        System.out.println("Server running on port " + DEFAULT_PORT + ". Press Ctrl+C to stop it.");
        awaitForever();
    }
}
