package mindustry.web;

import arc.*;
import arc.assets.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import mindustry.*;
import mindustry.core.*;
import mindustry.net.*;
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

        // Match the first platform-independent part of upstream ClientLauncher.
        // UI color aliases are consumed by Scene styles later, and are safe before
        // fonts/UI widgets exist because they only register Arc Color values.
        UI.loadColors();
        if(Colors.get("accent") == null || Colors.get("highlight") == null){
            throw new IllegalStateException("Mindustry UI color aliases failed browser initialization");
        }

        // Browser client foundation: real Arc rendering and asset machinery,
        // without desktop-only platform services.
        Core.batch = new SpriteBatch();
        Core.assets = new AssetManager();
        tree = new FileTree();

        // Preserve the upstream networking boundary without pulling ArcNet/NIO into
        // TeaVM. Net's packet registry and common state are real Mindustry code;
        // WebNetProvider owns the browser transport edge and will later gain WebSocket.
        Vars.net = new Net(platform.getNet());
        if(Vars.net == null || platform.getNet() != netProvider){
            throw new IllegalStateException("Mindustry browser NetProvider boundary failed initialization");
        }

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
