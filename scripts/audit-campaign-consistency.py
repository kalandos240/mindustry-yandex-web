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

if failures:
    print("Campaign release consistency audit: FAIL")
    for failure in failures:
        print(f" - {failure}")
    raise SystemExit(1)

print(
    "Campaign release consistency audit: PASS "
    f"({len(SERPULO)} Serpulo + {len(EREKIR)} Erekir sector maps)"
)
