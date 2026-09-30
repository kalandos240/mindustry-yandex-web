#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 3:
    raise SystemExit("usage: patch-mindustry-effects-web.py <Effect.java> <EffectStateComp.java>")

effect_path = Path(sys.argv[1])
state_path = Path(sys.argv[2])

effect = effect_path.read_text()
state = state_path.read_text()

field_anchor = """    private static final EffectContainer container = new EffectContainer();

    public static final Seq<Effect> all = new Seq<>();
"""
field_new = """    private static final EffectContainer container = new EffectContainer();

    // Web-only visual safety valve. A value <= 0 keeps stock/unlimited behavior.
    private static int webMaxActiveEffects;
    private static int webActiveEffects;
    private static int webDroppedEffects;

    public static final Seq<Effect> all = new Seq<>();
"""
if field_anchor not in effect:
    raise SystemExit("Effect Web budget field anchor changed")
effect = effect.replace(field_anchor, field_new, 1)

method_anchor = """    public Effect baseRotation(float d){
        baseRotation = d;
        return this;
    }

"""
method_new = """    public Effect baseRotation(float d){
        baseRotation = d;
        return this;
    }

    public static void setWebMaxActiveEffects(int max){
        webMaxActiveEffects = Math.max(0, max);
        webActiveEffects = 0;
        webDroppedEffects = 0;
    }

    public static int webMaxActiveEffects(){
        return webMaxActiveEffects;
    }

    public static int webActiveEffects(){
        return webActiveEffects;
    }

    public static int webDroppedEffects(){
        return webDroppedEffects;
    }

    public static void webResetEffectBudget(){
        webActiveEffects = 0;
        webDroppedEffects = 0;
    }

    public static void webEffectRemoved(){
        if(webActiveEffects > 0) webActiveEffects--;
    }

"""
if method_anchor not in effect:
    raise SystemExit("Effect Web budget method anchor changed")
effect = effect.replace(method_anchor, method_new, 1)

create_anchor = """        if(Core.camera.bounds(Tmp.r1).overlaps(Tmp.r2.setCentered(x, y, clip))){
            if(!initialized){
"""
create_new = """        // Once the mobile Web visual budget is saturated, reject immediately.
        // Avoid camera-bounds work and delayed Time.run() allocation for excess particles.
        if(webMaxActiveEffects > 0 && webActiveEffects >= webMaxActiveEffects){
            webDroppedEffects++;
            return;
        }

        if(Core.camera.bounds(Tmp.r1).overlaps(Tmp.r2.setCentered(x, y, clip))){
            if(webMaxActiveEffects > 0) webActiveEffects++;

            if(!initialized){
"""
if create_anchor not in effect:
    raise SystemExit("Effect.create Web budget anchor changed")
effect = effect.replace(create_anchor, create_new, 1)

add_anchor = """    protected void add(float x, float y, float rotation, Color color, Object data){
        var entity = EffectState.create();
        entity.effect = this;
"""
add_new = """    protected void add(float x, float y, float rotation, Color color, Object data){
        var entity = EffectState.create();
        entity.webBudgetCounted = webMaxActiveEffects > 0;
        entity.effect = this;
"""
if add_anchor not in effect:
    raise SystemExit("Effect.add Web budget anchor changed")
effect = effect.replace(add_anchor, add_new, 1)

state_field_anchor = """    Effect effect;
    Object data;

"""
state_field_new = """    Effect effect;
    Object data;
    boolean webBudgetCounted;

"""
if state_field_anchor not in state:
    raise SystemExit("EffectStateComp Web budget field anchor changed")
state = state.replace(state_field_anchor, state_field_new, 1)

state_draw_anchor = """    @Override
    public void draw(){
        lifetime = effect.render(id, color, time, lifetime, rotation, x, y, data);
    }

"""
state_draw_new = """    @Override
    public void draw(){
        lifetime = effect.render(id, color, time, lifetime, rotation, x, y, data);
    }

    @Override
    public void remove(){
        if(webBudgetCounted){
            Effect.webEffectRemoved();
            webBudgetCounted = false;
        }
    }

"""
if state_draw_anchor not in state:
    raise SystemExit("EffectStateComp.remove Web budget anchor changed")
state = state.replace(state_draw_anchor, state_draw_new, 1)

effect_path.write_text(effect)
state_path.write_text(state)
