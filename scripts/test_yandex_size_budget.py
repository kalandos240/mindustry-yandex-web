#!/usr/bin/env python3
"""Quick unit tests for the release-size limit; no upstream checkout required."""
from pathlib import Path
import tempfile
import unittest

from yandex_size_budget import MAX_UNPACKED_BYTES, analyze, category, render


class SizeBudgetTests(unittest.TestCase):
    def test_decimal_limit_not_mib(self):
        self.assertEqual(MAX_UNPACKED_BYTES, 100_000_000)
        self.assertLess(MAX_UNPACKED_BYTES, 100 * 1024 * 1024)

    def test_categories_and_headroom(self):
        with tempfile.TemporaryDirectory() as root:
            stage = Path(root)
            (stage / "assets" / "maps" / "serpulo").mkdir(parents=True)
            (stage / "assets" / "maps" / "serpulo" / "groundZero.msav").write_bytes(b"abc")
            (stage / "mindustry.js").write_bytes(b"12345")
            result = analyze(stage)
            self.assertEqual(result["unpacked_bytes"], 8)
            self.assertEqual(result["categories"]["Maps (campaign + custom)"], 3)
            self.assertEqual(result["categories"]["TeaVM JavaScript"], 5)
            self.assertEqual(result["status"], "PASS")
            self.assertIn("Headroom bytes:", render(result))

    def test_decimal_boundary(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "mindustry.js"
            with path.open("wb") as output:
                output.truncate(MAX_UNPACKED_BYTES)
            self.assertEqual(analyze(Path(root))["status"], "LOW_HEADROOM")
            with path.open("ab") as output:
                output.write(b"1")
            self.assertEqual(analyze(Path(root))["status"], "OVER_LIMIT")

    def test_symlink_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            stage = Path(root)
            (stage / "index.html").write_text("ok")
            (stage / "alias").symlink_to(stage / "index.html")
            with self.assertRaisesRegex(ValueError, "symlink"):
                analyze(stage)

    def test_types(self):
        self.assertEqual(category("assets/music/menu.ogg"), "Audio")
        self.assertEqual(category("assets/sprites/sprites.png"), "Sprites and textures")
        self.assertEqual(category("licenses/Mindustry-GPL-3.0.txt"), "Licenses and source notices")


if __name__ == "__main__":
    unittest.main()
