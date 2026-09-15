#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry"
ENTITY_GROUP = CORE / "entities" / "EntityGroup.java"
AI_CONTROLLER = CORE / "entities" / "units" / "AIController.java"

for path in (ENTITY_GROUP, AI_CONTROLLER):
    if not path.is_file():
        raise SystemExit(f"Missing pinned Mindustry unit diagnostic source: {path}")

# Preserve EntityGroup's mutable iteration index/removal semantics, but enrich any
# failing unit update with the concrete unit identity. This is diagnostic-only and
# does not skip, reorder, retry or otherwise change gameplay updates.
entity_group = ENTITY_GROUP.read_text(encoding="utf-8")
old_group_update = '''    public void update(){
        for(index = 0; index < array.size; index++){
            array.items[index].update();
        }
    }
'''
new_group_update = '''    public void update(){
        for(index = 0; index < array.size; index++){
            T entity = array.items[index];
            try{
                entity.update();
            }catch(Throwable error){
                if(entity instanceof Unit unit){
                    throw new IllegalStateException(
                        "Web unit entity update failed: id=" + unit.id +
                        ", type=" + (unit.type == null ? "null" : unit.type.name) +
                        ", team=" + (unit.team == null ? "null" : unit.team.id) +
                        ", controller=" + (unit.controller() == null ? "null" : "set"),
                        error
                    );
                }
                throw new IllegalStateException("Web entity-group member update failed: id=" + entity.id(), error);
            }
        }
    }
'''
if entity_group.count(old_group_update) != 1:
    raise SystemExit("Unit diagnostic EntityGroup.update anchor no longer matches pinned upstream")
ENTITY_GROUP.write_text(entity_group.replace(old_group_update, new_group_update, 1), encoding="utf-8")

ai = AI_CONTROLLER.read_text(encoding="utf-8")
old_ai_update = '''    @Override
    public void updateUnit(){
        //use fallback AI when possible
        if(useFallback() && (fallback != null || (fallback = fallback()) != null)){
            if(fallback.unit != unit) fallback.unit(unit);
            fallback.updateUnit();
            return;
        }

        updateVisuals();
        updateTargeting();
        updateMovement();
    }
'''
new_ai_update = '''    @Override
    public void updateUnit(){
        //use fallback AI when possible
        if(useFallback() && (fallback != null || (fallback = fallback()) != null)){
            try{
                if(fallback.unit != unit) fallback.unit(unit);
                fallback.updateUnit();
            }catch(Throwable error){
                throw new IllegalStateException("Web AI update failed at fallback", error);
            }
            return;
        }

        try{
            updateVisuals();
        }catch(Throwable error){
            throw new IllegalStateException("Web AI update failed at visuals", error);
        }
        try{
            updateTargeting();
        }catch(Throwable error){
            throw new IllegalStateException("Web AI update failed at targeting", error);
        }
        try{
            updateMovement();
        }catch(Throwable error){
            throw new IllegalStateException("Web AI update failed at movement", error);
        }
    }
'''
if ai.count(old_ai_update) != 1:
    raise SystemExit("Unit diagnostic AIController.updateUnit anchor no longer matches pinned upstream")
ai = ai.replace(old_ai_update, new_ai_update, 1)

old_pathfind = '''        Tile tile = unit.tileOn();
        if(tile == null) return;
        Tile targetTile = pathfinder.getField(unit.team, costType, pathTarget).getNextTile(tile, avoidance && unit.collisionLayer() == PhysicsProcess.layerGround ? unit.id : 0);

        if((tile == targetTile && stopAtTargetTile) || !unit.canPass(targetTile.x, targetTile.y)) return;
'''
new_pathfind = '''        Tile tile = unit.tileOn();
        if(tile == null) return;

        Tile targetTile;
        try{
            targetTile = pathfinder.getField(unit.team, costType, pathTarget).getNextTile(tile, avoidance && unit.collisionLayer() == PhysicsProcess.layerGround ? unit.id : 0);
        }catch(Throwable error){
            throw new IllegalStateException(
                "Web AI pathfind failed at flowfield-next-tile: unit=" + unit.id +
                ", type=" + unit.type.name + ", cost=" + costType + ", target=" + pathTarget,
                error
            );
        }
        if(targetTile == null){
            throw new IllegalStateException("Web AI pathfind returned null target tile for non-null source tile");
        }

        if((tile == targetTile && stopAtTargetTile) || !unit.canPass(targetTile.x, targetTile.y)) return;
'''
if ai.count(old_pathfind) != 1:
    raise SystemExit("Unit diagnostic AIController.pathfind anchor no longer matches pinned upstream")
AI_CONTROLLER.write_text(ai.replace(old_pathfind, new_pathfind, 1), encoding="utf-8")

print("Added per-unit and AI-stage browser diagnostics without changing update semantics")
