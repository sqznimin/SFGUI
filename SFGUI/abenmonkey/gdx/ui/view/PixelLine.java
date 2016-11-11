package abenmonkey.gdx.ui.view;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.ui.Image;

public class PixelLine extends Image {

    private float thickness = 1f;

    public PixelLine(Texture texture, float x, float y, float toX, float toY) {
        super(texture);

        setPosition(x, y, toX, toY);
    }

    public void setPosition(float x, float y, float toX, float toY) {
        this.setX(x);
        this.setY(y);

        double lineLength = Math.sqrt((toX - x) * (toX - x) + (toY - y) * (toY - y));
        this.setScaleX((float) lineLength);

        this.setRotation(90 - getAngle(x, y, toX, toY));
    }

    public void setOpacity(float opacity) {
        Color clr = getColor();
        clr.a = opacity;
        setColor(clr);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        this.setScaleY(thickness);
    }

    private float getAngle(float x, float y, float toX, float toY) {
        float angle = (float) Math.toDegrees(Math.atan2(toX - x, toY - y));

        if (angle < 0) {
            angle += 360;
        }

        return angle;
    }

    public void setThickness(float thickness) {
        this.thickness = thickness;
    }

}

