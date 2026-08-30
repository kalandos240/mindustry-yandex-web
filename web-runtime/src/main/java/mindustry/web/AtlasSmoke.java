package mindustry.web;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import mindustry.*;

/** Browser smoke for Mindustry's generated vanilla texture atlas. */
final class AtlasSmoke{
    static final String atlasPath = "sprites/sprites.aatls";

    private AtlasSmoke(){}

    static void loadAndVerify(){
        byte[] binary = Core.files.internal(atlasPath).readBytes();
        if(binary.length < 6
        || binary[0] != 'A'
        || binary[1] != 'A'
        || binary[2] != 'T'
        || binary[3] != 'L'
        || binary[4] != 'S'){
            throw new IllegalStateException("Mindustry vanilla atlas has an invalid AATLS header");
        }

        Core.assets.load(atlasPath, TextureAtlas.class);
        int updates = 0;
        while(!Core.assets.update()){
            if(++updates > 10000){
                throw new IllegalStateException("Mindustry vanilla atlas did not finish loading through Arc AssetManager");
            }
        }

        TextureAtlas loaded = Core.assets.get(atlasPath, TextureAtlas.class);
        if(loaded.getRegions().size < 100 || loaded.getTextures().size == 0){
            throw new IllegalStateException("Mindustry vanilla atlas loaded without its expected regions/pages");
        }

        for(Texture texture : loaded.getTextures()){
            if(texture.width <= 0 || texture.height <= 0 || texture.getTextureObjectHandle() == 0){
                throw new IllegalStateException("Mindustry vanilla atlas contains an invalid WebGL texture page");
            }
        }

        int error = Core.gl20.glGetError();
        if(error != GL20.GL_NO_ERROR){
            throw new IllegalStateException("Mindustry vanilla atlas WebGL upload failed with GL error 0x" + Integer.toHexString(error));
        }

        Vars.atlas = loaded;
    }
}
