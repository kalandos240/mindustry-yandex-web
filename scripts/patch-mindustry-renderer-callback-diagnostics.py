#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RENDERER = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "core" / "Renderer.java"

text = RENDERER.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str):
    global text
    if old not in text:
        raise SystemExit(f"Renderer callback diagnostics no longer match patched source ({label})")
    text = text.replace(old, new, 1)


def traced_draw(old: str, layer: str, call: str, phase: str):
    replace_once(
        old,
        f'''        Draw.draw({layer}, () -> {{\n            webPhase("{phase}");\n            {call}\n            webPhase("{phase}-ready");\n        }});\n''',
        phase,
    )


traced_draw('        Draw.draw(Layer.background, this::drawBackground);\n', 'Layer.background', 'drawBackground();', 'renderer-callback-background')
traced_draw('        Draw.draw(Layer.floor, blocks.floor::drawFloor);\n', 'Layer.floor', 'blocks.floor.drawFloor();', 'renderer-callback-floor')
traced_draw('        Draw.draw(Layer.block - 1, blocks::drawShadows);\n', 'Layer.block - 1', 'blocks.drawShadows();', 'renderer-callback-shadows')

replace_once(
    '''        Draw.draw(Layer.block - 0.09f, () -> {\n            blocks.floor.beginDraw();\n            blocks.floor.drawLayer(CacheLayer.walls);\n        });\n''',
    '''        Draw.draw(Layer.block - 0.09f, () -> {\n            webPhase("renderer-callback-walls");\n            blocks.floor.beginDraw();\n            blocks.floor.drawLayer(CacheLayer.walls);\n            webPhase("renderer-callback-walls-ready");\n        });\n''',
    'walls callback',
)

replace_once(
    '        Draw.drawRange(Layer.blockBuilding, () -> Draw.shader(Shaders.blockbuild, true), Draw::shader);\n',
    '''        Draw.drawRange(Layer.blockBuilding, () -> {\n            webPhase("renderer-callback-blockbuild-begin");\n            Draw.shader(Shaders.blockbuild, true);\n            webPhase("renderer-callback-blockbuild-begin-ready");\n        }, () -> {\n            webPhase("renderer-callback-blockbuild-end");\n            Draw.shader();\n            webPhase("renderer-callback-blockbuild-end-ready");\n        });\n''',
    'blockbuild range',
)

traced_draw('            Draw.draw(Layer.light, lights::draw);\n', 'Layer.light', 'lights.draw();', 'renderer-callback-light')
traced_draw('            Draw.draw(Layer.darkness, blocks::drawDarkness);\n', 'Layer.darkness', 'blocks.drawDarkness();', 'renderer-callback-darkness')
traced_draw('            Draw.draw(Layer.bullet - 0.02f, bloom::capture);\n', 'Layer.bullet - 0.02f', 'bloom.capture();', 'renderer-callback-bloom-capture')
traced_draw('            Draw.draw(Layer.effect + 0.02f, bloom::render);\n', 'Layer.effect + 0.02f', 'bloom.render();', 'renderer-callback-bloom-render')
traced_draw('        Draw.draw(Layer.plans, overlays::drawBottom);\n', 'Layer.plans', 'overlays.drawBottom();', 'renderer-callback-overlay-bottom')

replace_once(
    '''            Draw.drawRange(Layer.shields, 1f, () -> effectBuffer.begin(Color.clear), () -> {\n                effectBuffer.end();\n                effectBuffer.blit(Shaders.shield);\n            });\n''',
    '''            Draw.drawRange(Layer.shields, 1f, () -> {\n                webPhase("renderer-callback-shields-begin");\n                effectBuffer.begin(Color.clear);\n                webPhase("renderer-callback-shields-begin-ready");\n            }, () -> {\n                webPhase("renderer-callback-shields-end");\n                effectBuffer.end();\n                effectBuffer.blit(Shaders.shield);\n                webPhase("renderer-callback-shields-end-ready");\n            });\n''',
    'shield range',
)

replace_once(
    '''            Draw.drawRange(Layer.buildBeam, 1f, () -> effectBuffer.begin(Color.clear), () -> {\n                effectBuffer.end();\n                effectBuffer.blit(Shaders.buildBeam);\n            });\n''',
    '''            Draw.drawRange(Layer.buildBeam, 1f, () -> {\n                webPhase("renderer-callback-buildbeam-begin");\n                effectBuffer.begin(Color.clear);\n                webPhase("renderer-callback-buildbeam-begin-ready");\n            }, () -> {\n                webPhase("renderer-callback-buildbeam-end");\n                effectBuffer.end();\n                effectBuffer.blit(Shaders.buildBeam);\n                webPhase("renderer-callback-buildbeam-end-ready");\n            });\n''',
    'build beam range',
)

traced_draw('        Draw.draw(Layer.overlayUI, overlays::drawTop);\n', 'Layer.overlayUI', 'overlays.drawTop();', 'renderer-callback-overlay-top')
traced_draw('        if(state.rules.fog) Draw.draw(Layer.fogOfWar, fog::drawFog);\n', 'Layer.fogOfWar', 'fog.drawFog();', 'renderer-callback-fog')

replace_once(
    '''        Draw.draw(Layer.space, () -> {\n            if(launchAnimator == null || landTime <= 0f) return;\n            launchAnimator.drawLaunch();\n        });\n''',
    '''        Draw.draw(Layer.space, () -> {\n            webPhase("renderer-callback-space");\n            if(launchAnimator != null && landTime > 0f) launchAnimator.drawLaunch();\n            webPhase("renderer-callback-space-ready");\n        });\n''',
    'space callback',
)

RENDERER.write_text(text, encoding="utf-8")
print("Instrumented deferred Renderer callbacks for first real Web playing frame")
