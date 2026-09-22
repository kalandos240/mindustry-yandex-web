#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserUiRuntime.java"

if not UI.is_file():
    raise SystemExit(f"Missing browser UI runtime: {UI}")

text = UI.read_text(encoding="utf-8")

old_menu = '''        root.add(Core.bundle.get("customgame", "Custom Game")).padBottom(8f);
        root.row();
        root.button(Core.bundle.get("continue", "Continue"), BrowserLocalMapRuntime::continueSaved)
            .width(mobile ? 320f : 380f)
            .height(mobile ? 54f : 46f)
            .disabled(button -> !BrowserSaveRuntime.hasLocalSession())
            .padBottom(8f);
        root.row();

        Table mapButtons = new Table();
'''
new_menu = '''        // Touch-first Yandex UI keeps campaign actions large enough for phones,
        // while desktop retains the denser control sizing used by the local-map menu.
        float campaignWidth = mobile ? 320f : 380f;
        float campaignHeight = mobile ? 58f : 46f;

        root.add(Core.bundle.get("campaign", "Campaign") + " — " +
            Core.bundle.get("sector.groundZero.name", "Ground Zero")).padBottom(4f);
        root.row();

        TextButton campaignButton = new TextButton("");
        boolean[] campaignContinue = {BrowserCampaignRuntime.hasGroundZeroSave()};
        campaignButton.setText(Core.bundle.get(campaignContinue[0] ? "continue" : "play",
            campaignContinue[0] ? "Continue" : "Play"));
        markCampaignUiAction(campaignContinue[0] ? "continue" : "play");
        campaignButton.clicked(BrowserCampaignRuntime::playGroundZero);
        campaignButton.update(() -> {
            boolean hasSave = BrowserCampaignRuntime.hasGroundZeroSave();
            if(hasSave != campaignContinue[0]){
                campaignContinue[0] = hasSave;
                campaignButton.setText(Core.bundle.get(hasSave ? "continue" : "play",
                    hasSave ? "Continue" : "Play"));
                markCampaignUiAction(hasSave ? "continue" : "play");
            }
        });
        root.add(campaignButton).width(campaignWidth).height(campaignHeight).padBottom(10f);
        root.row();

        root.add(Core.bundle.get("customgame", "Custom Game")).padBottom(8f);
        root.row();
        root.button(Core.bundle.get("continue", "Continue"), BrowserLocalMapRuntime::continueSaved)
            .width(mobile ? 320f : 380f)
            .height(mobile ? 54f : 46f)
            .disabled(button -> !BrowserSaveRuntime.hasLocalSession())
            .padBottom(8f);
        root.row();

        Table mapButtons = new Table();
'''
if text.count(old_menu) != 1:
    raise SystemExit("Campaign UI menu anchor no longer matches post-local-save browser UI")
text = text.replace(old_menu, new_menu, 1)

old_pane = '''        root.add(pane).width(mobile ? 320f : 380f).height(mobile ? 430f : 500f);
        ui.menuGroup.addChild(root);
'''
new_pane = '''        // Landscape phones can be only ~320-400 logical px tall. Keep the
        // campaign controls touch-sized, and let the map list yield vertical space.
        float mapPaneHeight = mobile
            ? Math.max(120f, Math.min(220f, Core.graphics.getHeight() - 230f))
            : 430f;
        Cell<ScrollPane> mapPaneCell = root.add(pane).width(mobile ? 320f : 380f).height(mapPaneHeight);
        final float[] lastMapPaneHeight = {mapPaneHeight};
        if(mobile){
            pane.update(() -> {
                float nextHeight = Math.max(120f, Math.min(220f, Core.graphics.getHeight() - 230f));
                if(Math.abs(nextHeight - lastMapPaneHeight[0]) > 0.5f){
                    lastMapPaneHeight[0] = nextHeight;
                    mapPaneCell.height(nextHeight);
                    root.invalidateHierarchy();
                    markCampaignUiResized(nextHeight);
                }
            });
        }
        ui.menuGroup.addChild(root);
        markCampaignUiReady(mobile ? "mobile" : "desktop", campaignWidth, campaignHeight, mapPaneHeight);
'''
if text.count(old_pane) != 1:
    raise SystemExit("Campaign UI map-pane anchor no longer matches")
text = text.replace(old_pane, new_pane, 1)

old_hud = '''        controls.button(Core.bundle.get("back", "Back"), BrowserLocalMapRuntime::returnToMenu)
            .size(mobile ? 132f : 116f, mobile ? 52f : 44f)
            .pad(8f);
        controls.button(Core.bundle.get("pause", "Pause"), BrowserLocalMapRuntime::pause)
            .size(mobile ? 132f : 116f, mobile ? 52f : 44f)
            .pad(8f);
'''
new_hud = '''        boolean[] campaignBackUiSmoke = {false};
        controls.update(() -> {
            if(!campaignBackUiSmoke[0] && campaignBackUiSmokeRequested()
            && BrowserCampaignRuntime.active() && campaignCoreReady()){
                campaignBackUiSmoke[0] = true;
                markCampaignUiBackSmoke();
                BrowserCampaignRuntime.returnToMenu();
            }
        });

        controls.button(Core.bundle.get("back", "Back"), () -> {
            if(BrowserCampaignRuntime.active()){
                BrowserCampaignRuntime.returnToMenu();
            }else{
                BrowserLocalMapRuntime.returnToMenu();
            }
        }).size(mobile ? 148f : 116f, mobile ? 56f : 44f).pad(8f);
        controls.button(Core.bundle.get("pause", "Pause"), BrowserLocalMapRuntime::pause)
            .size(mobile ? 148f : 116f, mobile ? 56f : 44f)
            .pad(8f);
'''
if text.count(old_hud) != 1:
    raise SystemExit("Campaign UI HUD anchor no longer matches post-pause UI")
text = text.replace(old_hud, new_hud, 1)

old_pause_resume = '''        overlay.button(Core.bundle.get("resume", "Resume"), BrowserLocalMapRuntime::resume)
            .size(mobile ? 180f : 156f, mobile ? 58f : 48f);
'''
new_pause_resume = '''        overlay.button(Core.bundle.get("resume", "Resume"), BrowserLocalMapRuntime::resume)
            .size(mobile ? 196f : 156f, mobile ? 62f : 48f);
'''
if text.count(old_pause_resume) != 1:
    raise SystemExit("Campaign UI pause-overlay Resume anchor no longer matches post-pause UI")
text = text.replace(old_pause_resume, new_pause_resume, 1)

old_pause_back = '''        overlay.button(Core.bundle.get("back", "Back"), BrowserLocalMapRuntime::returnToMenu)
            .size(mobile ? 180f : 156f, mobile ? 58f : 48f)
            .padTop(8f);
'''
new_pause_back = '''        overlay.button(Core.bundle.get("back", "Back"), () -> {
            if(BrowserCampaignRuntime.active()){
                BrowserCampaignRuntime.returnToMenu();
            }else{
                BrowserLocalMapRuntime.returnToMenu();
            }
        }).size(mobile ? 196f : 156f, mobile ? 62f : 48f).padTop(8f);
'''
if text.count(old_pause_back) != 1:
    raise SystemExit("Campaign UI pause-overlay Back anchor no longer matches post-save UI")
text = text.replace(old_pause_back, new_pause_back, 1)

marker_anchor = '''    @JSBody(params = {"slot"}, script = "document.documentElement.setAttribute('data-mindustry-local-save-ui', 'ready'); document.documentElement.setAttribute('data-mindustry-local-continue-ui', 'ready'); document.documentElement.setAttribute('data-mindustry-local-continue-slot', slot);")
    private static native void markLocalSaveUiReady(String slot);
'''
marker_replacement = '''    @JSBody(params = {"layout", "buttonWidth", "buttonHeight", "paneHeight"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-ui','ready'); document.documentElement.setAttribute('data-mindustry-campaign-ui-layout',layout); document.documentElement.setAttribute('data-mindustry-campaign-ui-button-width',String(buttonWidth)); document.documentElement.setAttribute('data-mindustry-campaign-ui-button-height',String(buttonHeight)); document.documentElement.setAttribute('data-mindustry-campaign-ui-map-pane-height',String(paneHeight));")
    private static native void markCampaignUiReady(String layout, float buttonWidth, float buttonHeight, float paneHeight);

    @JSBody(params = {"action"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-ui-action',action);")
    private static native void markCampaignUiAction(String action);

    @JSBody(params = {"paneHeight"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-ui-resized','ready'); document.documentElement.setAttribute('data-mindustry-campaign-ui-map-pane-height',String(paneHeight));")
    private static native void markCampaignUiResized(float paneHeight);

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryCampaignUiBackSmoke') === '1';")
    private static native boolean campaignBackUiSmokeRequested();

    @JSBody(script = "return document.documentElement.getAttribute('data-mindustry-campaign-core') === 'ready';")
    private static native boolean campaignCoreReady();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-campaign-ui-back-smoke','triggered');")
    private static native void markCampaignUiBackSmoke();

    @JSBody(params = {"slot"}, script = "document.documentElement.setAttribute('data-mindustry-local-save-ui', 'ready'); document.documentElement.setAttribute('data-mindustry-local-continue-ui', 'ready'); document.documentElement.setAttribute('data-mindustry-local-continue-slot', slot);")
    private static native void markLocalSaveUiReady(String slot);
'''
if text.count(marker_anchor) != 1:
    raise SystemExit("Campaign UI marker anchor no longer matches post-local-save UI")
text = text.replace(marker_anchor, marker_replacement, 1)

UI.write_text(text, encoding="utf-8")
print("Enabled lean Campaign/Continue menu with campaign-aware desktop/mobile Back/Pause/Resume controls")
