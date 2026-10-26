package com.project.client;

import java.net.URL;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

// el controlador envia teclas premudes ratoli i els tirs
public class CtrlPlay implements Initializable {

    @FXML private Canvas canvas;
    private GraphicsContext gc;
    private CnvTimer timer;
    private long lastRunNanos;

    private static final double EXPLOSION_DURATION = 0.6; // es mesura en segons aixo

    private static final long AIM_SEND_INTERVAL_NANOS = 33_000_000L;

    private final List<ObjectStatic> staticObjects = new ArrayList<>();
    private final List<Bullet> bullets = new ArrayList<>();
    private final List<Explosion> explosions = new ArrayList<>();
    private final Set<KeyCode> pressedKeys = EnumSet.noneOf(KeyCode.class);

    private Tank tank;
    private Tank enemy;
    private final Target target = new Target();

    private int playerNumber = -1;

    private String gameOverMessage = null;

    // Última entrada enviada, per no enviar el mateix missatge a cada frame
    private int lastDx = 0, lastDy = 0;
    private double lastAimX = Double.NaN, lastAimY = Double.NaN;
    private long lastInputNanos = 0;

    @FXML 
    private Label txtMap;
    
    @FXML 
    private Label txtNamePlayer1;
    @FXML 
    private Label txtLivesPlayer1;
    @FXML 
    private Label txtBulletsPlayer1;  

    @FXML 
    private Label txtNamePlayer2;  
    @FXML 
    private Label txtLivesPlayer2;  
    @FXML 
    private Label txtBulletsPlayer2; 

    // --- Inici de partida ---

    public void startGame(int playerNumber, JSONArray walls, JSONArray obstacles) {
        if (playerNumber != 0 && playerNumber != 1) {
            System.out.println("Número de jugador no válido: " + playerNumber);
            return;
        }
        this.playerNumber = playerNumber;

        staticObjects.clear();
        addRects(walls, Color.DIMGRAY);
        addRects(obstacles, Color.LIGHTGRAY);

        bullets.clear();
        explosions.clear();
        pressedKeys.clear();
        gameOverMessage = null;
        lastRunNanos = 0;

        Tank blue = new Tank(new Point2D(90, 250), 0, 0, Color.BLUE);
        Tank red = new Tank(new Point2D(710, 250), 180, 180, Color.RED);
        tank = playerNumber == 0 ? blue : red;
        enemy = playerNumber == 0 ? red : blue;

        target.setPosition(enemy.getPosition().getX(), enemy.getPosition().getY());
        tank.pointAt(target.getPosition());

        lastDx = 0;
        lastDy = 0;
        lastAimX = target.getPosition().getX();
        lastAimY = target.getPosition().getY();
        lastInputNanos = System.nanoTime();

        System.out.println("Número de jugador asignado: " + playerNumber);
        redraw();
    }

    private void addRects(JSONArray rects, Color color) {
        if (rects == null) return;
        for (int i = 0; i < rects.length(); i++) {
            JSONObject r = rects.getJSONObject(i);
            staticObjects.add(new ObjectStatic(
                    r.getDouble("x"), r.getDouble("y"), r.getDouble("w"), r.getDouble("h"), color));
        }
    }

    // --- Missatges del servidor ---

    // aixo es per a l'estat de la partida
    public void applyState(JSONObject state) {
        Platform.runLater(() -> applyStateFx(state));
    }

    public void showGameOver(String winner) {
        Platform.runLater(() -> {
            String me = Main.playerName;
            gameOverMessage = winner != null && winner.equals(me)
                    ? "¡Has ganado!"
                    : "Ha ganado " + winner;
        });
    }

    private void applyStateFx(JSONObject state) {
        if (tank == null || enemy == null) return;

        JSONArray tanks = state.optJSONArray("tanks");
        if (tanks != null) {
            for (int i = 0; i < tanks.length(); i++) {
                JSONObject t = tanks.getJSONObject(i);
                Tank shown = (i == playerNumber) ? tank : enemy;
                shown.updateState(
                        new Point2D(t.getDouble("x"), t.getDouble("y")),
                        t.getDouble("angle"),
                        t.getDouble("turretAngle"),
                        t.getBoolean("alive"));
            }
            if (tank.isAlive()) tank.pointAt(target.getPosition());
        }

        bullets.clear();
        JSONArray bulletsJson = state.optJSONArray("bullets");
        if (bulletsJson != null) {
            for (int i = 0; i < bulletsJson.length(); i++) {
                JSONObject b = bulletsJson.getJSONObject(i);
                bullets.add(new Bullet(new Point2D(b.getDouble("x"), b.getDouble("y")), b.getDouble("angle")));
            }
        }

        JSONArray events = state.optJSONArray("events");
        if (events != null) {
            for (int i = 0; i < events.length(); i++) {
                JSONObject e = events.getJSONObject(i);
                if ("explosion".equals(e.optString("type"))) {
                    explosions.add(new Explosion(
                            e.getDouble("x"), e.getDouble("y"), e.getDouble("r"), EXPLOSION_DURATION));
                }
            }
        }
    }

    // --- Inicialització i entrada ---

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        gc = canvas.getGraphicsContext2D();

        Platform.runLater(() -> { configureInput(); });

        timer = new CnvTimer(fps -> update(), this::redraw, 60);
        timer.start();
        redraw();
    }

    private void configureInput() {

        canvas.setOnMouseMoved(this::updateTarget);
        canvas.setOnMouseDragged(this::updateTarget);
        canvas.setOnMousePressed(event -> {
            updateTarget(event);
            if (event.getButton() == MouseButton.PRIMARY) sendShoot();
        });

        canvas.getScene().setOnKeyPressed(event -> pressedKeys.add(event.getCode()));
        canvas.getScene().setOnKeyReleased(event -> pressedKeys.remove(event.getCode()));
        canvas.getScene().getWindow().focusedProperty().addListener((obs, old, focused) -> {
            if (!focused) pressedKeys.clear();
        });
    }

    private void updateTarget(MouseEvent event) {
        target.setPosition(event.getX(), event.getY());
    }

    // --- Enviament d'entrada al servidor ---

    // demanar al servidor que dispari cap al punt de mira actual
    private void sendShoot() {
        if (!canPlay()) return;

        JSONObject value = new JSONObject()
                .put("aimX", target.getPosition().getX())
                .put("aimY", target.getPosition().getY());
        send(new JSONObject().put("type", "shoot").put("value", value));
    }

    // enviar tecles i punt de mira, només si han canviat
    private void sendInputIfChanged() {
        if (!canPlay()) return;

        int dx = 0, dy = 0;
        if (pressedKeys.contains(KeyCode.LEFT) || pressedKeys.contains(KeyCode.A)) dx -= 1;
        if (pressedKeys.contains(KeyCode.RIGHT) || pressedKeys.contains(KeyCode.D)) dx += 1;
        if (pressedKeys.contains(KeyCode.UP) || pressedKeys.contains(KeyCode.W)) dy -= 1;
        if (pressedKeys.contains(KeyCode.DOWN) || pressedKeys.contains(KeyCode.S)) dy += 1;

        double aimX = target.getPosition().getX();
        double aimY = target.getPosition().getY();
        long now = System.nanoTime();

        boolean moveChanged = dx != lastDx || dy != lastDy;
        boolean aimChanged = aimX != lastAimX || aimY != lastAimY;
        boolean aimDue = now - lastInputNanos >= AIM_SEND_INTERVAL_NANOS;

        if (!moveChanged && !(aimChanged && aimDue)) return;

        JSONObject value = new JSONObject()
                .put("dx", dx)
                .put("dy", dy)
                .put("aimX", aimX)
                .put("aimY", aimY);
        send(new JSONObject().put("type", "input").put("value", value));

        lastDx = dx;
        lastDy = dy;
        lastAimX = aimX;
        lastAimY = aimY;
        lastInputNanos = now;
    }

    // nomes s'envia entrada mentre la partida esta en marxa i el tanc esta viu
    private boolean canPlay() {
        return tank != null && tank.isAlive() && gameOverMessage == null;
    }

    private void send(JSONObject message) {
        if (Main.wsClient != null) {
            Main.wsClient.safeSend(message.toString());
        }
    }


    private void update() {
        if (tank == null || enemy == null) return;

        double dt = calculateDt();
        sendInputIfChanged();
        updateExplosions(dt);
    }

    // aixo calcula el temps
    private double calculateDt() {
        long now = System.nanoTime();
        double dt = lastRunNanos == 0 ? 0 : (now - lastRunNanos) / 1_000_000_000.0;
        lastRunNanos = now;
        return Math.min(dt, 0.05);
    }

    private void updateExplosions(double dt) {
        for (Explosion explosion : explosions) {
            explosion.update(dt);
        }
        explosions.removeIf(Explosion::isFinished);
    }


    private void redraw() {

        // fons
        double w = canvas.getWidth(), h = canvas.getHeight();
        gc.setFill(Color.WHITESMOKE);
        gc.fillRect(0, 0, w, h);

        // parets i obstacles
        for (ObjectStatic rectangle : staticObjects) rectangle.draw(gc);

        for (Bullet bullet : bullets) bullet.draw(gc);

        if (tank != null && enemy != null) {
            tank.draw(gc);
            enemy.draw(gc);
        }

        for (Explosion explosion : explosions) explosion.draw(gc);

        // cano i punt de mira del tanc
        if (tank != null && tank.isAlive()) {
            target.drawGuide(gc, tank, staticObjects);
            target.draw(gc, tank);
        }

        if (gameOverMessage != null) {
            gc.save();
            gc.setFill(Color.BLACK);
            gc.setFont(Font.font("Arial", FontWeight.BOLD, 40));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(gameOverMessage, w / 2, h / 2);
            gc.restore();
        }
    }
}
