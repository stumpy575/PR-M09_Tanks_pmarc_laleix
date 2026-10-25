package com.project.client;

import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

class Bullet extends ObjectDynamic {


    static final double RADIUS = 5;

    Bullet(Point2D position, double angleDegrees) {
        super(position, angleDegrees, 0);
    }

    @Override
    double getRadius() {
        return RADIUS;
    }

    @Override
    void draw(GraphicsContext gc) {

        gc.save();
        gc.translate(position.getX(), position.getY());
        gc.rotate(angle);

        gc.setFill(Color.RED);
        gc.fillRect(-4, -2, 7, 4);
        gc.fillOval(1, -2, 4, 4);
        gc.restore();
    }
}
