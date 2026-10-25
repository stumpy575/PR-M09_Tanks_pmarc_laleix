package com.project.server.game;

public final class Tank {

    public static final double RADIUS = 25;

    public final int index;

    public double x, y;
    public double angle;
    public double turretAngle;
    public double speed;
    public boolean alive = true;

    // ultima entrada rebuda del client
    public int dx, dy;         // direccio de moviment
    public double aimX, aimY;  // posicio del punt de mira

    public Tank(int index, double x, double y, double angle) {
        this.index = index;
        this.x = x;
        this.y = y;
        this.angle = angle;
        this.turretAngle = angle;
    }

    void pointAtAim() {
        turretAngle = Math.toDegrees(Math.atan2(aimY - y, aimX - x));
    }

    void destroy() {
        alive = false;
        speed = 0;
    }
}
