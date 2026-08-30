package mindustry.web;

import org.teavm.extension.Autoregistered;
import org.teavm.extension.spi.reflection.SimpleReflectionPolicy;

/**
 * Keeps only the reflection metadata required by vanilla Mindustry startup.
 *
 * Block.initBuilding() walks declared nested classes and invokes the public constructor
 * of the first Building subtype it finds. Arc Json also constructs Planet.PlanetData and
 * assigns its public fields while loading the packaged Serpulo campaign metadata.
 * TeaVM strips this metadata by default, so retain only these narrow surfaces instead of
 * enabling arbitrary application-wide reflection.
 */
@Autoregistered
public final class MindustryReflectionPolicy extends SimpleReflectionPolicy{
    @Override
    protected void setup(){
        selectPackage("mindustry.world.blocks", true)
            .reflectablePublicMembers();

        selectClass("mindustry.type.Planet$PlanetData")
            .reflectablePublicMembers();
    }
}
