package abenmonkey.gdx.ui.window;

import abenmonkey.gdx.ui.view.PixelRect;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.Layout;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.FocusManager;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.util.CursorManager;
import com.kotcrab.vis.ui.widget.VisSplitPane.VisSplitPaneStyle;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPaneListener;

public class WindowPane extends WidgetGroup implements WindowManager.WindowDragListener {

    private static Color PIXEL_FILL_COLOR = new Color(190 / 255f, 145 / 255f, 23 / 255f, 0.3f);
    private static Color PIXEL_BORDER_COLOR = new Color(190 / 255f, 145 / 255f, 23 / 255f, 1);

    private static float PLACE_HOLDER_HIDDING_SPEED = 1f;
    private static float PLACE_HOLDER_SHOWING_SPEED = 1f;
    private static float ACCEPT_WINDOW_PERCENT = 0.03f;
    private static float ELEMENT_MINIMUM_PERCENT = 0.1f;
    private static float WINDOW_INSERT_DURATION = 0.3f;

    private static final int FORWARD = 0;
    private static final int BACKWARD = 1;
    private static final int TWOSIDES = 2;

    private VisSplitPaneStyle style;
    private boolean vertical = true;
    private int maxSize = 5;
    private int currentSize;
    private boolean enableComposite = true;

    private Rectangle handleOver;
    private int handleOverIndex;
    private Vector2 handlePosition = new Vector2();
    private Vector2 lastPoint = new Vector2();

    private int insertIndex = -1;
    private float desiredPercent;
    private float referencePer;

    private Array<Element> elements = new Array<>();
    private Array<Rectangle> handleBounds = new Array<>();

    private PixelRect pixelRect;

    public WindowPane() {
        this(true);
    }

    public WindowPane(boolean vertical) {
        this.vertical = vertical;
        String styleName = "default-" + (vertical ? "vertical" : "horizontal");
        style = VisUI.getSkin().get(styleName, VisSplitPaneStyle.class);
        pixelRect = new PixelRect();
        pixelRect.setFillColor(PIXEL_FILL_COLOR);
        pixelRect.setBorderColor(PIXEL_BORDER_COLOR);

        WindowManager.register(this);

        initialize();
    }

    public void setMaxSize(int maxSize) {
        if (maxSize < 1) throw new IllegalStateException("Maxsize must >= 1.");
        this.maxSize = maxSize;
    }

    public void setEnableComposite(boolean enableComposite) {
        this.enableComposite = enableComposite;
    }

    private void initialize() {
        addListener(new ClickListener() {
            Cursor.SystemCursor currentCursor;
            Cursor.SystemCursor targetCursor;

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                CursorManager.restoreDefaultCursor();
                currentCursor = null;
            }

            @Override
            public boolean mouseMoved(InputEvent event, float x, float y) {
                if (getHandleContaining(x, y) != null) {
                    if (vertical) {
                        targetCursor = Cursor.SystemCursor.VerticalResize;
                    } else {
                        targetCursor = Cursor.SystemCursor.HorizontalResize;
                    }

                    if (currentCursor != targetCursor) {
                        Gdx.graphics.setSystemCursor(targetCursor);
                        currentCursor = targetCursor;
                    }
                } else {
                    if (currentCursor != null) {
                        CursorManager.restoreDefaultCursor();
                        currentCursor = null;
                    }
                }

                return false;
            }
        });

        addListener(new InputListener() {
            int draggingPointer = -1;

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (isTouchable() == false) return false;

                if (draggingPointer != -1) return false;
                if (pointer == 0 && button != 0) return false;
                Rectangle containingHandle = getHandleContaining(x, y);
                if (containingHandle != null) {
                    handleOverIndex = handleBounds.indexOf(containingHandle, true);
                    FocusManager.resetFocus(getStage());

                    draggingPointer = pointer;
                    lastPoint.set(x, y);
                    handlePosition.set(containingHandle.x, containingHandle.y);
                    return true;
                }
                return false;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                if (pointer == draggingPointer) draggingPointer = -1;
                handleOver = getHandleContaining(x, y);
            }

            @Override
            public boolean mouseMoved(InputEvent event, float x, float y) {
                handleOver = getHandleContaining(x, y);
                return false;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (pointer != draggingPointer) return;

                if (!vertical) {
                    float delta = x - lastPoint.x;
                    horiDragged(handleOverIndex, delta);
                    lastPoint.set(x, y);
                } else {
                    float delta = y - lastPoint.y;
                    vertDragged(handleOverIndex, delta);
                    lastPoint.set(x, y);
                }
                invalidate();
            }
        });
    }

    private void horiDragged(int handleIndex, float delta) {
        float width = getWidth();
        float handleWidth = style.handle.getMinWidth();
        float availWidth = width - (handleBounds.size * handleWidth);

        Element before = elements.get(handleIndex);
        Element after = elements.get(handleIndex + 1);
        before.bound.width += delta;
        before.percent = before.bound.width / availWidth;
        after.bound.width -= delta;
        after.percent = after.bound.width / availWidth;

        if (delta > 0) replan(handleIndex + 1, BACKWARD);
        if (delta < 0) replan(handleIndex, FORWARD);
    }

    private void vertDragged(int handleIndex, float delta) {
        float height = getHeight();
        float handleHeight = style.handle.getMinHeight();
        float availHeight = height - (handleBounds.size * handleHeight);

        Element before = elements.get(handleIndex);
        Element after = elements.get(handleIndex + 1);
        before.bound.height -= delta;
        before.percent = before.bound.height / availHeight;
        after.bound.height += delta;
        after.percent = after.bound.height / availHeight;

        if (delta < 0) replan(handleIndex + 1, BACKWARD);
        if (delta > 0) replan(handleIndex, FORWARD);
    }

    private Rectangle getHandleContaining(float x, float y) {
        for (Rectangle rect : handleBounds) {
            if (rect.contains(x, y)) {
                return rect;
            }
        }

        return null;
    }

    //1.对于forward和backward，需要先设定好全部element的percent，然后进行规划;
    //2.对于twosides主要针对element的动态插入和移除，需要设定要index位置的element的percent，
    // 然后进行规划得到全部element的percent。
    private void replan(int index, int type) {

        Element element = elements.get(index);
        float percent = element.percent;

        if (type == TWOSIDES) {

            Element prev = null;
            Element next = null;
            if (index > 0) prev = elements.get(index - 1);
            if (index < elements.size - 1) next = elements.get(index + 1);

            if (referencePer <= 0) {
                if (prev != null && next != null) {
                    float ratio = prev.percent / (next.percent + prev.percent);
                    prev.percent -= referencePer * ratio;
                    next.percent -= referencePer * (1 - ratio);
                } else {
                    if (next != null) next.percent -= referencePer;
                    if (prev != null) prev.percent -= referencePer;
                }
            } else {
                if (prev == null && next == null) {
                    return;
                }
                if (prev == null) {
                    next.percent -= referencePer;
                    replan(index + 1, BACKWARD);
                } else if (next == null) {
                    prev.percent -= referencePer;
                    replan(index - 1, FORWARD);
                    return;
                } else {
                    float ratio = prev.percent / (next.percent + prev.percent);
                    float prevDec = ratio * referencePer;

                    float preTotal = 0;
                    for (int i = 0; i < index; i++) {
                        preTotal += elements.get(i).percent;
                    }
                    float preRedund = preTotal - ELEMENT_MINIMUM_PERCENT * index;
                    if (preRedund < prevDec) {
                        prevDec = preRedund * 0.9f;      // for accuracy
                    }
                    next.percent -= (referencePer - prevDec);
                    replan(index + 1, BACKWARD);
                    prev.percent -= prevDec;
                    replan(index - 1, FORWARD);
                }

            }

            return;
        }

        if (percent < ELEMENT_MINIMUM_PERCENT) {
            switch (type) {
                case FORWARD:
                    if (index == 0) {
                        float delta = ELEMENT_MINIMUM_PERCENT - percent;
                        element.percent = ELEMENT_MINIMUM_PERCENT;
                        Element next = elements.get(index + 1);
                        next.percent -= delta;
                        replan(index + 1, BACKWARD);
                    } else {
                        float delta = ELEMENT_MINIMUM_PERCENT - percent;
                        element.percent = ELEMENT_MINIMUM_PERCENT;
                        Element prev = elements.get(index - 1);
                        prev.percent -= delta;
                        replan(index - 1, FORWARD);
                    }
                    break;

                case BACKWARD:
                    if (index == elements.size - 1) {
                        float delta = ELEMENT_MINIMUM_PERCENT - percent;
                        element.percent = ELEMENT_MINIMUM_PERCENT;
                        Element prev = elements.get(index - 1);
                        prev.percent -= delta;
                        replan(index - 1, FORWARD);
                    } else {
                        float delta = ELEMENT_MINIMUM_PERCENT - percent;
                        element.percent = ELEMENT_MINIMUM_PERCENT;
                        Element next = elements.get(index + 1);
                        next.percent -= delta;
                        replan(index + 1, BACKWARD);
                    }
                    break;
            }

        }

    }

    @Override
    public void layout() {
        if (!vertical)
            calculateHorizBoundsAndPositions();
        else
            calculateVertBoundsAndPositions();

        for (Element element : elements) {
            Actor actor = element.actor;
            actor.setBounds(element.bound.x, element.bound.y, element.bound.width, element.bound.height);
            if (actor instanceof Layout) ((Layout) actor).validate();
        }

    }

    private void calculateHorizBoundsAndPositions() {
        if (elements.size == 0) return;

        float width = getWidth();
        float height = getHeight();
        float handleWidth = style.handle.getMinWidth();

        float availWidth = width - (handleBounds.size * handleWidth);

        float areaUsed = 0;
        float currentX = 0;
        for (int i = 0; i < elements.size - 1; i++) {
            Element element = elements.get(i);
            float areaWidth = element.percent * availWidth;
            areaUsed += areaWidth;
            element.bound.set(currentX, 0, areaWidth, height);
            currentX += areaWidth;
            handleBounds.get(i).set(currentX, 0, handleWidth, height);
            currentX += handleWidth;
        }
        elements.peek().bound.set(currentX, 0, availWidth - areaUsed, height);
    }

    private void calculateVertBoundsAndPositions() {
        if (elements.size == 0) return;

        float width = getWidth();
        float height = getHeight();
        float handleHeight = style.handle.getMinHeight();

        float availHeight = height - (handleBounds.size * handleHeight);

        float areaUsed = 0;
        float currentY = height;
        for (int i = 0; i < elements.size - 1; i++) {
            Element element = elements.get(i);
            float areaHeight = element.percent * availHeight;
            areaUsed += areaHeight;
            element.bound.set(0, currentY - areaHeight, width, areaHeight);
            currentY -= areaHeight;
            handleBounds.get(i).set(0, currentY - handleHeight, width, handleHeight);
            currentY -= handleHeight;
        }
        elements.peek().bound.set(0, 0, width, availHeight - areaUsed);
    }

    @Override
    public void act(float delta) {
        super.act(delta);

        for (int i = 0; i < elements.size; i++) {
            Element element = elements.get(i);
            if (element.state == Element.State.Hiding) {
                if (element.percent <= 0) {
                    super.removeActor(element.actor);
                    elements.removeIndex(i);
                    if (handleBounds.size > 0)
                        handleBounds.removeIndex(handleBounds.size - 1); //just keep size
                    insertIndex = -1;
                    currentSize--;
                } else {
                    referencePer = element.hide(delta);
                    replan(i, TWOSIDES);
                }
                invalidate();
            }

            if (element.state == Element.State.Showing) {
                if (element.percent >= element.targetPercent) {
                    element.state = Element.State.Idel;
                } else {
                    referencePer = element.show(delta);
                    replan(i, TWOSIDES);

                    invalidate();
                }
            }

        }

    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        validate();

        Color color = getColor();

        applyTransform(batch, computeTransform());

        for (int i = 0; i < elements.size; i++) {
            Element element = elements.get(i);
            Actor actor = element.actor;
            Rectangle bounds = element.bound;
            Rectangle scissor = element.scissor;
            getStage().calculateScissors(bounds, scissor);
            if (ScissorStack.pushScissors(scissor)) {
                if (actor.isVisible()) actor.draw(batch, parentAlpha * color.a);
                batch.flush();
                ScissorStack.popScissors();
            }
        }

        batch.setColor(color.r, color.g, color.b, parentAlpha * color.a);

        Drawable handle = style.handle;
        Drawable handleOver = style.handle;
        if (isTouchable() && style.handleOver != null) handleOver = style.handleOver;
        for (Rectangle rect : handleBounds) {
            if (this.handleOver == rect) {
                handleOver.draw(batch, rect.x, rect.y, rect.width, rect.height);
            } else {
                handle.draw(batch, rect.x, rect.y, rect.width, rect.height);
            }
        }

        //insert
        drawInsert(batch, parentAlpha);

        resetTransform(batch);
    }

    private void drawInsert(Batch batch, float parentAlpha) {

        if (insertIndex != -1) {

            Vector2 temp = new Vector2();
            localToStageCoordinates(temp);

            Element element = elements.get(insertIndex);
            Rectangle bound = element.bound;
            pixelRect.setHeight(bound.height);
            pixelRect.setWidth(bound.width);
            pixelRect.setPosition(bound.x + temp.x, bound.y + temp.y);

            pixelRect.draw(batch, parentAlpha);

        }

    }

    public void setWindows(FlexibleWindow... windows) {
        elements.clear();
        handleBounds.clear();

        float splits = windows.length;
        float percent = 1 / splits;

        for (FlexibleWindow window : windows) {
            currentSize++;
            if (currentSize > maxSize) {
                currentSize = maxSize;
                break;
            }

            window.setWindowPane(this);

            Element element = new Element();
            element.actor = window;

            elements.add(element);
            if (elements.size > 1)
                handleBounds.add(new Rectangle());
            super.addActor(window);

            element.percent = percent;
        }

        invalidate();
    }

    void removeWindow(FlexibleWindow window) {
        for (int i = 0; i < elements.size; i++) {
            Element element = elements.get(i);
            Actor actor = element.actor;
            if (actor == window) {

                if (elements.size == 1) {
                    super.removeActor(window);
                    elements.clear();
                    handleBounds.clear();
                    insertIndex = -1;
                    currentSize--;
                    return;
                }

                PlaceHolder placeHolder = new PlaceHolder();
                element.actor = placeHolder;
                element.state = Element.State.Hiding;

                super.removeActor(window);
                super.addActorAt(i, placeHolder);

                return;
            }
            if (enableComposite && actor instanceof CompositePane) {
                CompositePane pane = (CompositePane) actor;
                if (pane.hasWindow(window)) {
                    window.remove();
                    pane.removeWindow(window);

                    if (pane.windowSize() == 1) {
                        FlexibleWindow rest = pane.windows.first();
                        element.actor = rest;

                        super.removeActor(pane);
                        super.addActorAt(i, rest);
                    }

                    currentSize--;
                    return;
                }
            }
        }
    }

    void receiveWindow(FlexibleWindow window) {
        if (insertIndex < 0) return;

        Element replace = elements.get(insertIndex);

        if (!enableComposite && !(replace.actor instanceof PlaceHolder)) return;

        Rectangle bound = replace.bound;
        Vector2 temp = new Vector2(bound.x, bound.y);
        localToStageCoordinates(temp);

        Action sizeAction = Actions.sizeTo(bound.width, bound.height, WINDOW_INSERT_DURATION, Interpolation.fade);
        Action moveAction = Actions.moveTo(temp.x, temp.y, WINDOW_INSERT_DURATION, Interpolation.fade);

        if ((replace.actor instanceof FlexibleWindow)) {
            window.addAction(Actions.sequence(Actions.parallel(sizeAction, moveAction), Actions.run(() -> {

                super.removeActor(replace.actor);
                CompositePane compositePane = new CompositePane();
                compositePane.addWindow((FlexibleWindow) replace.actor);
                compositePane.addWindow(window);
                if (hasChildren())
                    super.addActorAt(insertIndex, compositePane);
                else super.addActor(compositePane);
                replace.actor = compositePane;

                window.setPrepareReceivePane(null);
                window.setWindowPane(this);

                currentSize++;
                insertIndex = -1;
            })));
            return;
        }

        if ((replace.actor instanceof CompositePane)) {
            window.addAction(Actions.sequence(Actions.parallel(sizeAction, moveAction), Actions.run(() -> {

                CompositePane compositePane = (CompositePane) replace.actor;
                compositePane.addWindow(window);

                window.setPrepareReceivePane(null);
                window.setWindowPane(this);

                currentSize++;
                insertIndex = -1;
            })));
            return;
        }


        window.addAction(Actions.sequence(Actions.parallel(sizeAction, moveAction), Actions.run(() -> {
            super.removeActor(replace.actor);
            super.addActorAt(insertIndex, window);
            replace.actor = window;

            window.setPrepareReceivePane(null);
            window.setWindowPane(this);

            currentSize++;
            insertIndex = -1;
        })));

    }

    private void hideAllPlaceHolder() {
        for (Element element : elements) {
            if (element.actor instanceof PlaceHolder) {
                element.state = Element.State.Hiding;
            }
        }
    }

    @Override
    public void enter(float x, float y, FlexibleWindow window) {

    }

    @Override
    public void exit(float x, float y, FlexibleWindow window) {
        hideAllPlaceHolder();
        insertIndex = -1;
    }

    private Element getElement(float percent) {
        float percentUsed = 0;
        for (int i = 0; i < elements.size; i++) {
            Element element = elements.get(i);
            percentUsed += element.percent;
            if (percentUsed > percent) return element;
        }
        return elements.peek();
    }

    @Override
    public void move(float x, float y, FlexibleWindow window) {
        if (currentSize == maxSize) return;

        float percent = 1 - y / getHeight();
        if (!vertical) percent = x / getWidth();

        if (insertIndex != -1) {
            Element element = getElement(percent);
            if (elements.get(insertIndex) == element) return;

            insertIndex = -1;
            hideAllPlaceHolder();
        }

        float prev = 0;
        float next = 0;

        if (elements.size == 0) {
            int newIndex = 0;
            processInsertPlaceHolder(newIndex, percent);
            return;
        }

        for (int i = 0; i < elements.size; i++) {
            Element element = elements.get(i);
            next += element.percent;
            if (percent >= prev && percent <= prev + ACCEPT_WINDOW_PERCENT) {
                int newIndex = i;
                processInsertPlaceHolder(newIndex, percent);
                return;
            }
            if (percent >= next - ACCEPT_WINDOW_PERCENT && percent <= next) {
                int newIndex = i + 1;
                processInsertPlaceHolder(newIndex, percent);
                return;
            }
            if (enableComposite && percent > prev + ACCEPT_WINDOW_PERCENT && percent < next - ACCEPT_WINDOW_PERCENT) {
                insertIndex = i;
                return;
            }
            prev += element.percent;
        }
    }

    private void processInsertPlaceHolder(int newIndex, float currentPer) {
        if (newIndex >= 0) {

            // find if placeHolder already exists
            Element placeHolder = null;
            for (int i = newIndex - 1; i <= newIndex + 1; i++) {
                if (i >= 0 && i <= elements.size - 1) {
                    Element element = elements.get(i);
                    Actor actor = element.actor;
                    if (actor instanceof PlaceHolder) {
                        placeHolder = element;
                        insertIndex = i;
                        break;
                    }
                }
            }
            if (placeHolder != null) {
                desiredPercent = 1f / (elements.size);
                placeHolder.targetPercent = desiredPercent;
                placeHolder.state = Element.State.Showing;
            } else {
                desiredPercent = 1f / (elements.size + 1);
                addPlaceHolder(newIndex, desiredPercent);
                insertIndex = newIndex;
            }

        }
    }

    private void addPlaceHolder(int index, float targetPercent) {
        Element element = new Element();
        PlaceHolder placeHolder = new PlaceHolder();
        element.actor = placeHolder;
        element.targetPercent = targetPercent;
        element.state = Element.State.Showing;

        elements.insert(index, element);

        if (elements.size > 1)
            handleBounds.add(new Rectangle()); // keep size

        super.addActorAt(index, placeHolder);
    }

    @Override
    public Actor hit(float x, float y, boolean touchable) {
        if (touchable && getTouchable() == Touchable.disabled) return null;
        if (getHandleContaining(x, y) != null) {
            return this;
        } else {
            return super.hit(x, y, touchable);
        }
    }

    @Override
    public float getPrefWidth() {
        float width = 0;
        for (Actor actor : getChildren()) {
            width = actor instanceof Layout ? ((Layout) actor).getPrefWidth() : actor.getWidth();
        }
        if (!vertical) width += handleBounds.size * style.handle.getMinWidth();
        return width;
    }

    @Override
    public float getPrefHeight() {
        float height = 0;
        for (Actor actor : getChildren()) {
            height = actor instanceof Layout ? ((Layout) actor).getPrefHeight() : actor.getHeight();

        }
        if (vertical) height += handleBounds.size * style.handle.getMinHeight();
        return height;
    }

    @Override
    public float getMinWidth() {
        return 0;
    }

    @Override
    public float getMinHeight() {
        return 0;
    }


    static class Element {

        enum State {
            Hiding, Showing, Idel
        }

        Element() {

        }

        Rectangle bound = new Rectangle();
        Rectangle scissor = new Rectangle();
        Actor actor;
        State state = State.Idel;
        //        Type type;
        float percent;
        float targetPercent;

        float hide(float delta) {
            float deltaPer = PLACE_HOLDER_HIDDING_SPEED * delta;
            if (deltaPer > percent) deltaPer = percent;
            percent -= deltaPer;
            return -deltaPer;
        }

        float show(float delta) {
            float deltaPer = PLACE_HOLDER_SHOWING_SPEED * delta;
            if (percent + deltaPer > targetPercent) deltaPer = targetPercent - percent;
            percent += deltaPer;
            return deltaPer;
        }
    }

    public static class PlaceHolder extends Actor {
        //        private Drawable drawable = VisUI.getSkin().getDrawable("border");
        public void draw(Batch batch, float parentAlpha) {
//            drawable.draw(batch, getX(), getY(), getWidth(), getHeight());
        }

    }

    public static class CompositePane extends Table {

        private TabbedPane tabbedPane;
        private Table windowTable;
        private Array<FlexibleWindow> windows = new Array<>(5);

        public CompositePane() {
            TabbedPane.TabbedPaneStyle style = VisUI.getSkin().get(TabbedPane.TabbedPaneStyle.class);
            style.draggable = false;
            tabbedPane = new TabbedPane(style);

            tabbedPane.setAllowTabDeselect(false);

            windowTable = new Table();

            add(windowTable).fill().expand().row();
            add(tabbedPane.getTable()).fillX().expandX();

            tabbedPane.addListener(new TabbedPaneListener() {
                @Override
                public void switchedTab(Tab tab) {
                    windowTable.clear();
                    windowTable.add(tab.getContentTable()).fill().expand();
                }

                @Override
                public void removedTab(Tab tab) {

                }

                @Override
                public void removedAllTabs() {

                }
            });
        }

        public void addWindow(FlexibleWindow window) {
            Tab tab = new Tab(false, false) {
                @Override
                public String getTabTitle() {
                    return window.getTitleLabel().getText().toString();
                }

                @Override
                public Table getContentTable() {
                    Table table = new Table();
                    table.add(window).fill().expand();
                    return table;
                }
            };

            tabbedPane.add(tab);
            tabbedPane.switchTab(tab);

            windows.add(window);
        }

        public void removeWindow(FlexibleWindow window) {
            String title = window.getTitleLabel().getText().toString();
            Array<Tab> tabs = tabbedPane.getTabs();
            for (Tab tab : tabs) {
                if (title.equals(tab.getTabTitle())) {
                    tabbedPane.remove(tab);
                }
            }

            windows.removeValue(window, true);
        }

        public boolean hasWindow(FlexibleWindow window) {
            return windows.contains(window, true);
        }

        public int windowSize() {
            return windows.size;
        }

    }


    @Override
    public void addActorAfter(Actor actorAfter, Actor actor) {
        throw new UnsupportedOperationException("Manual actor management not supported by MultiSplitPane");
    }

    @Override
    public void addActor(Actor actor) {
        throw new UnsupportedOperationException("Manual actor management not supported by MultiSplitPane");
    }

    @Override
    public void addActorAt(int index, Actor actor) {
        throw new UnsupportedOperationException("Manual actor management not supported by MultiSplitPane");
    }

    @Override
    public void addActorBefore(Actor actorBefore, Actor actor) {
        throw new UnsupportedOperationException("Manual actor management not supported by MultiSplitPane");
    }

    @Override
    public boolean removeActor(Actor actor) {
        throw new UnsupportedOperationException("Manual actor management not supported by MultiSplitPane");
    }
}
