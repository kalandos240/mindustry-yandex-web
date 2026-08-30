package mindustry.web;

import org.teavm.extension.Autoregistered;
import org.teavm.extension.spi.reflection.SimpleReflectionPolicy;

/**
 * Keeps only the reflection metadata Mindustry's vanilla Block factory discovery needs.
 *
 * Block.initBuilding() walks declared nested classes and invokes the public constructor
 * of the first Building subtype it finds. TeaVM intentionally strips that metadata by
 * default, which made every Web block silently fall back to Building::create. Restrict
 * reflection to the block package tree so specialized CoreBuild/TurretBuild/etc. remain
 * available without enabling arbitrary application-wide reflection.
 */
@Autoregistered
public final class MindustryReflectionPolicy extends SimpleReflectionPolicy{
    @Override
    protected void setup(){
        selectPackage("mindustry.world.blocks", true)
            .reflectablePublicMembers();
    }
}
