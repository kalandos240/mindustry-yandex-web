#!/usr/bin/env python3
"""Instrument the archived build ONLY, retaining the JS stack of the first Java throw."""
from pathlib import Path
import sys

path = Path("web-runtime/build/web/mindustry.js")
source = path.read_text()
anchor = "$rt_throw = ex => {\n    throw $rt_exception(ex);"
assert source.count(anchor) == 1, "Pinned TeaVM exception boundary changed"
source = source.replace(anchor, """$rt_throw = ex => {
    if((globalThis.__mindustryThrowCount = (globalThis.__mindustryThrowCount || 0) + 1) <= 30){
        console.error('TEAVM_THROW_STACK', new Error().stack);
    }
    throw $rt_exception(ex);""")
path.write_text(source)

if "--bind-preset-maps" in sys.argv:
    # Diagnostic A/B replay of the proven root cause on this EXACT archived
    # TeaVM build. Production fixes live in preparePresetGenerator/findLauncher.
    anchor = "mmg_PlanetGenerator_findLaunchCandidate = ($this, $destination, $launchSector) => {"
    assert source.count(anchor) == 1, "Pinned generator function signature changed"
    source = source.replace(anchor, anchor + """
    const preset = $destination.$preset0;
    if(preset && (!preset.$generator0 || !preset.$generator0.$map5)){
        preset.$generator0 = mmg_FileMapGenerator__init_0(preset.$name3, preset);
        if($rt_suspending()) throw new Error('Diagnostic map binding unexpectedly suspended');
        console.info('DEBUG_PRESET_BOUND', $rt_ustr(preset.$name3));
    }
""")
    path.write_text(source)
    html = path.with_name("index.html")
    content = html.read_text()
    anchor = "const forceCampaignEager = params.get("
    assert content.count(anchor) == 1
    html.write_text(content.replace(anchor, "const forceCampaignEager = true || params.get("))
