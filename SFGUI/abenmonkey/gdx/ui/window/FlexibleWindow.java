package abenmonkey.gdx.ui.window;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.widget.VisWindow;

public class FlexibleWindow extends VisWindow {

    private WindowPane windowPane;
    private WindowPane prepareReceivePane;
    private boolean isFlexible = true;
    private InputListener toFrontListener;


    public FlexibleWindow(String title) {
        super(title);

        getTitleLabel().setAlignment(Align.left);
        setResizable(true);
        setModal(false);
     //   setKeepWithinStage(false);

        for (EventListener listener : getCaptureListeners()) {
            removeCaptureListener(listener);
        }
        toFrontListener = new InputListener() {
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                toFront();
                return false;
            }
        };
        addCaptureListener(toFrontListener);

        initialize();

    }

    private void initialize() {
        addCaptureListener(new InputListener() {

            //for move out pane
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (!isFlexible || windowPane == null) return true;

                float width = getWidth(), height = getHeight();
                if (y <= height && y >= height - getPadTop() && x >= 0 && x <= width) {

                    removeFromWindowPane();

                    return true;
                }
                return true;
            }

            //for insert to pane
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {

                if (windowPane == null && prepareReceivePane != null) {
                    prepareReceivePane.receiveWindow(FlexibleWindow.this);
                }
            }

        });

        addAction(new Action() {
            Vector2 mouse = new Vector2();

            @Override
            public boolean act(float delta) {
                if (isDragging() && isFlexible) {
                    mouse.set(Gdx.input.getX(), Gdx.input.getY());
                    getStage().screenToStageCoordinates(mouse);
                    WindowManager.handleWindowDrag(mouse.x, mouse.y, FlexibleWindow.this);
                }

                return false;
            }
        });
    }

    public boolean isFlexible(){
        return  isFlexible;
    }

    public void setFlexible(boolean isFlexible){
        this.isFlexible = isFlexible;
    }

    void removeFromWindowPane() {

        Stage stage = getStage();
        Vector2 position = new Vector2();
        localToStageCoordinates(position);

        windowPane.removeWindow(FlexibleWindow.this);
        windowPane = null;
        setMovable(true);
        setResizable(true);

        addListener(toFrontListener);
        stage.addActor(this);
        setPosition(position.x, position.y);
    }

    void setWindowPane(WindowPane windowPane) {
        if (this.windowPane == windowPane) return;
        this.windowPane = windowPane;
        setMovable(false);
        setResizable(false);
        removeCaptureListener(toFrontListener);
    }

    void setPrepareReceivePane(WindowPane windowPane) {
        this.prepareReceivePane = windowPane;
    }

    public WindowPane getPrepareReceivePane() {
        return prepareReceivePane;
    }


}
