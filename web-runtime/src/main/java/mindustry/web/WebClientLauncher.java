package mindustry.web;

import arc.*;
import arc.assets.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import mindustry.*;
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

        // First browser client slice: real Arc rendering and asset machinery,
        // without desktop-only platform services. Content/UI are enabled in
        // subsequent slices once their Web dependencies are available.
        batch = new SpriteBatch();
        assets = new AssetManager();
        tree = new FileTree();
    }

    @Override
    public NetProvider getNet(){
        return netProvider;
    }
}
