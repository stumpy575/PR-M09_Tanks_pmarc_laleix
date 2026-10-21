package com.project.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

import org.json.JSONArray;
import org.json.JSONObject;

public class GameState {

    private static final String K_NAME = "name";
    private static final String K_COLOR = "color";

    private final List<String> players = new ArrayList<>();
    private final Map<String, String> colors = new HashMap<>();
    private final Map<String, JSONObject> tankStates = new LinkedHashMap<>();

    private long seed = 0;
    private boolean running = false;
    private String winner = null;

    // per a començar una nova partida
    public synchronized void start(List<String> names, Function<String, String> colorFor) {
        players.clear();
        colors.clear();
        tankStates.clear();

        for (String name : names) {
            players.add(name);
            colors.put(name, colorFor.apply(name));
        }

        seed = new Random().nextLong(); // aixo es per a que els clients generin el mateix mapa
        winner = null;
        running = true;
    }

    public synchronized long getSeed() {
        return seed;
    }

    // guarda l'ultim estat del tanc (posicio, angles i bales) que envia el client
    public synchronized void setTankState(String name, JSONObject state) {
        if (!running || !players.contains(name)) return;

        // el nom i el color els posa el servidor, no el client
        state.put(K_NAME, name);
        state.put(K_COLOR, colors.get(name));
        tankStates.put(name, state);
    }

    // self = false: name ha tocat el rival (guanya name)
    // self = true: name s'ha destruit ell mateix (guanya el rival)
    public synchronized void hit(String name, boolean self) {
        if (!running || winner != null || !players.contains(name)) return; // nomes un guanyador per partida

        winner = self ? rivalOf(name) : name;
    }

    // si un jugador marxa en plena partida guanya l'altre
    public synchronized void removePlayer(String name) {
        if (running && winner == null) {
            winner = rivalOf(name);
        }

        players.remove(name);
        colors.remove(name);
        tankStates.remove(name);

        if (winner == null && players.size() < 2) running = false;
    }

    // retorna el guanyador una sola vegada i tanca la partida
    public synchronized String consumeWinner() {
        if (winner == null) return null;

        String result = winner;
        winner = null;
        running = false;
        return result;
    }

    // estat de tots els tancs: [{ name, color, x, y, angle, turretAngle, bullets }]
    public synchronized JSONArray tanksToJSON() {
        JSONArray arr = new JSONArray();
        for (JSONObject state : tankStates.values()) {
            arr.put(state);
        }
        return arr;
    }

    private String rivalOf(String name) {
        for (String p : players) {
            if (!p.equals(name)) return p;
        }
        return null;
    }
}