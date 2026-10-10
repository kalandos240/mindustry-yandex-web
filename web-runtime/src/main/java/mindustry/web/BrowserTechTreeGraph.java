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
import mindustry.gen.*;
import mindustry.ui.layout.*;
import mindustry.ui.layout.TreeLayout.*;
import mindustry.ui.layout.BranchTreeLayout.TreeLocation;
import java.util.Arrays;

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
    private float minX, minY, maxX, maxY;

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
        nodeWidth = baseNodeWidth * zoom;
        nodeHeight = baseNodeHeight * zoom;
        column = baseColumn * zoom;
        row = baseRow * zoom;
        if(root == null) return;

        // Same two-sided BranchTreeLayout used by pinned vanilla ResearchDialog:
        // technologies branch above/below a centered root, not rightward in rows.
        LayoutNode branchRoot = makeLayout(root, null);
        LayoutNode[] original = branchRoot.children;
        int split = (original.length + 1) / 2;
        LayoutNode[] upper = Arrays.copyOfRange(original, 0, split);
        LayoutNode[] lower = Arrays.copyOfRange(original, split, original.length);

        branchRoot.children = upper;
        BranchTreeLayout top = new BranchTreeLayout();
        top.gapBetweenLevels = Math.max(2f, row - nodeHeight);
        top.gapBetweenNodes = Math.max(2f, column - nodeWidth);
        top.rootLocation = TreeLocation.top;
        top.layout(branchRoot);
        float previousY = branchRoot.y;
        if(lower.length > 0){
            branchRoot.children = lower;
            branchRoot.cachedWidth = -1f;
            BranchTreeLayout bottom = new BranchTreeLayout();
            bottom.gapBetweenLevels = top.gapBetweenLevels;
            bottom.gapBetweenNodes = top.gapBetweenNodes;
            bottom.rootLocation = TreeLocation.bottom;
            bottom.layout(branchRoot);
            shift(upper, branchRoot.y - previousY);
        }
        branchRoot.children = original;
        minX = minY = Float.POSITIVE_INFINITY;
        maxX = maxY = Float.NEGATIVE_INFINITY;
        measureLayout(branchRoot);
        preferredWidth = margin * 2f + maxX - minX + nodeWidth;
        preferredHeight = margin * 2f + maxY - minY + nodeHeight;
        cacheCoordinates(branchRoot);
        for(TechNode node : visible){
            Vec2 point = coordinates.get(node);
            if(point == null) continue;
            // Stock Mindustry ResearchDialog uses the small Styles.nodei
            // image button with a yellow border for researched technologies
            // and a red border for locked dependencies. No duplicate labels.
            boolean parentReady = node.parent == null || node.parent.content.unlocked();
            ImageButton button = !parentReady || node.content.uiIcon == null
                ? new ImageButton(Icon.lock, Styles.nodei)
                : new ImageButton(node.content.uiIcon, Styles.nodei);
            button.name = "web-research-node-" + node.content.name;
            button.resizeImage(32f * zoom);
            button.getImage().setScaling(Scaling.fit);
            boolean unlocked = node.content.unlocked();
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

    private static final class LayoutNode extends TreeNode<LayoutNode>{
        final TechNode content;

        LayoutNode(TechNode content, LayoutNode parent, float size){
            this.content = content;
            this.parent = parent;
            width = height = size;
        }
    }

    private LayoutNode makeLayout(TechNode node, LayoutNode parent){
        LayoutNode result = new LayoutNode(node, parent, nodeWidth);
        visible.add(node);
        if(expanded.contains(node) && node.children.size > 0){
            result.children = new LayoutNode[node.children.size];
            int i = 0;
            for(TechNode child : node.children){
                result.children[i++] = makeLayout(child, result);
            }
        }else{
            result.children = new LayoutNode[0];
        }
        return result;
    }

    private void shift(LayoutNode[] children, float amount){
        for(LayoutNode child : children){
            child.y += amount;
            shift(child.children, amount);
        }
    }

    private void measureLayout(LayoutNode node){
        minX = Math.min(minX, node.x);
        maxX = Math.max(maxX, node.x);
        minY = Math.min(minY, node.y);
        maxY = Math.max(maxY, node.y);
        for(LayoutNode child : node.children) measureLayout(child);
    }

    private void cacheCoordinates(LayoutNode node){
        coordinates.put(node.content, new Vec2(
            margin + node.x - minX + nodeWidth * 0.5f,
            margin + node.y - minY + nodeHeight * 0.5f));
        for(LayoutNode child : node.children) cacheCoordinates(child);
    }

    public int nodeCount(){
        return visible.size;
    }
    
    /** Direct lookup for selected-node popovers; never scan Arc Scene on every frame. */
    public Button buttonFor(TechNode node){
        return buttons.get(node);
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
        // Position from the cached original Mindustry layout, O(N) on rebuild.
        for(TechNode node : visible){
            Vec2 point = coordinates.get(node);
            Button button = buttons.get(node);
            if(button != null && point != null){
                button.setPosition(point.x - nodeWidth * 0.5f,
                    point.y - nodeHeight * 0.5f);
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
                float sx = getX(Align.left) + fromActor.getX(Align.left) + fromActor.getWidth() * 0.5f;
                float ex = getX(Align.left) + toActor.getX(Align.left) + toActor.getWidth() * 0.5f;
                float y1 = fromActor.getY(Align.bottom) + fromActor.getHeight() * 0.5f;
                float y2 = toActor.getY(Align.bottom) + toActor.getHeight() * 0.5f;
                boolean upwards = y2 >= y1;
                float sy = getY(Align.bottom) + fromActor.getY(Align.bottom) +
                    (upwards ? fromActor.getHeight() : 0f);
                float ey = getY(Align.bottom) + toActor.getY(Align.bottom) +
                    (upwards ? 0f : toActor.getHeight());
                float bendY = (sy + ey) * 0.5f;
                Lines.line(sx, sy, sx, bendY);
                Lines.line(sx, bendY, ex, bendY);
                Lines.line(ex, bendY, ex, ey);
            }
            Draw.reset();
        }
        super.draw();
    }
}
