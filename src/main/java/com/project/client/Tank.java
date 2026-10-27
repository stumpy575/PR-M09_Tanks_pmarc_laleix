package com.project.client;

import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

class Tank extends ObjectDynamic {


    static final double RADIUS = 25;

    private double turretAngle;
    private final Color color;
    private boolean alive;
    private boolean invulnerable;

    Tank(Point2D position, double angle, double turretAngle, Color color) {
        super(position, angle, 0);
        this.color = color;
        reset(position, angle, turretAngle);
    }

    @Override
    double getRadius() {
        return RADIUS;
    }

    Color getColor() {
        return color;
    }

    double getTurretAngle() {
        return turretAngle;
    }

    boolean isAlive() {
        return alive;
    }

    void destroy() {
        alive = false;
        setSpeed(0);
    }

    void setInvulnerable(boolean invulnerable) {
        this.invulnerable = invulnerable;
    }

    void reset(Point2D position, double angle, double turretAngle) {
        setPosition(position);
        setAngle(angle);
        setSpeed(0);
        this.turretAngle = turretAngle;
        alive = true;
    }

    // aixo actualitza l'estat del tanc amb les dades del servidor
    void updateState(Point2D position, double angle, double turretAngle, boolean alive) {
        setPosition(position);
        setAngle(angle);
        this.turretAngle = turretAngle;
    
        if (alive) {
            this.alive = true;
        } else {
            destroy();
        }
    }

    void pointAt(Point2D target) {
        turretAngle = HelperMath.angleToTarget(getPosition(), target);
    }

    @Override
    void draw(GraphicsContext gc) {
    
        if (!alive) return;
    
        // El tanc parpelleja mentre és invulnerable
        if (invulnerable && (System.nanoTime() / 100_000_000L) % 2 == 0) {
            return;
        }

        gc.save();
        gc.translate(position.getX(), position.getY());
        gc.rotate(angle);
        gc.setFill(Color.DARKSLATEGRAY);
        gc.fillRect(-18, -17, 36, 6);
        gc.fillRect(-18, 11, 36, 6);
        gc.setFill(color);
        gc.fillRect(-16, -11, 32, 22);
        gc.restore();

        gc.save();
        gc.translate(position.getX(), position.getY());
        gc.rotate(turretAngle);
        gc.setFill(color.darker());
        gc.fillRect(0, -3, 32, 6);
        gc.fillOval(-9, -9, 18, 18);
        gc.restore();
    }
}
