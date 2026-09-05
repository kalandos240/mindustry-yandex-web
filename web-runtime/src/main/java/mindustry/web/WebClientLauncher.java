package mindustry.web;

import arc.*;
import arc.assets.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import mindustry.*;
import mindustry.core.*;
import mindustry.net.Net.*;

import static mindustry.Vars.*;

/**
 * Browser-specific Mindustry client startup.
 *
 * The desktop ClientLauncher setup eagerly wires networking, native FreeType,
 * mod scripting, editor tooling and JVM worker pools. Those systems are added
 * back to the Web target only after a browser-native implementation exists.
 */
public final class WebClientLauncher extends ClientLauncher{
    private final NetProvider netProvider = new WebNetProvider();

    @Override
    public void setup(){
        platform = this;
        maxTextureSize = Gl.getInt(Gl.maxTextureSize);

        Time.setDeltaProvider(() -> {
            float result = Core.graphics.getDeltaTime() * 60f;
            return (Float.isNaN(result) || Float.isInfinite(result))
                ? 1f
                : Mathf.clamp(result, 0.0001f, maxDeltaClient);
        });

        // Browser client foundation: real Arc rendering and asset machinery,
        // without desktop-only platform services.
        Core.batch = new SpriteBatch();
        Core.assets = new AssetManager();
        tree = new FileTree();

        // Vars.init() creates GameState before gameplay systems begin consuming
        // campaign/sector state. The Web bootstrap intentionally does not execute
        // all of Vars.init() yet, so preserve that invariant explicitly. Serpulo's
        // emissive planet mesh queries Sector.isCaptured() during Content.load(),
        // which in turn requires state to exist even while the client is at menu.
        state = new GameState();

        // Use the exact upstream vanilla registration sequence now that TeaVM keeps
        // the narrow reflection metadata required by Block.initBuilding() and campaign
        // metadata JSON. Content.init() is deliberately executed before texture loading:
        // upstream defines it as the logical init/postInit phase for registered content.
        content = new ContentLoader();
        content.createBaseContent();
        content.init();
    }

    @Override
    public NetProvider getNet(){
        return netProvider;
    }
}
