package com.project.server.game;

// rectangles per a les parets i obstacles
public final class Rect {

    public final double x, y, w, h;

    public Rect(double x, double y, double w, double h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public boolean touchesCircle(double cx, double cy, double radius) {
        double nearestX = Math.max(x, Math.min(cx, x + w));
        double nearestY = Math.max(y, Math.min(cy, y + h));
        return Math.hypot(cx - nearestX, cy - nearestY) < radius;
    }
}
