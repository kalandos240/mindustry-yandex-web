#!/usr/bin/env python3
"""Audit an exported browser HAR without treating portal ad/SDK traffic as game code.

An archive load is first-party when its host is the deployed game origin. The
portal may independently request SDK, analytics and third-party ad resources;
only a saved HAR with request initiator metadata can attribute those to a frame.
This report never prints request queries, cookies, tokens, response bodies or
headers.
"""
from __future__ import annotations

from collections import Counter
import json
from pathlib import Path
import sys
from urllib.parse import urlsplit

YANDEX_SUFFIXES = (
    "yandex.ru", "yandex.net", "yandex.com", "yastatic.net",
    "games.yandex.ru", "ya.ru", "yandex.st", "yastat.net",
    "adfox.ru", "yandexmetrica.com", "ymetrica.com",
)


def trusted(host: str) -> bool:
    hostname = host.lower().rstrip(".")
    return any(hostname == base or hostname.endswith("." + base) for base in YANDEX_SUFFIXES)


def audit(data: dict) -> tuple[dict, int]:
    entries = data.get("log", {}).get("entries", [])
    if not isinstance(entries, list):
        raise ValueError("HAR log.entries must be an array")
    hosts: Counter[str] = Counter()
    unexpected: Counter[str] = Counter()
    game_status: Counter[int] = Counter()
    game_responses = 0
    for entry in entries:
        url = entry.get("request", {}).get("url", "")
        parts = urlsplit(url)
        if parts.scheme not in ("http", "https", "ws", "wss"):
            continue
        host = (parts.hostname or "").lower()
        if not host:
            continue
        hosts[host] += 1
        if not trusted(host):
            unexpected[host] += 1
        if host.endswith(".games.s3.yandex.net"):
            game_responses += 1
            game_status[int(entry.get("response", {}).get("status", 0))] += 1
    result = {
        "requests": sum(hosts.values()),
        "hosts": dict(hosts.most_common()),
        "unexpected_hosts": dict(unexpected.most_common()),
        "game_origin_requests": game_responses,
        "game_origin_http_statuses": dict(sorted(game_status.items())),
    }
    errors = sum(n for code, n in game_status.items() if code >= 400 or code == 0)
    return result, len(unexpected) + errors


def main() -> int:
    if len(sys.argv) != 2:
        raise SystemExit("usage: python3 scripts/audit-browser-har.py <Firefox-or-Chrome.har>")
    data = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8-sig"))
    result, failures = audit(data)
    print(json.dumps(result, indent=2, ensure_ascii=False))
    if failures:
        print("HAR warning: unexpected request host(s) or failed game asset request(s)", file=sys.stderr)
    return int(failures > 0)


if __name__ == "__main__":
    raise SystemExit(main())
