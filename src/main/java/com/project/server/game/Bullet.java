package com.project.server.game;

public final class Bullet {

    public static final double RADIUS = 5;

    public final int owner;

    public double x, y;
    public double angle;
    public final double speed;
    public boolean bounced;

    Bullet(double x, double y, double angle, double speed, int owner) {
        this.x = x;
        this.y = y;
        this.angle = angle;
        this.speed = speed;
        this.owner = owner;
    }

    void bounce(boolean invertX, boolean invertY) {
        double rad = Math.toRadians(angle);
        double dirX = Math.cos(rad);
        double dirY = Math.sin(rad);
        if (invertX) dirX = -dirX;
        if (invertY) dirY = -dirY;
        angle = Math.toDegrees(Math.atan2(dirY, dirX));
        bounced = true;
    }

    boolean touches(Tank tank) {
        return Math.hypot(x - tank.x, y - tank.y) < RADIUS + Tank.RADIUS;
    }

    boolean touches(Bullet other) {
        return Math.hypot(x - other.x, y - other.y) < RADIUS + RADIUS;
    }
}
