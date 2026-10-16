package mindustry.web;

import arc.math.geom.*;
import mindustry.ai.types.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.world.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/**
 * Opt-in real-game exercise of a unit actually produced by the stock factory:
 * CommandAI movement -> weapon-owned Bullet -> damage to a hostile wall.
 *
 * Only runs with ?mindustryProducedUnitBehaviorSmoke=1. A hostile wall is
 * deliberately placed as a CI target; no unit movement, damage or projectile
 * is synthesized. RTS input is separately covered by native desktop/mobile
 * DOM command tests.
 */
public final class BrowserProducedUnitBehaviorSmoke{
    private static boolean checked, enabled, complete, fired;
    private static int stage, frames, stageFrames;
    private static float initialX, initialY, wallStartHealth;
    private static Unit produced;
    private static Building enemy;
    private static CommandAI ai;
    private static String planet;

    private BrowserProducedUnitBehaviorSmoke(){}

    public static void update(){
        if(!requested() || complete) return;
        if(state == null || !state.isPlaying() || !BrowserCampaignRuntime.active()
            || state.rules == null || state.rules.sector == null) return;
        if(++frames > 1200)
            throw new IllegalStateException("Produced unit combat smoke timed out at stage " + stage);

        if(stage == 0){
            produced = BrowserUnitProductionSmoke.producedUnit();
            if(produced == null) return;
            planet = state.rules.sector.planet.name;
            if(!(produced.controller() instanceof CommandAI command) || !produced.isCommandable()
                || produced.type.weapons.isEmpty())
                throw new IllegalStateException("Real " + produced.type.name +
                    " cannot execute original movement and weapon AI");
            ai = command;
            initialX = produced.x;
            initialY = produced.y;
            Vec2 destination = chooseDestination(produced);
            if(destination == null)
                throw new IllegalStateException("No navigable original terrain for produced " +
                    produced.type.name + " on " + planet);
            ai.commandPosition(destination);
            stage = 1;
            stageFrames = 0;
            markStage("original-command-move", planet);
            return;
        }

        if(!produced.isAdded() || !produced.isValid()){
            throw new IllegalStateException("Real factory-produced " +
                produced.type.name + " despawned before combat test");
        }

        if(stage == 1){
            if(produced.dst(initialX, initialY) >= 12f){
                stage = 2;
                stageFrames = 0;
                markStage("original-unit-moved", planet);
                return;
            }
            if(++stageFrames > 360)
                throw new IllegalStateException("Produced " + produced.type.name +
                    " did not follow stock CommandAI movement: from=" +
                    initialX + "," + initialY + " now=" + produced.x + "," + produced.y +
                    " target=" + ai.targetPos);
            return;
        }

        if(stage == 2){
            // Find unobstructed, naturally empty terrain within weapon range
            // and place a hostile inert wall as a target fixture. It remains
            // a genuine Building receiving damage from real Bullet physics.
            int cx = (int)(produced.x / tilesize);
            int cy = (int)(produced.y / tilesize);
            int[][] offsets = {
                {5, 0}, {4, 2}, {4, -2}, {0, 5},
                {0, -5}, {-5, 0}, {-4, 2}, {-4, -2},
                {3, 0}, {0, 3}, {-3, 0}, {0, -3}
            };
            Block wall = "erekir".equals(planet) ? Blocks.berylliumWall : Blocks.copperWall;
            for(int[] offset : offsets){
                Tile candidate = world.tile(cx + offset[0], cy + offset[1]);
                if(candidate == null || candidate.block() != Blocks.air
                    || candidate.floor().isDeep() || !candidate.floor().placeableOn
                    || world.getDarkness(candidate.x, candidate.y) >= 3) continue;
                if(produced.dst(candidate.worldx(), candidate.worldy()) >= produced.range() - 5f)
                    continue;

                candidate.setBlock(wall, state.rules.waveTeam, 0);
                if(candidate.build == null || candidate.build.team == produced.team())
                    throw new IllegalStateException("Original hostile combat target missing");
                enemy = candidate.build;
                wallStartHealth = enemy.health;
                ai.commandTarget(enemy);
                stage = 3;
                stageFrames = 0;
                markStage("original-weapon-attack", planet);
                return;
            }
            throw new IllegalStateException("No suitable hostile target for original " +
                produced.type.name + " on " + planet);
        }

        if(stage == 3){
            // Every shot remains an original owned Bullet from the actual
            // factory-produced unit. Other allied/enemy weapons cannot make
            // this check pass just by damaging our temporary target.
            Groups.bullet.each(b -> {
                if(b.owner == produced) fired = true;
            });
            if(fired && (enemy.health < wallStartHealth - 0.01f ||
                !enemy.isAdded())){
                complete = true;
                markPassed(planet, produced.type.name, frames, stageFrames,
                    initialX, initialY, produced.x, produced.y);
                return;
            }
            if(++stageFrames > 500)
                throw new IllegalStateException("Real " + produced.type.name +
                    " failed to fire and damage original hostile wall: bullet=" + fired +
                    " health=" + enemy.health + "/" + wallStartHealth +
                    " target=" + ai.attackTarget);
        }
    }

    private static Vec2 chooseDestination(Unit unit){
        int x = (int)(unit.x / tilesize), y = (int)(unit.y / tilesize);
        int[][] moves = {
            {7, 0}, {6, 2}, {6, -2}, {0, 7},
            {0, -7}, {-7, 0}, {-6, 2}, {-6, -2}
        };
        for(int[] off : moves){
            Tile dest = world.tile(x + off[0], y + off[1]);
            if(dest != null && !dest.solid() && !dest.floor().isDeep()
                && world.getDarkness(dest.x, dest.y) < 3)
                return new Vec2(dest.worldx(), dest.worldy());
        }
        return null;
    }

    private static boolean requested(){
        if(!checked){
            enabled = queryRequested();
            checked = true;
        }
        return enabled;
    }

    @JSBody(script="return new URLSearchParams(location.search).get('mindustryProducedUnitBehaviorSmoke') === '1';")
    private static native boolean queryRequested();

    @JSBody(params={"stage","planet"},script="""
        document.documentElement.setAttribute('data-mindustry-produced-unit-behavior-stage',stage);
        document.documentElement.setAttribute('data-mindustry-produced-unit-behavior-planet',planet);
        """)
    private static native void markStage(String stage,String planet);

    @JSBody(params={"planet","type","frames","attackFrames","beforeX","beforeY","afterX","afterY"},script="""
        const root=document.documentElement;
        root.setAttribute('data-mindustry-produced-unit-behavior','passed');
        root.setAttribute('data-mindustry-produced-unit-behavior-planet',planet);
        root.setAttribute('data-mindustry-produced-unit-behavior-unit',type);
        root.setAttribute('data-mindustry-produced-unit-behavior-movement','stock-CommandAI-moved');
        root.setAttribute('data-mindustry-produced-unit-behavior-weapon','owned-projectile-hit-hostile-building');
        root.setAttribute('data-mindustry-produced-unit-behavior-frames',String(frames));
        root.setAttribute('data-mindustry-produced-unit-behavior-attack-frames',String(attackFrames));
        root.setAttribute('data-mindustry-produced-unit-behavior-displacement',String(
            Math.hypot(afterX-beforeX,afterY-beforeY)));
        """)
    private static native void markPassed(String planet,String type,int frames,int attackFrames,
        float beforeX,float beforeY,float afterX,float afterY);
}
