#!/usr/bin/env bash
set -euo pipefail
MINDUSTRY_RESEARCH_TARGETS=desktop-menu exec bash "$(dirname "$0")/verify-browser-native-research.sh"
