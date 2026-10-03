#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
INDEX="$WEB_DIR/index.html"
PLATFORM="$WEB_DIR/yandex-platform.js"
MANIFEST="$WEB_DIR/assets-manifest.js"
PLANETS_SOURCE="$ROOT_DIR/work/Mindustry/core/src/mindustry/content/Planets.java"
SERPULO_DATA="$WEB_DIR/assets/planets/serpulo.json"
MAX_BYTES=$((100 * 1024 * 1024))

fail(){
  echo "Yandex release audit failed: $*" >&2
  exit 1
}

[ -d "$WEB_DIR" ] || fail "staged Web directory is missing"
[ -s "$INDEX" ] || fail "index.html is not in archive root"
[ -s "$PLATFORM" ] || fail "yandex-platform.js is missing"
[ -s "$WEB_DIR/mindustry.js" ] || fail "mindustry.js is missing"
[ -s "$MANIFEST" ] || fail "assets-manifest.js is missing"
if find "$WEB_DIR" -type f -name '*.map' -print -quit | grep -q .; then
  fail "source map file leaked into production package"
fi
[ -s "$WEB_DIR/assets/logicids.dat" ] || fail "processor logic ID mapping is missing"
[ -s "$WEB_DIR/licenses/Mindustry-GPL-3.0.txt" ] || fail "Mindustry GPL-3.0 license text missing"
[ -s "$WEB_DIR/licenses/Arc-Apache-2.0.txt" ] || fail "Arc Apache-2.0 license text missing"
[ -s "$WEB_DIR/licenses/SOURCE-NOTICE.txt" ] || fail "source notice missing"
[ -s "$WEB_DIR/licenses/upstream.lock" ] || fail "upstream lockfile missing from release"
grep -Fq 'c9686eb5d0ae5dd47ee02c40f99f7d5018ccbc8c' "$WEB_DIR/licenses/SOURCE-NOTICE.txt" || fail "Mindustry source pin missing from notice"
grep -Fq 'c38f8f5ff27f47a5886d0903aadeba42e4302411' "$WEB_DIR/licenses/SOURCE-NOTICE.txt" || fail "Arc source pin missing from notice"

# Vanilla planet definitions are Java content compiled into mindustry.js. Serpulo also
# sets loadPlanetData=true, and tools:pack generates planets/serpulo.json at build time;
# Planet.getData() consumes that same-origin file for attack-sector/preset metadata.
# Erekir does not use loadPlanetData, so no Erekir JSON asset is required or staged.
[ -s "$PLANETS_SOURCE" ] || fail "pinned Mindustry Planets.java missing from build workspace"
grep -Fq 'erekir = new Planet("erekir", sun, 1f, 2)' "$PLANETS_SOURCE" || fail "pinned Erekir planet definition changed or missing"
grep -Fq 'serpulo = new Planet("serpulo", sun, 1f, 3)' "$PLANETS_SOURCE" || fail "pinned Serpulo planet definition changed or missing"
sed -n '/serpulo = new Planet("serpulo", sun, 1f, 3){{/,/verilus = makeAsteroid/p' "$PLANETS_SOURCE" | grep -Fq 'loadPlanetData = true;' || fail "pinned Serpulo PlanetData loading flag missing"
grep -Fq 'campaignRuleDefaults.rtsAI = true' "$PLANETS_SOURCE" || fail "pinned Erekir campaign RTS rule missing"
grep -Fq 'erekir' "$WEB_DIR/mindustry.js" || fail "compiled Erekir content is not reachable in TeaVM output"
grep -Fq 'serpulo' "$WEB_DIR/mindustry.js" || fail "compiled Serpulo content is not reachable in TeaVM output"
[ -s "$SERPULO_DATA" ] || fail "generated Serpulo PlanetData asset missing"
grep -Fq 'attackSectors' "$SERPULO_DATA" || fail "generated Serpulo PlanetData lacks attack sectors"
grep -Fq 'groundZero' "$SERPULO_DATA" || fail "generated Serpulo PlanetData lacks Ground Zero mapping"
grep -Fq 'planets/serpulo.json' "$MANIFEST" || fail "generated Serpulo PlanetData missing from asset manifest"
[ ! -e "$WEB_DIR/assets/planets/erekir.json" ] || fail "unexpected Erekir PlanetData JSON was staged"

for preset in onset aegis lake intersect atlas split basin marsh peaks ravine caldera-erekir stronghold crevice siege crossroads karst origin; do
  [ -s "$WEB_DIR/assets/maps/erekir/$preset.msav" ] || fail "Erekir campaign map missing: $preset.msav"
done
for preset in onset aegis lake intersect atlas split basin marsh peaks ravine caldera-erekir stronghold crevice siege crossroads karst origin; do
  grep -Fq "maps/erekir/$preset.msav" "$MANIFEST" || fail "Erekir campaign map missing from asset manifest: $preset.msav"
done

# Original builtin local skirmish maps from the pinned release must be available inside
# the archive before the next user-controlled map-selection/start milestone is enabled.
[ -s "$WEB_DIR/assets/maps/default/maze.msav" ] || fail "builtin local map maze.msav missing"
[ -s "$WEB_DIR/assets/maps/default/archipelago.msav" ] || fail "builtin local map archipelago.msav missing"
[ -s "$WEB_DIR/assets/maps/serpulo/groundZero.msav" ] || fail "Ground Zero campaign map missing"
[ -s "$WEB_DIR/assets/maps/serpulo/frozenForest.msav" ] || fail "Frozen Forest campaign map missing"
[ -s "$WEB_DIR/assets/maps/serpulo/crateredBattleground.msav" ] || fail "Cratered Battleground campaign map missing"
[ -s "$WEB_DIR/assets/maps/serpulo/ruinousShores.msav" ] || fail "Ruinous Shores campaign map missing"
[ -s "$WEB_DIR/assets/maps/serpulo/windsweptIslands.msav" ] || fail "Windswept Islands campaign map missing"
for preset in biomassFacility fungalPass frontier saltFlats tarFields impact0078 stainedMountains infestedCanyons nuclearComplex desolateRift facility32m perilousHarbor extractionOutpost coastline navalFortress overgrowth mycelialBastion littoralShipyard planetaryTerminal taintedWoods atolls testingGrounds sunkenPier weatheredChannels; do
  [ -s "$WEB_DIR/assets/maps/serpulo/$preset.msav" ] || fail "Campaign branch map missing: $preset.msav"
done
grep -Fq 'maps/default/maze.msav' "$MANIFEST" || fail "builtin local map missing from asset manifest"
grep -Fq 'maps/serpulo/groundZero.msav' "$MANIFEST" || fail "Ground Zero missing from asset manifest"
grep -Fq 'maps/serpulo/frozenForest.msav' "$MANIFEST" || fail "Frozen Forest missing from asset manifest"
grep -Fq 'maps/serpulo/crateredBattleground.msav' "$MANIFEST" || fail "Cratered Battleground missing from asset manifest"
grep -Fq 'maps/serpulo/ruinousShores.msav' "$MANIFEST" || fail "Ruinous Shores missing from asset manifest"
grep -Fq 'maps/serpulo/windsweptIslands.msav' "$MANIFEST" || fail "Windswept Islands missing from asset manifest"
for preset in biomassFacility fungalPass frontier saltFlats tarFields impact0078 stainedMountains infestedCanyons nuclearComplex desolateRift facility32m perilousHarbor extractionOutpost coastline navalFortress overgrowth mycelialBastion littoralShipyard planetaryTerminal taintedWoods atolls testingGrounds sunkenPier weatheredChannels; do
  grep -Fq "maps/serpulo/$preset.msav" "$MANIFEST" || fail "Campaign branch map missing from asset manifest: $preset.msav"
done
map_count="$(find "$WEB_DIR/assets/maps/default" -maxdepth 1 -type f -name '*.msav' | wc -l)"
[ "$map_count" -gt 1 ] || fail "builtin local map set is unexpectedly incomplete"
echo "Builtin local maps staged: $map_count"

# Campaign milestone assets: all stock Serpulo TechTree presets reached by the current
# complete browser campaign implementation must stay in the same-origin release.
for preset in groundZero frozenForest crateredBattleground ruinousShores windsweptIslands biomassFacility fungalPass frontier saltFlats tarFields impact0078 stainedMountains infestedCanyons nuclearComplex desolateRift facility32m perilousHarbor extractionOutpost coastline navalFortress overgrowth mycelialBastion littoralShipyard planetaryTerminal taintedWoods atolls testingGrounds sunkenPier weatheredChannels; do
  [ -s "$WEB_DIR/assets/maps/serpulo/$preset.msav" ] || fail "campaign preset map missing: $preset.msav"
  grep -Fq "maps/serpulo/$preset.msav" "$MANIFEST" || fail "campaign preset missing from asset manifest: $preset.msav"
done

# Stock Renderer must remain completely local. Shaders.init(), Content.load(),
# Renderer.init(), PlanetRenderer/Bloom and EnvRenderers all execute in the browser
# gameplay path and may queue textures before the second AssetManager drain.
[ -s "$WEB_DIR/assets/shaders/default.vert" ] || fail "renderer shader default.vert missing"
[ -s "$WEB_DIR/assets/shaders/screenspace.vert" ] || fail "renderer shader screenspace.vert missing"
[ -s "$WEB_DIR/assets/shaders/blockbuild.frag" ] || fail "renderer shader blockbuild.frag missing"
[ -s "$WEB_DIR/assets/bloomshaders/screenspace.vert" ] || fail "renderer bloom screenspace shader missing"
[ -s "$WEB_DIR/assets/bloomshaders/bloom.frag" ] || fail "renderer bloom fragment shader missing"
[ -s "$WEB_DIR/assets/sprites/noise.png" ] || fail "renderer noise texture missing"
[ -s "$WEB_DIR/assets/sprites/noiseAlpha.png" ] || fail "weather noiseAlpha texture missing"
[ -s "$WEB_DIR/assets/sprites/fog.png" ] || fail "weather fog texture missing"
[ -s "$WEB_DIR/assets/sprites/caustics.png" ] || fail "renderer caustics texture missing"
[ -s "$WEB_DIR/assets/sprites/space.png" ] || fail "renderer space texture missing"
[ -s "$WEB_DIR/assets/sprites/clouds.png" ] || fail "renderer clouds texture missing"
[ -s "$WEB_DIR/assets/sprites/rays.png" ] || fail "renderer rays texture missing"
[ -s "$WEB_DIR/assets/sprites/distortAlpha.png" ] || fail "renderer distortAlpha texture missing"
for face in right left top bottom front back; do
  [ -s "$WEB_DIR/assets/cubemaps/stars/$face.png" ] || fail "renderer star cubemap face missing: $face.png"
done

bytes="$(du -sb "$WEB_DIR" | awk '{print $1}')"
[ "$bytes" -le "$MAX_BYTES" ] || fail "unpacked package is $bytes bytes; limit is $MAX_BYTES"
echo "Yandex unpacked bytes: $bytes / $MAX_BYTES"

# Requirement 1.22: no spaces or Cyrillic/non-ASCII characters in archive paths.
while IFS= read -r path; do
  rel="${path#./}"
  case "$rel" in
    *' '*) fail "space in archive path: $rel" ;;
  esac
  if LC_ALL=C printf '%s' "$rel" | grep -qP '[^\x00-\x7F]'; then
    fail "non-ASCII archive path: $rel"
  fi
done < <(cd "$WEB_DIR" && find . -mindepth 1 -print)

# SDK must be supplied by the Yandex host at the documented relative path; the
# archive must not ship a downloaded or stale SDK copy.
grep -Fq "script.src = '/sdk.js'" "$PLATFORM" || fail "relative /sdk.js loader missing"
[ ! -e "$WEB_DIR/sdk.js" ] || fail "sdk.js must not be bundled into the game archive"
if grep -REn --include='*.html' --include='*.js' 'sdk\.games\.s3\.yandex\.net|https?://|wss?://' "$WEB_DIR"; then
  fail "absolute/external URL literal remained in staged release"
fi

# Required SDK lifecycle and environment integration.
grep -Fq 'YaGames.init' "$PLATFORM" || fail "YaGames.init integration missing"
grep -Fq 'environment.i18n.lang' "$PLATFORM" || fail "SDK language auto-detection missing"
grep -Fq "ysdk.on('game_api_pause'" "$PLATFORM" || fail "game_api_pause subscription missing"
grep -Fq "ysdk.on('game_api_resume'" "$PLATFORM" || fail "game_api_resume subscription missing"
grep -Fq 'LoadingAPI' "$PLATFORM" || fail "LoadingAPI.ready integration missing"
grep -Fq 'GameplayAPI' "$PLATFORM" || fail "GameplayAPI integration missing"
grep -Fq 'showFullscreenAdv' "$PLATFORM" || fail "Yandex fullscreen advertisement integration missing"
grep -Fq 'getPlayer' "$PLATFORM" || fail "Yandex Player bridge missing"

# Mobile/browser UX requirements: full active area, no page scroll/swipe refresh,
# no long-tap selection/context menu, touch-first canvas.
grep -Fq 'width=device-width,initial-scale=1,viewport-fit=cover' "$INDEX" || fail "mobile viewport missing"
grep -Fq 'overflow: hidden' "$INDEX" || fail "browser scrolling is not disabled"
grep -Fq 'overscroll-behavior: none' "$INDEX" || fail "swipe-to-refresh guard missing"
grep -Fq 'touch-action: none' "$INDEX" || fail "touch-action guard missing"
grep -Fq 'user-select: none' "$INDEX" || fail "selection guard missing"
grep -Fq "addEventListener('contextmenu'" "$INDEX" || fail "context-menu guard missing"
grep -Fq "addEventListener('selectstart'" "$INDEX" || fail "selection event guard missing"
if grep -Eiq '<(audio|video)([[:space:]>])' "$INDEX"; then
  fail "system audio/video player element must not be exposed"
fi

# This release intentionally supports exactly the two audited languages.
[ -s "$WEB_DIR/assets/locales" ] || fail "locales file missing"
grep -qx 'en' "$WEB_DIR/assets/locales" || fail "English locale missing"
grep -qx 'ru' "$WEB_DIR/assets/locales" || fail "Russian locale missing"
[ "$(wc -l < "$WEB_DIR/assets/locales")" -eq 2 ] || fail "unexpected extra locales in Yandex release"

# Runtime itself must remain fully packaged; Yandex SDK is the platform boundary.
grep -Fq "data-mindustry-network', 'local-only'" "$INDEX" || fail "local-only game network guard missing"
grep -Fq 'data-mindustry-links' "$WEB_DIR/mindustry.js" || fail "compiled no-links marker missing"

echo 'Yandex release audit: PASS'
