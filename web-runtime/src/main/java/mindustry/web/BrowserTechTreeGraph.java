package mindustry.web;

import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.util.*;
import arc.math.geom.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import mindustry.content.TechTree.*;
import mindustry.ui.*;
import mindustry.graphics.*;

/**
 * Scrollable orthogonal tech graph built from original TechNode.parent/children.
 * Unlike the desktop ResearchDialog this allocates only buttons for currently
 * expanded branches; it is safe for the TeaVM sprite/heap budget.
 *
 * Zoom changes actual node/edge spacing instead of scaling rasterized fonts.
 * The enclosing stock ScrollPane handles touch drag and two-axis movement.
 */
public final class BrowserTechTreeGraph extends WidgetGroup{
    private static final float baseNodeWidth = 46f, baseNodeHeight = 46f;
    private static final float baseColumn = 79f, baseRow = 68f;
    private static final float margin = 23f;
    private final ObjectMap<TechNode, Vec2> coordinates = new ObjectMap<>();
    // Lookup by the actual TechNode, not O(N) Scene.find() on every frame.
    private final ObjectMap<TechNode, Button> buttons = new ObjectMap<>();
    private final ObjectSet<TechNode> visible = new ObjectSet<>();
    private TechNode root;
    private ObjectSet<TechNode> expanded;
    private Cons<TechNode> clicked;
    private float zoom = 1f, preferredWidth = 300f, preferredHeight = 300f;
    private float nodeWidth, nodeHeight, column, row;
    private int leaves, maxDepth;

    public void rebuild(TechNode root, ObjectSet<TechNode> expanded,
                        float zoom, Cons<TechNode> clicked){
        this.root = root;
        this.expanded = expanded;
        this.clicked = clicked;
        this.zoom = zoom;
        clear();
        coordinates.clear();
        buttons.clear();
        visible.clear();
        leaves = 0;
        maxDepth = 0;
        nodeWidth = baseNodeWidth * zoom;
        nodeHeight = baseNodeHeight * zoom;
        column = baseColumn * zoom;
        row = baseRow * zoom;

        if(root == null) return;
        place(root, 0);
        preferredWidth = margin * 2f + maxDepth * column + nodeWidth;
        preferredHeight = margin * 2f + Math.max(1, leaves) * row;
        for(TechNode node : visible){
            Vec2 point = coordinates.get(node);
            if(point == null) continue;
            // Stock Mindustry ResearchDialog uses the small Styles.nodei
            // image button with a yellow border for researched technologies
            // and a red border for locked dependencies. No duplicate labels.
            ImageButton button = node.content.uiIcon == null
                ? new ImageButton(Icon.lock, Styles.nodei)
                : new ImageButton(node.content.uiIcon, Styles.nodei);
            button.name = "web-research-node-" + node.content.name;
            button.resizeImage(32f * zoom);
            button.getImage().setScaling(Scaling.fit);
            boolean unlocked = node.content.unlocked();
            boolean parentReady = node.parent == null || node.parent.content.unlocked();
            button.getStyle().up = unlocked ? Tex.buttonOver :
                parentReady ? Tex.button : Tex.buttonRed;
            if(!unlocked) button.getImage().setColor(parentReady ? Color.gray : Pal.gray);
            button.setSize(nodeWidth, nodeHeight);
            button.setPosition(point.x - nodeWidth * 0.5f,
                point.y - nodeHeight * 0.5f);
            button.clicked(() -> this.clicked.get(node));
            addChild(button);
            buttons.put(node, button);
        }
        invalidateHierarchy();
    }

    private float place(TechNode node, int depth){
        if(node == null) return 0;
        maxDepth = Math.max(maxDepth, depth);
        visible.add(node);
        float logicalRow;
        if(expanded.contains(node) && node.children.size > 0){
            float first = -1, last = -1;
            for(TechNode child : node.children){
                float y = place(child, depth + 1);
                if(first < 0) first = y;
                last = y;
            }
            logicalRow = (first + last) * 0.5f;
        }else{
            logicalRow = leaves++;
        }
        // Coordinates are converted to bottom-left scene origin after all
        // leaves are placed, ensuring the same layout on mobile and desktop.
        coordinates.put(node, new Vec2(margin + depth * column + nodeWidth / 2f,
            logicalRow));
        return logicalRow;
    }

    public int nodeCount(){
        return visible.size;
    }

    @Override
    public float getPrefWidth(){
        return preferredWidth;
    }

    @Override
    public float getPrefHeight(){
        return preferredHeight;
    }

    @Override
    public void layout(){
        // Convert logical top-to-bottom row indices to scene Y positions.
        // This is idempotent because the coordinate is set in rebuild and
        // updated only once when new layout data has arrived.
        for(TechNode node : visible){
            Vec2 point = coordinates.get(node);
            if(point == null) continue;
            float centerY = preferredHeight - margin - (point.y + 0.5f) * row;
            Button button = buttons.get(node);
            if(button != null){
                button.setPosition(point.x - nodeWidth * 0.5f,
                    centerY - nodeHeight * 0.5f);
            }
        }
    }

    @Override
    public void draw(){
        validate();
        if(root != null){
            Lines.stroke(Math.max(1.1f, zoom * 1.8f));
            for(TechNode node : visible){
                if(node.parent == null || !visible.contains(node.parent)) continue;
                Draw.color(node.parent.content.unlocked() ? Pal.accent : Color.darkGray);
                Button fromActor = buttons.get(node.parent);
                Button toActor = buttons.get(node);
                if(fromActor == null || toActor == null) continue;
                float sx = getX(Align.left) + fromActor.getX(Align.left) + fromActor.getWidth();
                float ex = getX(Align.left) + toActor.getX(Align.left);
                float sy = getY(Align.bottom) + fromActor.getY(Align.bottom) + fromActor.getHeight() * 0.5f;
                float ey = getY(Align.bottom) + toActor.getY(Align.bottom) + toActor.getHeight() * 0.5f;
                float bend = (sx + ex) * 0.5f;
                Lines.line(sx, sy, bend, sy);
                Lines.line(bend, sy, bend, ey);
                Lines.line(bend, ey, ex, ey);
            }
            Draw.reset();
        }
        super.draw();
    }
}
