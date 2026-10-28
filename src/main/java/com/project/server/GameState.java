package com.project.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

import org.json.JSONArray;
import org.json.JSONObject;

import com.project.server.game.Bullet;
import com.project.server.game.Explosion;
import com.project.server.game.GameWorld;
import com.project.server.game.Rect;
import com.project.server.game.Tank;

public class GameState {

    private final List<String> players = new ArrayList<>();
    private final Map<String, String> colors = new HashMap<>();

    private GameWorld world = null;
    private boolean running = false;
    private String winner = null;

    public synchronized void start(List<String> names, Function<String, String> colorFor) {
        if (names.size() < 2) return;

        players.clear();
        colors.clear();
        for (String name : names) {
            players.add(name);
            colors.put(name, colorFor.apply(name));
        }

        world = new GameWorld(new Random());
        winner = null;
        running = true;
    }

    public synchronized boolean isRunning() {
        return running;
    }

    // --- Entrada dels jugadors  ---

    public synchronized void setInput(String name, int dx, int dy, double aimX, double aimY) {
        int player = players.indexOf(name);
        if (!running || winner != null || player < 0) return;
        world.setInput(player, dx, dy, aimX, aimY);
    }

    public synchronized void fire(String name, double aimX, double aimY) {
        int player = players.indexOf(name);
        if (!running || winner != null || player < 0) return;
        world.fire(player, aimX, aimY);
    }

    // --- Simulació --

    // Avança la partida dt segons i comprova si algu ha guanyat
    public synchronized void tick(double dt) {
        if (!running || winner != null) return;

        world.tick(dt);

        int destroyed = world.getDestroyedTank();
        if (destroyed >= 0) {
            winner = players.get(1 - destroyed);
        }
    }

    // Si un jugador abandona la partida, gana el rival.
    public synchronized void removePlayer(String name) {
        if (!running || winner != null || !players.contains(name)) {
            return;
        }
    
        winner = rivalOf(name);
    }

    public synchronized String consumeWinner() {
        if (winner == null) return null;

        String result = winner;
        winner = null;
        running = false;
        return result;
    }


    public synchronized JSONObject layoutToJSON() {
        JSONObject json = new JSONObject();
        if (world != null) {
            json.put("walls", rectsToJSON(world.getWalls()));
            json.put("obstacles", rectsToJSON(world.getObstacles()));
        }
        return json;
    }

    // instantania de la partida amb els tancs les bales i les explosions
    public synchronized JSONObject snapshot() {
        if (!running || world == null) return null;

        JSONArray tanks = new JSONArray();
        for (Tank t : world.getTanks()) {
            String name = players.get(t.index);
            tanks.put(
                new JSONObject()
                .put("name", name)
                .put("color", colors.get(name))
                .put("x", round(t.x))
                .put("y", round(t.y))
                .put("angle", round(t.angle))
                .put("turretAngle", round(t.turretAngle))
                .put("alive", t.alive).put("lives", t.lives)
                .put("invulnerable", t.invulnerabilityTimer > 0)
            );
        }

        JSONArray bullets = new JSONArray();
        for (Bullet b : world.getBullets()) {
            bullets.put(new JSONObject()
                    .put("x", round(b.x))
                    .put("y", round(b.y))
                    .put("angle", round(b.angle))
                    .put("owner", b.owner));
        }

        JSONArray events = new JSONArray();
        for (Explosion e : world.drainExplosions()) {
            events.put(new JSONObject()
                    .put("type", "explosion")
                    .put("x", round(e.x()))
                    .put("y", round(e.y()))
                    .put("r", round(e.radius())));
        }

        return new JSONObject()
                .put("tanks", tanks)
                .put("bullets", bullets)
                .put("events", events);
    }

    private static JSONArray rectsToJSON(List<Rect> rects) {
        JSONArray arr = new JSONArray();
        for (Rect r : rects) {
            arr.put(new JSONObject().put("x", r.x).put("y", r.y).put("w", r.w).put("h", r.h));
        }
        return arr;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private String rivalOf(String name) {
        for (String p : players) {
            if (!p.equals(name)) return p;
        }
        return null;
    }

    public synchronized String getLoser(String winner) {
        return rivalOf(winner);
    }
}
