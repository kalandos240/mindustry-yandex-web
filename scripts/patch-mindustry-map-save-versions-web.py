#!/usr/bin/env python3
from pathlib import Path
import re
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
SAVEIO = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "io" / "SaveIO.java"
ASSETS = ROOT / "work" / "Mindustry" / "core" / "assets"
BUILD = ROOT / "web-runtime" / "build.gradle"

for path in (SAVEIO, BUILD):
    if not path.is_file():
        raise SystemExit(f"Missing pinned source: {path}")

# Keep only legacy readers required by files that are actually copied into the Web
# package. Campaign maps are not all the same historical MSAV version (Frozen Forest
# is v11 on the pinned upstream), so a hard-coded v4-v10 list is both brittle and can
# retain unnecessary TeaVM reachability.
includes = re.findall(r'include\s+"(maps/[^"]+)"', BUILD.read_text(encoding="utf-8"))
map_files = set()
for pattern in includes:
    if pattern.endswith("/**"):
        directory = ASSETS / pattern[:-3]
        if not directory.is_dir():
            raise SystemExit(f"Packaged map directory is missing from pinned assets: {pattern}")
        map_files.update(directory.rglob("*.msav"))
    elif "*" in pattern:
        map_files.update(p for p in ASSETS.glob(pattern) if p.is_file() and p.suffix == ".msav")
    else:
        path = ASSETS / pattern
        if not path.is_file():
            raise SystemExit(f"Packaged map include is missing from pinned assets: {pattern}")
        map_files.add(path)

if not map_files:
    raise SystemExit("No packaged MSAV maps found while building Web save-version registry")

versions = set()
by_version = {}
for path in sorted(map_files):
    try:
        raw = zlib.decompress(path.read_bytes())
    except zlib.error as exc:
        raise SystemExit(f"Cannot inflate packaged MSAV map {path}: {exc}")
    if len(raw) < 8 or raw[:4] != b"MSAV":
        raise SystemExit(f"Packaged map has invalid MSAV header: {path}")
    version = struct.unpack(">i", raw[4:8])[0]
    if version < 1 or version > 13:
        raise SystemExit(f"Unsupported packaged MSAV version {version}: {path}")
    versions.add(version)
    by_version.setdefault(version, []).append(path.relative_to(ASSETS).as_posix())

# v13 must always remain last: SaveIO.getVersion()/getSaveWriter() uses versionArray.peek().
versions.add(13)
ordered = sorted(versions)
registry = ", ".join(f"new Save{version}()" for version in ordered)

text = SAVEIO.read_text(encoding="utf-8")
old = 'public static final Seq<SaveVersion> versionArray = Seq.with(new Save4(), new Save13()); // Web: pinned built-in v4 maps + current v13 saves.'
new = f'public static final Seq<SaveVersion> versionArray = Seq.with({registry}); // Web: readers required by packaged maps + current v13 writer.'

if text.count(old) != 1:
    raise SystemExit("Packaged map version overlay expected exactly one Save4+Save13 Web version registry")

SAVEIO.write_text(text.replace(old, new, 1), encoding="utf-8")

# Save11's historical data-patch hook constructs DataManager/PatchAsset and pulls Arc's
# executor graph into TeaVM. Packaged Yandex maps contain no external data-patch assets;
# preserve the v11 wire position while failing closed if that assumption ever changes.
if 11 in versions:
    save11 = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "io" / "versions" / "Save11.java"
    if not save11.is_file():
        raise SystemExit(f"Required packaged-map reader is missing: {save11}")
    save11_text = save11.read_text(encoding="utf-8")
    old_v11 = '''    //old, simplified string-only data patches
    @Override
    public void readDataPatches(DataInput stream, SaveReadState saveState) throws IOException{
        Seq<DataAsset> assets = new Seq<>();

        int amount = stream.readUnsignedByte();
        for(int i = 0; i < amount; i++){
            int len = stream.readInt();
            byte[] bytes = new byte[len];
            stream.readFully(bytes);
            assets.add(new PatchAsset(new String(bytes, Strings.utf8)));
        }

        Events.fire(new DataPatchLoadEvent(assets));

        state.data.load(assets);
    }
'''
    new_v11 = '''    // Web/Yandex packaged v11 maps have no external data-patch assets. Keep the
    // historical one-byte count in the wire format without retaining DataManager's
    // desktop executor/asset patch graph.
    @Override
    public void readDataPatches(DataInput stream, SaveReadState saveState) throws IOException{
        int amount = stream.readUnsignedByte();
        if(amount != 0){
            throw new IOException("Mindustry Web cannot load v11 maps containing data patch assets: " + amount);
        }
    }
'''
    if save11_text.count(old_v11) != 1:
        raise SystemExit("Save11 Web data-patch overlay no longer matches pinned upstream")
    save11.write_text(save11_text.replace(old_v11, new_v11, 1), encoding="utf-8")

summary = ", ".join(f"v{version}={len(by_version.get(version, []))}" for version in ordered if version != 13 or version in by_version)
print(f"Detected packaged MSAV versions: {summary}")
print(f"Web SaveIO registry: {', '.join('v' + str(v) for v in ordered)} (v13 writer)")
