"""Prove a real ore-producing vanilla drill reaches native spatial WebAudio.

This is a regression for the former no-op SoundControl.addAmbientSource port.
The game is unmodified; only a stock CI fixture places a real DrillBuild on
a mineable Ground Zero ore tile through the regular game world.
"""
import argparse
import asyncio
import json
import shutil
import threading
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from playwright.async_api import async_playwright

ROOT = Path(__file__).resolve().parents[1]
STAGE = ROOT / "web-runtime/build/web"
OUT = ROOT / "work/ambient-drill-evidence"


async def main(device):
    assert (STAGE / "mindustry.js").stat().st_size > 0
    assert (STAGE / "assets/sounds/loops/loopDrill.ogg").stat().st_size > 0
    OUT.mkdir(parents=True, exist_ok=True)
    port = 8157
    server = ThreadingHTTPServer(
        ("127.0.0.1", port),
        partial(SimpleHTTPRequestHandler, directory=str(STAGE)),
    )
    threading.Thread(target=server.serve_forever, daemon=True).start()
    try:
        async with async_playwright() as pw:
            browser = await pw.chromium.launch(
                executable_path=shutil.which("google-chrome") or shutil.which("chromium"),
                headless=True,
                args=["--no-sandbox", "--disable-dev-shm-usage"],
            )
            try:
                for mobile in (False, True):
                    if mobile != (device == 'mobile'):
                        continue
                    context = await browser.new_context(
                        viewport={"width": 390 if mobile else 1280, "height": 844 if mobile else 800},
                        is_mobile=mobile,
                        has_touch=mobile,
                    )
                    page = await context.new_page()
                    errors = []
                    page.on("pageerror", lambda error: errors.append(str(error)))
                    url = (
                        f"http://127.0.0.1:{port}/index.html?"
                        "lang=en&mindustryCampaignSmoke=groundZero"
                        "&mindustryBuildingMechanicsSmoke=1"
                        + ("&mindustryMobile=1" if mobile else "")
                    )
                    try:
                        await page.goto(url, timeout=180000)
                        # Unlock audio *before* the smoke fixture constructs its
                        # temporary mining drill. If we wait for the ore fixture
                        # to finish first, its short-lived drill may fill up and
                        # become silent during a later 45-second polling window.
                        await page.wait_for_function(
                            """() => document.documentElement.getAttribute('data-mindustry-web')==='ready'
                                && document.documentElement.getAttribute('data-mindustry-campaign-state')==='playing'""",
                            timeout=180000,
                        )
                        if mobile:
                            await page.touchscreen.tap(180, 440)
                        else:
                            await page.mouse.click(600, 450)
                        await page.wait_for_function(
                            "() => !!window.__mindustryAudio?.unlocked",
                            timeout=30000,
                        )
                        await page.wait_for_function(
                            """() => [...__mindustryAudio.voices.values()].some(v =>
                              v.loop && v.started && v.volume>0.001
                              && v.url.includes('/sounds/loops/loopDrill.ogg')
                              && v.gain?.gain?.value>0.001
                            )""",
                            timeout=45000,
                        )
                        await page.wait_for_function(
                            """() => document.documentElement.getAttribute('data-mindustry-building-mechanics')==='passed'
                               && document.documentElement.getAttribute('data-mindustry-building-mechanics-drill')==='ore-produced'""",
                            timeout=180000,
                        )
                        snapshot = await page.evaluate(
                            """() => ({
                              game:document.documentElement.getAttribute('data-mindustry-campaign-state'),
                              drill:document.documentElement.getAttribute('data-mindustry-building-mechanics-drill'),
                              stageTile:document.documentElement.getAttribute('data-mindustry-building-mechanics-tile'),
                              soundUnlocked:__mindustryAudio.unlocked,
                              context:__mindustryAudio.ctx?.state,
                              ambientDrillVoices:[...__mindustryAudio.voices.values()]
                                .filter(v=>v.url.includes('/sounds/loops/loopDrill.ogg'))
                                .map(v=>({url:v.url,loop:v.loop,started:v.started,
                                  volume:v.volume,pan:v.pan,gain:v.gain?.gain?.value})),
                              audioErrors:document.documentElement.getAttribute('data-mindustry-audio-error')
                            })"""
                        )
                        assert snapshot["game"] == "playing", snapshot
                        assert not errors, errors
                        (OUT / f"{device}.json").write_text(json.dumps(snapshot, indent=2))
                        await page.screenshot(path=str(OUT / f"{device}.png"))
                        print(f"{device}: stock ore-producing DrillBuild emits genuine spatial loopDrill WebAudio PASS", flush=True)
                    except Exception:
                        diag = await page.evaluate(
                            """() => ({
                              html:document.documentElement.outerHTML.slice(0,3500),
                              audio:window.__mindustryAudio ? {
                                unlocked:__mindustryAudio.unlocked,
                                state:__mindustryAudio.ctx?.state,
                                voices:[...__mindustryAudio.voices.values()].map(v=>({
                                  url:v.url,loop:v.loop,volume:v.volume,started:v.started,
                                  gain:v.gain?.gain?.value
                                })).slice(0,80)
                              } : null
                            })"""
                        )
                        (OUT / f"{device}-failure.json").write_text(json.dumps({"errors":errors, "diag":diag},indent=2))
                        await page.screenshot(path=str(OUT / f"{device}-failure.png"))
                        raise
                    finally:
                        await context.close()
            finally:
                await browser.close()
    finally:
        server.shutdown()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--device", choices=["desktop","mobile"], required=True)
    args = parser.parse_args()
    asyncio.run(main(args.device))
