package mindustry.web;

import org.teavm.extension.Autoregistered;
import org.teavm.extension.spi.reflection.SimpleReflectionPolicy;

/**
 * Keeps only the reflection metadata required by vanilla Mindustry startup.
 *
 * Block.initBuilding() walks declared nested classes and invokes the public constructor
 * of the first Building subtype it finds. Arc Json also constructs Planet.PlanetData and
 * its ObjectIntMap field while loading packaged Serpulo campaign metadata. Content loading
 * resolves BuildVisibility by its public static fields. JsonIO configures the Rules
 * collection element types by looking up the public spawns/loadout fields by name.
 * TeaVM strips this metadata by default, so retain only these narrow surfaces instead
 * of enabling arbitrary reflection.
 */
@Autoregistered
public final class MindustryReflectionPolicy extends SimpleReflectionPolicy{
    @Override
    protected void setup(){
        selectPackage("mindustry.world.blocks", true)
            .reflectablePublicMembers();

        selectClass("mindustry.type.Planet$PlanetData")
            .reflectablePublicMembers();

        selectClass("arc.struct.ObjectIntMap")
            .reflectablePublicMembers();

        selectClass("mindustry.world.meta.BuildVisibility")
            .reflectablePublicMembers();

        selectClass("mindustry.game.Rules")
            .reflectablePublicMembers();

        // Campaign map objectives are constructed explicitly by BrowserJsonCompatibility,
        // but Arc Json readFields/writeFields still needs reflective field tables. Retain
        // fields only for the pinned objective hierarchy; do not broaden reflection to
        // the whole mindustry.game package.
        selectClass("mindustry.game.MapObjectives$MapObjective").reflectableFields(field -> true);
        selectClass("mindustry.game.MapObjectives$ResearchObjective").reflectableFields(field -> true);
        selectClass("mindustry.game.MapObjectives$ProduceObjective").reflectableFields(field -> true);
        selectClass("mindustry.game.MapObjectives$ItemObjective").reflectableFields(field -> true);
        selectClass("mindustry.game.MapObjectives$CoreItemObjective").reflectableFields(field -> true);
        selectClass("mindustry.game.MapObjectives$BuildCountObjective").reflectableFields(field -> true);
        selectClass("mindustry.game.MapObjectives$UnitCountObjective").reflectableFields(field -> true);
        selectClass("mindustry.game.MapObjectives$DestroyUnitsObjective").reflectableFields(field -> true);
        selectClass("mindustry.game.MapObjectives$TimerObjective").reflectableFields(field -> true);
        selectClass("mindustry.game.MapObjectives$DestroyBlockObjective").reflectableFields(field -> true);
        selectClass("mindustry.game.MapObjectives$DestroyBlocksObjective").reflectableFields(field -> true);
        selectClass("mindustry.game.MapObjectives$CommandModeObjective").reflectableFields(field -> true);
        selectClass("mindustry.game.MapObjectives$FlagObjective").reflectableFields(field -> true);
        selectClass("mindustry.game.MapObjectives$DestroyCoreObjective").reflectableFields(field -> true);

        selectClass("mindustry.game.MapObjectives$ObjectiveMarker").reflectableFields(field -> true);
        selectClass("mindustry.game.MapObjectives$PosMarker").reflectableFields(field -> true);
        selectClass("mindustry.game.MapObjectives$ShapeTextMarker").reflectableFields(field -> true);
        selectClass("mindustry.game.MapObjectives$PointMarker").reflectableFields(field -> true);
        selectClass("mindustry.game.MapObjectives$ShapeMarker").reflectableFields(field -> true);
        selectClass("mindustry.game.MapObjectives$TextMarker").reflectableFields(field -> true);
        selectClass("mindustry.game.MapObjectives$LineMarker").reflectableFields(field -> true);
        selectClass("mindustry.game.MapObjectives$TextureMarker").reflectableFields(field -> true);
        selectClass("mindustry.game.MapObjectives$QuadMarker").reflectableFields(field -> true);
        selectClass("mindustry.game.MapObjectives$TextureHolder").reflectableFields(field -> true);

        selectClass("arc.math.geom.Vec2").reflectableFields(field -> true);
        selectClass("arc.math.geom.Point2").reflectableFields(field -> true);
    }
}
