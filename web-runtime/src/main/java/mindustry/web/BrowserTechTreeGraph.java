package mindustry.web;

import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
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
    private static final float baseNodeWidth = 157f, baseNodeHeight = 47f;
    private static final float baseColumn = 205f, baseRow = 69f;
    private static final float margin = 23f;
    private final ObjectMap<TechNode, Vec2> coordinates = new ObjectMap<>();
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
            Button button = new Button(Styles.defaultb);
            button.name = "web-research-node-" + node.content.name;
            if(node.content.uiIcon != null){
                button.add(new Image(node.content.uiIcon))
                    .size(28f * zoom).padLeft(5f * zoom);
            }
            Label label = new Label(node.content.localizedName);
            label.setFontScale((Core.graphics.isPortrait() ? 0.77f : 0.82f) * zoom);
            label.setWrap(true);
            button.add(label).growX().left().padLeft(4f * zoom);
            Label symbol = new Label(node.content.unlocked() ? "+" :
                node.children.size > 0 ? (expanded.contains(node) ? "-" : ">") : "");
            symbol.setFontScale(0.83f * zoom);
            button.add(symbol).width(15f * zoom).padRight(3f * zoom);
            if(node.parent != null && !node.parent.content.unlocked()){
                button.setColor(Color.lightGray);
            }
            button.setSize(nodeWidth, nodeHeight);
            button.setPosition(point.x - nodeWidth * 0.5f,
                point.y - nodeHeight * 0.5f);
            button.clicked(() -> this.clicked.get(node));
            addChild(button);
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
            for(var child : getChildren()){
                if(("web-research-node-" + node.content.name).equals(child.name)){
                    child.setPosition(point.x - nodeWidth * 0.5f,
                        centerY - nodeHeight * 0.5f);
                    break;
                }
            }
        }
    }

    @Override
    public void draw(){
        if(root != null){
            Draw.color(Pal.accent);
            Lines.stroke(Math.max(1.1f, zoom * 1.5f));
            for(TechNode node : visible){
                if(node.parent == null || !visible.contains(node.parent)) continue;
                var fromActor = find("web-research-node-" + node.parent.content.name);
                var toActor = find("web-research-node-" + node.content.name);
                if(fromActor == null || toActor == null) continue;
                float sx = getX() + fromActor.x + fromActor.width;
                float ex = getX() + toActor.x;
                float sy = getY() + fromActor.y + fromActor.height * 0.5f;
                float ey = getY() + toActor.y + toActor.height * 0.5f;
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
