#!/usr/bin/env python3
"""Check actual bootstrap proofs without requiring debug prose on the game screen."""
from html.parser import HTMLParser
from pathlib import Path
import sys


class BootstrapDom(HTMLParser):
    def __init__(self):
        super().__init__()
        self.root = {}

    def handle_starttag(self, tag, attrs):
        if tag == "html":
            self.root = dict(attrs)


def verify(html):
    parser = BootstrapDom()
    parser.feed(html)
    attrs = parser.root
    required = {"binary-png", "texture-upload", "vanilla-atlas", "content-regions",
                "sprite-frame", "framebuffer-readback", "settings-roundtrip"}
    actual = set(attrs.get("data-mindustry-bootstrap-proof", "").split())
    if missing := required - actual:
        raise ValueError("Missing real bootstrap proofs: " + ", ".join(sorted(missing)))
    if attrs.get("data-mindustry-web") != "ready":
        raise ValueError("Bootstrap did not reach ready")
    if int(attrs.get("data-mindustry-bootstrap-nonblack-pixels", "0")) < 16:
        raise ValueError("Vanilla sprite framebuffer readback was not verified")


if __name__ == "__main__":
    verify(Path(sys.argv[1]).read_text())
    print("Actual packaged assets, WebGL upload/atlas/sprite/readback and settings proofs PASS")
