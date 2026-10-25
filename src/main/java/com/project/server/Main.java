package com.project.server;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
            "GREEN", "ORANGE"
    );

    private static final int REQUIRED_CLIENTS = 2;

    private static final int TICK_FPS = 60;

    private static final int RESTART_DELAY_SECONDS = 3;

    private static final String K_TYPE = "type";
    private static final String K_VALUE = "value";
    private static final String K_CLIENT_NAME = "clientName";
    private static final String K_TANKS = "tanks";
    private static final String K_SEED = "seed";
    private static final String K_WINNER = "winner";
    private static final String K_SELF = "self";

    private static final String T_TANK_STATE = "tankState";  // de client a server
    private static final String T_HIT = "hit";               // de client a server
    private static final String T_SERVER_DATA = "serverData";// de server a client
    private static final String T_COUNTDOWN = "countdown";   // de server a client
    private static final String T_GAME_START = "gameStart";  // de server a client
    private static final String T_GAME_OVER = "gameOver";    // de server a client

    private final ClientRegistry clients; // llista dels clients connectats

    private final GameState game = new GameState(); // la clase gamestate

    private volatile boolean countdownRunning = false;

    private final ScheduledExecutorService ticker;

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

    private String getColorForName(String name) {
        int idx = PLAYER_NAMES.indexOf(name);
        if (idx < 0) {
            idx = 0;
        }
        return PLAYER_COLORS.get(idx % PLAYER_COLORS.size());
    }

    // compte enrere que inicia la partida
    private void sendCountdown() {
        synchronized (this) {
            if (countdownRunning) {
                return;
            }
            if (clients.snapshot().size() != REQUIRED_CLIENTS) {
                return;
            }
            countdownRunning = true;
        }

        new Thread(() -> {
            boolean completed = true;
            try {
                for (int i = 5; i >= 0; i--) {
                    if (clients.snapshot().size() < REQUIRED_CLIENTS) {
                        completed = false;
                        break; // si es desconecta un client es cancela el compte enrere
                    }
                    sendCountdownToAll(i);
                    if (i > 0) {
                        Thread.sleep(750);
                    }
                }
            } catch (InterruptedException ie) {
                completed = false;
                Thread.currentThread().interrupt();
            } finally {
                countdownRunning = false;
            }

            if (completed) {
                List<String> names = new ArrayList<>(clients.snapshot().values());
                Collections.sort(names);
                game.start(names, name -> getColorForName(name));
                broadcastGameStart();
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
        JSONObject rst = msg(T_GAME_START).put(K_SEED, game.getSeed());
        broadcastExcept(null, rst.toString());
    }

    private void broadcastStatus() {
        JSONObject rst = msg(T_SERVER_DATA).put(K_TANKS, game.tanksToJSON());
        List<String> names = new ArrayList<>(clients.snapshot().values());
        Collections.sort(names);
        rst.put("players", new JSONArray(names));
        for (WebSocket conn : clients.snapshot().keySet()) {
            rst.put(K_CLIENT_NAME, clients.nameBySocket(conn));
            sendSafe(conn, rst.toString());
        }
    }

    private void broadcastGameOver(String winner) {
        broadcastExcept(null, msg(T_GAME_OVER).put(K_WINNER, winner).toString());
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        // nomes admet REQUIRED_CLIENTS jugadors
        if (clients.snapshot().size() >= REQUIRED_CLIENTS) {
            System.out.println("Connexió rebutjada: partida plena");
            conn.close(1013, "Partida plena");
            return;
        }

        String name = clients.add(conn);
        System.out.println("WebSocket client connected: " + name + " (" + getColorForName(name) + ")");
        sendCountdown();
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String name = clients.remove(conn);
        if (name != null) {
            game.removePlayer(name);
            System.out.println("WebSocket client disconnected: " + name);
        }
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
                } else {
                    System.out.println("No se pudo registrar el nombre: " + newName);
                }
            }
        
            case T_TANK_STATE -> {
                JSONObject value = obj.optJSONObject(K_VALUE);
                if (value != null) {
                    game.setTankState(name, value);
                }
            }
        
            case T_HIT -> {
                JSONObject value = obj.optJSONObject(K_VALUE);
                boolean self = value != null && value.optBoolean(K_SELF, false);
                game.hit(name, self);
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
        long periodMs = Math.max(1, 1000 / TICK_FPS);
        ticker.scheduleAtFixedRate(() -> {
            try {
                String winner = game.consumeWinner();
                if (winner != null) {
                    //acaba la paertida i comença un altre partida
                    broadcastGameOver(winner);
                    ticker.schedule(this::sendCountdown, RESTART_DELAY_SECONDS, TimeUnit.SECONDS);
                }

                if (!clients.snapshot().isEmpty()) {
                    broadcastStatus();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, periodMs, TimeUnit.MILLISECONDS);
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
