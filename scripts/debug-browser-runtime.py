#!/usr/bin/env python3
"""Instrument the archived build ONLY, retaining the JS stack of the first Java throw."""
from pathlib import Path

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
