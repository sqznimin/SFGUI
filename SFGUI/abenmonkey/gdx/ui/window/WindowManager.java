package abenmonkey.gdx.ui.window;


import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

public class WindowManager {
    private static Array<WindowPane> windowPanes = new Array<>();

    public static void register(WindowPane windowPane) {
        windowPanes.add(windowPane);
    }

    public static void remove(WindowPane windowPane) {
        windowPanes.removeValue(windowPane, true);
    }

    public static void handleWindowDrag(float stageX, float stageY, FlexibleWindow draggingWindow) {

        Vector2 temp = new Vector2();

        //find in which pane area
        for (WindowPane windowPane : windowPanes) {
            temp.set(stageX, stageY);
            windowPane.stageToLocalCoordinates(temp);
            float width = windowPane.getWidth();
            float height = windowPane.getHeight();
            if (temp.x >= 0 && temp.x <= width && temp.y >= 0 && temp.y <= height) {
                WindowPane oldPane = draggingWindow.getPrepareReceivePane();
                if (windowPane != oldPane) {
                    //TODO this should use oldPane's local coord
                    if (oldPane != null) oldPane.exit(temp.x, temp.y, draggingWindow);
                    windowPane.enter(temp.x, temp.y, draggingWindow);
                    draggingWindow.setPrepareReceivePane(windowPane);
                } else {
                    windowPane.move(temp.x, temp.y, draggingWindow);
                }
                return;
            }
        }
        WindowPane oldPane = draggingWindow.getPrepareReceivePane();
        if (oldPane != null) oldPane.exit(temp.x, temp.y, draggingWindow);
        draggingWindow.setPrepareReceivePane(null);

    }

    public interface WindowDragListener {
        void enter(float x, float y, FlexibleWindow window);

        void exit(float x, float y, FlexibleWindow window);

        void move(float x, float y, FlexibleWindow window);
    }

}
