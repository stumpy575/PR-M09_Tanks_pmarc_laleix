package com.project.server.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class GameWorld {

    public static final double WIDTH = 800;
    public static final double HEIGHT = 500;

    private static final double WALL = 20;
    private static final double TANK_SPEED = 140;   // la velocitat es mesura en pixels persegon
    private static final double BULLET_SPEED = 360;
    private static final int MAX_BULLETS = 4;

    // nombre de passos petits en que es divideix cada actualitzacio per a que els objectes no es saltin colisions
    private static final int PHYSICS_PRECISION = 10;

    private static final List<Rect> OBSTACLE_OPTIONS = List.of(
        new Rect(260, 100, 60, 100),
        new Rect(260, 300, 60, 100),
        new Rect(370, 170, 60, 160),
        new Rect(480, 100, 60, 100),
        new Rect(480, 300, 60, 100)
    );

    private final List<Rect> walls = new ArrayList<>();
    private final List<Rect> obstacles = new ArrayList<>();
    private final List<Rect> solids = new ArrayList<>(); // parets i obstacles
    private final List<Tank> tanks = new ArrayList<>();
    private final List<Bullet> bullets = new ArrayList<>();
    private final List<Explosion> explosions = new ArrayList<>();

    private int destroyedTank = -1; // index del primer tanc mort, -1 es cap

    public GameWorld(Random random) {
        walls.add(new Rect(0, 0, WIDTH, WALL));
        walls.add(new Rect(0, HEIGHT - WALL, WIDTH, WALL));
        walls.add(new Rect(0, 0, WALL, HEIGHT));
        walls.add(new Rect(WIDTH - WALL, 0, WALL, HEIGHT));

        List<Rect> options = new ArrayList<>(OBSTACLE_OPTIONS);
        Collections.shuffle(options, random);
        obstacles.addAll(options.subList(0, 2));

        solids.addAll(walls);
        solids.addAll(obstacles);

        Tank first = new Tank(0, 90, 250, 0);
        Tank second = new Tank(1, 710, 250, 180);
        first.aimX = second.x;
        first.aimY = second.y;
        second.aimX = first.x;
        second.aimY = first.y;
        tanks.add(first);
        tanks.add(second);
    }

    public List<Rect> getWalls() { return walls; }
    public List<Rect> getObstacles() { return obstacles; }
    public List<Tank> getTanks() { return tanks; }
    public List<Bullet> getBullets() { return bullets; }

    public int getDestroyedTank() { return destroyedTank; }

    public List<Explosion> drainExplosions() {
        List<Explosion> copy = new ArrayList<>(explosions);
        explosions.clear();
        return copy;
    }

    // --- Entrada dels jugadors ---

    public void setInput(int player, int dx, int dy, double aimX, double aimY) {
        Tank tank = tanks.get(player);
        tank.dx = Integer.signum(dx);
        tank.dy = Integer.signum(dy);
        setAim(tank, aimX, aimY);
    }

    public void fire(int player, double aimX, double aimY) {
        Tank tank = tanks.get(player);
        if (destroyedTank >= 0 || !tank.alive) return;
        if (countBullets(player) >= MAX_BULLETS) return;

        setAim(tank, aimX, aimY);
        tank.pointAtAim();
        bullets.add(new Bullet(tank.x, tank.y, tank.turretAngle, BULLET_SPEED, player));
    }

    private void setAim(Tank tank, double aimX, double aimY) {
        if (!Double.isNaN(aimX) && !Double.isNaN(aimY)) {
            tank.aimX = aimX;
            tank.aimY = aimY;
        }
    }

    private int countBullets(int owner) {
        int n = 0;
        for (Bullet b : bullets) if (b.owner == owner) n++;
        return n;
    }

    // --- Simulació ---

    // Avança la simulacio x segons
    public void tick(double dt) {

        double step = dt / PHYSICS_PRECISION;

        for (int i = 0; i < PHYSICS_PRECISION; i++) {

            for (Tank tank : tanks) {
                updateTank(tank, step);
                tank.updateInvulnerability(step);
            }

            updateBullets(step);
        }

        for (Tank tank : tanks) {
            if (tank.alive) {
                tank.pointAtAim();
            }
        }
    }

    private void updateTank(Tank tank, double dt) {
        if (!tank.alive || (tank.dx == 0 && tank.dy == 0)) {
            tank.speed = 0;
            return;
        }
        tank.angle = Math.toDegrees(Math.atan2(tank.dy, tank.dx));
        tank.speed = TANK_SPEED;

        double rad = Math.toRadians(tank.angle);
        double nextX = tank.x + Math.cos(rad) * tank.speed * dt;
        double nextY = tank.y + Math.sin(rad) * tank.speed * dt;

        if (!isTankBlocked(tank, nextX, nextY)) {
            tank.x = nextX;
            tank.y = nextY;
        } else if (!isTankBlocked(tank, nextX, tank.y)) {
            tank.x = nextX;
        } else if (!isTankBlocked(tank, tank.x, nextY)) {
            tank.y = nextY;
        }
    }

    private boolean isTankBlocked(Tank tank, double x, double y) {
        if (touchesSolid(x, y, Tank.RADIUS)) return true;
        for (Tank other : tanks) {
            if (other != tank && other.alive
                    && Math.hypot(x - other.x, y - other.y) < Tank.RADIUS + Tank.RADIUS) {
                return true;
            }
        }
        return false;
    }

    private boolean touchesSolid(double x, double y, double radius) {
        for (Rect r : solids) {
            if (r.touchesCircle(x, y, radius)) return true;
        }
        return false;
    }

    private void updateBullets(double dt) {
        List<Bullet> toRemove = new ArrayList<>();

        for (Bullet bullet : bullets) {
            double rad = Math.toRadians(bullet.angle);
            double nextX = bullet.x + Math.cos(rad) * bullet.speed * dt;
            double nextY = bullet.y + Math.sin(rad) * bullet.speed * dt;

            if (touchesSolid(nextX, nextY, Bullet.RADIUS)) {
                if (bullet.bounced) {
                    toRemove.add(bullet);
                    addExplosion(bullet.x, bullet.y, 20);
                } else {
                    boolean invertX = touchesSolid(nextX, bullet.y, Bullet.RADIUS);
                    boolean invertY = touchesSolid(bullet.x, nextY, Bullet.RADIUS);
                    if (!invertX && !invertY) {
                        invertX = true;
                        invertY = true;
                    }
                    bullet.bounce(invertX, invertY);
                }
                continue;
            }
            bullet.x = nextX;
            bullet.y = nextY;

            if (destroyedTank >= 0) continue;
                    
            for (Tank tank : tanks) {
            
                if (!tank.alive || !bullet.touches(tank)) {
                    continue;
                }
            
                if (tank.index != bullet.owner || bullet.bounced) {
                
                    // El proyectil desaparece al impactar
                    toRemove.add(bullet);
                
                    // Solo recibe daño si no es invulnerable
                    if (tank.invulnerabilityTimer <= 0) {
                        destroyTank(tank);
                    }
                
                    break;
                }
            }
        }

        // comprobacio si les bales xoquen
        for (int i = 0; i < bullets.size(); i++) {
            for (int j = i + 1; j < bullets.size(); j++) {
                Bullet a = bullets.get(i);
                Bullet b = bullets.get(j);
                if (toRemove.contains(a) || toRemove.contains(b)) continue;
                if (a.touches(b)) {
                    toRemove.add(a);
                    toRemove.add(b);
                    addExplosion((a.x + b.x) / 2, (a.y + b.y) / 2, 20);
                }
            }
        }

        bullets.removeAll(toRemove);
    }

    private void destroyTank(Tank tank) {
    
        tank.takeDamage();
    
        if (!tank.alive) {
            if (destroyedTank < 0) {
                destroyedTank = tank.index;
            }
        
            addExplosion(tank.x, tank.y, 60);
        } else {
            addExplosion(tank.x, tank.y, 30);
        }
    }

    private void addExplosion(double x, double y, double radius) {
        explosions.add(new Explosion(x, y, radius));
    }
}
