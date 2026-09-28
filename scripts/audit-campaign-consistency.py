#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
BUILD = (ROOT / "web-runtime" / "build.gradle").read_text(encoding="utf-8")
AUDIT = (ROOT / "scripts" / "audit-yandex-release.sh").read_text(encoding="utf-8")
SERPULO_TEST = (ROOT / "scripts" / "verify-browser-campaign-capture.sh").read_text(encoding="utf-8")
EREKIR_TEST = (ROOT / "scripts" / "verify-browser-erekir-progression.sh").read_text(encoding="utf-8")
RESEARCH = (ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserCampaignResearch.java").read_text(encoding="utf-8")
RUNTIME = (ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserCampaignRuntime.java").read_text(encoding="utf-8")
UI_PATCH = (ROOT / "scripts" / "patch-browser-campaign-ui.py").read_text(encoding="utf-8")

SERPULO = [
    "groundZero", "frozenForest", "crateredBattleground", "ruinousShores",
    "windsweptIslands", "biomassFacility", "fungalPass", "frontier", "saltFlats",
    "tarFields", "impact0078", "stainedMountains", "infestedCanyons",
    "nuclearComplex", "desolateRift", "facility32m", "perilousHarbor",
    "extractionOutpost", "coastline", "navalFortress", "overgrowth",
    "mycelialBastion", "littoralShipyard", "planetaryTerminal", "taintedWoods",
    "atolls", "testingGrounds", "sunkenPier", "weatheredChannels",
]

EREKIR = [
    ("onset", "onset"), ("aegis", "aegis"), ("lake", "lake"),
    ("intersect", "intersect"), ("atlas", "atlas"), ("split", "split"),
    ("basin", "basin"), ("marsh", "marsh"), ("peaks", "peaks"),
    ("ravine", "ravine"), ("caldera", "caldera-erekir"),
    ("stronghold", "stronghold"), ("crevice", "crevice"), ("siege", "siege"),
    ("crossroads", "crossroads"), ("karst", "karst"), ("origin", "origin"),
]

LAUNCH_ORIGINS = {
    "frozenForest": "groundZero",
    "crateredBattleground": "frozenForest",
    "ruinousShores": "crateredBattleground",
    "windsweptIslands": "ruinousShores",
    "biomassFacility": "windsweptIslands",
    "fungalPass": "biomassFacility",
    "frontier": "fungalPass",
    "saltFlats": "frontier",
    "tarFields": "saltFlats",
    "impact0078": "tarFields",
    "stainedMountains": "biomassFacility",
    "infestedCanyons": "stainedMountains",
    "nuclearComplex": "infestedCanyons",
    "desolateRift": "nuclearComplex",
    "facility32m": "stainedMountains",
    "perilousHarbor": "frontier",
    "extractionOutpost": "perilousHarbor",
    "coastline": "extractionOutpost",
    "navalFortress": "coastline",
    "overgrowth": "frontier",
    "mycelialBastion": "overgrowth",
    "littoralShipyard": "mycelialBastion",
    "planetaryTerminal": "littoralShipyard",
    "taintedWoods": "infestedCanyons",
    "atolls": "extractionOutpost",
    "testingGrounds": "coastline",
    "sunkenPier": "navalFortress",
    "weatheredChannels": "navalFortress",
    "aegis": "onset",
    "lake": "aegis",
    "intersect": "lake",
    "atlas": "intersect",
    "split": "atlas",
    "basin": "atlas",
    "marsh": "basin",
    "peaks": "marsh",
    "ravine": "marsh",
    "caldera": "ravine",
    "stronghold": "caldera",
    "crevice": "stronghold",
    "siege": "crevice",
    "crossroads": "siege",
    "karst": "crossroads",
    "origin": "karst",
}

failures = []

def require(haystack: str, needle: str, where: str) -> None:
    if needle not in haystack:
        failures.append(f"{where}: missing {needle}")

def build_maps(planet: str) -> set[str]:
    return set(re.findall(rf'include "maps/{planet}/([^"]+)\.msav"', BUILD))

expected_serpulo = set(SERPULO)
expected_erekir = {file_name for _, file_name in EREKIR}
actual_serpulo = build_maps("serpulo")
actual_erekir = build_maps("erekir")

if actual_serpulo != expected_serpulo:
    failures.append(
        "build.gradle Serpulo map set mismatch: "
        f"missing={sorted(expected_serpulo - actual_serpulo)} "
        f"extra={sorted(actual_serpulo - expected_serpulo)}"
    )
if actual_erekir != expected_erekir:
    failures.append(
        "build.gradle Erekir map set mismatch: "
        f"missing={sorted(expected_erekir - actual_erekir)} "
        f"extra={sorted(actual_erekir - expected_erekir)}"
    )

def kebab(name: str) -> str:
    return re.sub(r"(?<!^)(?=[A-Z])", "-", name).lower()

def smoke_tokens(name: str) -> set[str]:
    base = kebab(name)
    numeric = re.sub(r"(?<=[A-Za-z])(?=[0-9])", "-", base)
    return {name, base, numeric}

for field in SERPULO:
    path = f"maps/serpulo/{field}.msav"
    require(AUDIT, field, "Yandex audit Serpulo")
    require(RESEARCH, f"SectorPresets.{field}", "campaign research Serpulo")
    require(RUNTIME, f"SectorPresets.{field}", "campaign runtime Serpulo")
    tokens = smoke_tokens(field)
    if not any(token in SERPULO_TEST for token in tokens):
        failures.append(
            f"campaign smoke Serpulo: missing one of {sorted(tokens)}"
        )
    require(BUILD, path, "build.gradle Serpulo")

for field, file_name in EREKIR:
    path = f"maps/erekir/{file_name}.msav"
    require(AUDIT, file_name, "Yandex audit Erekir")
    require(RESEARCH, f"SectorPresets.{field}", "campaign research Erekir")
    require(RUNTIME, f"SectorPresets.{field}", "campaign runtime Erekir")
    require(EREKIR_TEST, file_name if field == "caldera" else field, "campaign smoke Erekir")
    require(BUILD, path, "build.gradle Erekir")


def declared_static_methods(source: str) -> set[str]:
    return set(re.findall(r"public static [^{;\n]+?\s+(\w+)\s*\(", source))

def referenced_methods(source: str, owner: str) -> set[str]:
    return set(re.findall(rf"{re.escape(owner)}\.(\w+)\s*\(", source))

research_declared = declared_static_methods(RESEARCH)
research_referenced = (
    referenced_methods(RUNTIME, "BrowserCampaignResearch")
    | referenced_methods(UI_PATCH, "BrowserCampaignResearch")
)
runtime_declared = declared_static_methods(RUNTIME)
runtime_referenced = referenced_methods(UI_PATCH, "BrowserCampaignRuntime")

for method in sorted(research_referenced - research_declared):
    failures.append(f"campaign Java surface: missing BrowserCampaignResearch.{method} declaration")
for method in sorted(runtime_referenced - runtime_declared):
    failures.append(f"campaign Java surface: missing BrowserCampaignRuntime.{method} declaration")

ready_aliases = set(re.findall(r"public static boolean ([A-Za-z0-9]+Ready)\(\)", RESEARCH))
if ready_aliases:
    failures.append(f"campaign ready-query dedup regression: {sorted(ready_aliases)}")
require(RESEARCH, "public static boolean ready(SectorPreset preset)", "generic campaign ready query")
require(UI_PATCH, "BrowserCampaignResearch.ready(", "campaign UI generic ready query")

captured_aliases = set(re.findall(r"public static boolean ([A-Za-z0-9]+Captured)\(\)", RESEARCH))
expected_captured_aliases = {"groundZeroCaptured", "onsetCaptured"}
if captured_aliases != expected_captured_aliases:
    failures.append(
        "campaign capture-query dedup regression: "
        f"expected={sorted(expected_captured_aliases)} actual={sorted(captured_aliases)}"
    )
require(RESEARCH, "public static boolean isCaptured(SectorPreset preset)", "generic campaign capture query")
require(UI_PATCH, "BrowserCampaignResearch.isCaptured(", "campaign UI generic capture query")

spend_next_aliases = set(re.findall(r"public static void (spendNext[A-Za-z0-9]+)\(\)", RESEARCH))
expected_spend_next_aliases = {
    "spendNextCraterResearch",
    "spendNextImpactResearch",
    "spendNextNuclearResearch",
    "spendNextDesolateResearch",
}
if spend_next_aliases != expected_spend_next_aliases:
    failures.append(
        "campaign spend-next dedup regression: "
        f"expected={sorted(expected_spend_next_aliases)} actual={sorted(spend_next_aliases)}"
    )
require(RESEARCH, "public static void spendNext(UnlockableContent next)", "generic campaign spend-next")
require(UI_PATCH, "BrowserCampaignResearch.spendNext(", "campaign UI generic spend-next")

save_aliases = set(re.findall(r"public static boolean (has[A-Za-z0-9]+Save)\(\)", RUNTIME))
expected_save_aliases = {"hasGroundZeroSave", "hasOnsetSave"}
if save_aliases != expected_save_aliases:
    failures.append(
        "campaign save-query dedup regression: "
        f"expected={sorted(expected_save_aliases)} actual={sorted(save_aliases)}"
    )
require(RUNTIME, "public static boolean hasSave(SectorPreset preset)", "generic campaign save query")
require(UI_PATCH, "BrowserCampaignRuntime.hasSave(", "campaign UI generic save query")

for preset, origin in LAUNCH_ORIGINS.items():
    action = "caldera" if preset == "caldera" else preset
    require(
        RUNTIME,
        f'playUnlockedPreset(SectorPresets.{preset}, SectorPresets.{origin}, "{action}")',
        f"campaign launch mapping {preset}",
    )

if len(LAUNCH_ORIGINS) != 44:
    failures.append(f"campaign launch mapping count changed: {len(LAUNCH_ORIGINS)} != 44")

# The pinned Origin finale is special: it must stay a five-stage timer graph followed
# by the stock attack victory predicate. These markers make accidental regression
# to a generic/forced capture fail before the expensive TeaVM compile.
for needle in [
    "stageOriginObjectives()",
    'String[] flags = {"u1", "u2", "u3", "u4", "u5"}',
    "float[] durations = {36000f, 72000f, 108000f, 108000f, 72000f}",
    "state.rules.objectiveTimerMultiplier = 0f",
    'armObjectiveAttackCapture("origin")',
    "data-mindustry-erekir-origin-objective-count",
    "data-mindustry-erekir-origin-captured",
]:
    require(RUNTIME, needle, "Origin runtime contract")

for needle in [
    'data-mindustry-erekir-origin-objectives="staged"',
    'data-mindustry-erekir-origin-objective-count="5"',
    'data-mindustry-erekir-origin-objective-flags="ready"',
    'data-mindustry-erekir-origin-captured="true"',
    'data-mindustry-campaign-captured-preset="origin"',
    'data-mindustry-campaign-capture-win-wave="0"',
]:
    require(EREKIR_TEST, needle, "Origin browser smoke contract")

require(
    UI_PATCH,
    "BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.origin)",
    "Origin UI completion state",
)
require(UI_PATCH, 'Core.bundle.get("planet.erekir.name", "Erekir")', "Origin UI completion label")

if failures:
    print("Campaign release consistency audit: FAIL")
    for failure in failures:
        print(f" - {failure}")
    raise SystemExit(1)

print(
    "Campaign release consistency audit: PASS "
    f"({len(SERPULO)} Serpulo + {len(EREKIR)} Erekir sector maps)"
)
