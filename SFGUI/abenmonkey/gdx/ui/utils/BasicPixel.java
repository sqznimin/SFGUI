package abenmonkey.gdx.ui.utils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;

public class BasicPixel {

    private static BasicPixel singelton = null;

    private Texture pixel;

    private BasicPixel() {

    }

    private static synchronized BasicPixel self() {
        if (singelton == null) {
            singelton = new BasicPixel();

            singelton.init();
        }

        return singelton;
    }

    private void init() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        pixel = new Texture(pixmap);
    }

    public static Texture getPixel() {
        return self().pixel;
    }
}

