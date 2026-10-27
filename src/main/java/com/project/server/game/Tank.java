package com.project.server.game;

public final class Tank {

    public static final double RADIUS = 25;

    public final int index;

    public double x, y;
    public double angle;
    public double turretAngle;
    public double speed;
    public boolean alive = true;
    public int lives = 3;

    private static final double INVULNERABILITY_TIME = 1.0;
    public double invulnerabilityTimer = 0;

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

    void takeDamage() {

        if (!alive || invulnerabilityTimer > 0) {
            return;
        }

        lives--;

        if (lives <= 0) {
            lives = 0;
            alive = false;
            speed = 0;
        } else {
            invulnerabilityTimer = INVULNERABILITY_TIME;
        }
    }

    void updateInvulnerability(double dt) {
        
        if (invulnerabilityTimer > 0) {
            invulnerabilityTimer -= dt;
        
            if (invulnerabilityTimer < 0) {
                invulnerabilityTimer = 0;
            }
        }
    }
}
