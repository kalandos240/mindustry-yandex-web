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
        root.add(campaignButton).width(campaignWidth).height(campaignHeight).padBottom(8f);
        root.row();

        // Early Serpulo progression uses the exact stock TechNode requirements/objectives,
        // but presents them in a compact Yandex-friendly surface instead of constructing
        // the heavyweight desktop ResearchDialog tree.
        Table campaignProgress = new Table();
        campaignProgress.defaults().pad(2f);
        campaignProgress.add(Core.bundle.get("research", "Research")).colspan(2).padBottom(2f);
        campaignProgress.row();

        TextButton conveyorResearch = new TextButton("");
        conveyorResearch.clicked(() -> BrowserCampaignResearch.spend(mindustry.content.Blocks.conveyor));
        conveyorResearch.setDisabled(() -> mindustry.content.Blocks.conveyor.unlocked()
            || !BrowserCampaignResearch.canSpend(mindustry.content.Blocks.conveyor));
        conveyorResearch.update(() -> conveyorResearch.setText(
            mindustry.content.Blocks.conveyor.localizedName + " — " +
            (mindustry.content.Blocks.conveyor.unlocked()
                ? Core.bundle.get("unlocked", "Unlocked")
                : Core.bundle.get("research", "Research") + " " +
                    BrowserCampaignResearch.remaining(mindustry.content.Blocks.conveyor))
        ));
        campaignProgress.add(conveyorResearch).colspan(2).width(campaignWidth).height(mobile ? 48f : 40f);
        campaignProgress.row();

        TextButton junctionResearch = new TextButton("");
        junctionResearch.clicked(() -> BrowserCampaignResearch.spend(mindustry.content.Blocks.junction));
        junctionResearch.setDisabled(() -> mindustry.content.Blocks.junction.unlocked()
            || !BrowserCampaignResearch.canSpend(mindustry.content.Blocks.junction));
        junctionResearch.update(() -> junctionResearch.setText(
            mindustry.content.Blocks.junction.localizedName + " — " +
            (mindustry.content.Blocks.junction.unlocked()
                ? Core.bundle.get("unlocked", "Unlocked")
                : Core.bundle.get("research", "Research") + " " +
                    BrowserCampaignResearch.remaining(mindustry.content.Blocks.junction))
        ));
        campaignProgress.add(junctionResearch).width(campaignWidth / 2f - 3f).height(mobile ? 50f : 42f);

        TextButton routerResearch = new TextButton("");
        routerResearch.clicked(() -> BrowserCampaignResearch.spend(mindustry.content.Blocks.router));
        routerResearch.setDisabled(() -> mindustry.content.Blocks.router.unlocked()
            || !BrowserCampaignResearch.canSpend(mindustry.content.Blocks.router));
        routerResearch.update(() -> routerResearch.setText(
            mindustry.content.Blocks.router.localizedName + " — " +
            (mindustry.content.Blocks.router.unlocked()
                ? Core.bundle.get("unlocked", "Unlocked")
                : Core.bundle.get("research", "Research") + " " +
                    BrowserCampaignResearch.remaining(mindustry.content.Blocks.router))
        ));
        campaignProgress.add(routerResearch).width(campaignWidth / 2f - 3f).height(mobile ? 50f : 42f);
        campaignProgress.row();

        TextButton frozenForestButton = new TextButton("");
        frozenForestButton.clicked(BrowserCampaignRuntime::playFrozenForest);
        frozenForestButton.setDisabled(() -> !BrowserCampaignResearch.frozenForestReady());
        frozenForestButton.update(() -> {
            boolean ready = BrowserCampaignResearch.frozenForestReady();
            boolean saved = BrowserCampaignRuntime.hasFrozenForestSave();
            frozenForestButton.setText(Core.bundle.get("sector.frozenForest.name", "Frozen Forest") + " — " +
                (ready
                    ? Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play")
                    : Core.bundle.get("locked", "Locked")));
            markCampaignProgressState(
                mindustry.content.Blocks.conveyor.unlocked(),
                mindustry.content.Blocks.junction.unlocked(),
                mindustry.content.Blocks.router.unlocked(),
                ready,
                saved
            );
        });
        campaignProgress.add(frozenForestButton).colspan(2).width(campaignWidth).height(mobile ? 54f : 44f).padTop(2f);
        campaignProgress.row();

        // Keep later research compact on phones: expose one true TechTree prerequisite
        // at a time instead of adding a permanent button for every early power node.
        TextButton craterResearch = new TextButton("");
        craterResearch.clicked(BrowserCampaignResearch::spendNextCraterResearch);
        craterResearch.setDisabled(() -> {
            mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextCraterResearch();
            return next == null || !BrowserCampaignResearch.canSpend(next);
        });
        craterResearch.update(() -> {
            mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextCraterResearch();
            if(BrowserCampaignResearch.waitingForCraterCoal()){
                craterResearch.setText(Core.bundle.format("requirement.produce", mindustry.content.Items.coal.localizedName));
            }else if(next == null){
                craterResearch.setText(Core.bundle.get("research", "Research") + " — " +
                    Core.bundle.get("complete", "Complete"));
            }else{
                craterResearch.setText(next.localizedName + " — " +
                    Core.bundle.get("research", "Research") + " " + BrowserCampaignResearch.remaining(next));
            }
        });

        TextButton craterButton = new TextButton("");
        craterButton.clicked(BrowserCampaignRuntime::playCrateredBattleground);
        craterButton.setDisabled(() -> !BrowserCampaignResearch.crateredBattlegroundReady());
        craterButton.update(() -> {
            boolean ready = BrowserCampaignResearch.crateredBattlegroundReady();
            boolean saved = BrowserCampaignRuntime.hasCrateredBattlegroundSave();
            craterButton.setText(Core.bundle.get("sector.crateredBattleground.name", "Cratered Battleground") + " — " +
                (ready
                    ? Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play")
                    : Core.bundle.get("locked", "Locked")));
            markCampaignCraterState(
                mindustry.content.Blocks.mechanicalDrill.unlocked(),
                mindustry.content.Items.coal.unlocked(),
                mindustry.content.Blocks.combustionGenerator.unlocked(),
                mindustry.content.Blocks.powerNode.unlocked(),
                mindustry.content.Blocks.mender.unlocked(),
                ready,
                saved
            );
        });

        campaignProgress.add(craterResearch).width(campaignWidth / 2f - 3f).height(mobile ? 50f : 42f).padTop(2f);
        campaignProgress.add(craterButton).width(campaignWidth / 2f - 3f).height(mobile ? 50f : 42f).padTop(2f);

        root.add(campaignProgress).width(campaignWidth).padBottom(8f);
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
            ? Math.max(56f, Math.min(110f, Core.graphics.getHeight() - 440f))
            : 330f;
        Cell<ScrollPane> mapPaneCell = root.add(pane).width(mobile ? 320f : 380f).height(mapPaneHeight);
        final float[] lastMapPaneHeight = {mapPaneHeight};
        if(mobile){
            pane.update(() -> {
                float nextHeight = Math.max(56f, Math.min(110f, Core.graphics.getHeight() - 440f));
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
            .disabled(button -> BrowserCampaignRuntime.active())
            .pad(8f);
'''
if text.count(old_hud) != 1:
    raise SystemExit("Campaign UI HUD anchor no longer matches post-pause UI")
text = text.replace(old_hud, new_hud, 1)

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

    @JSBody(params = {"conveyor", "junction", "router", "frozenReady", "frozenSaved"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-progress-ui','ready'); document.documentElement.setAttribute('data-mindustry-campaign-conveyor-unlocked',conveyor ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-junction-unlocked',junction ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-router-unlocked',router ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-frozen-forest-ready',frozenReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-frozen-forest-save',frozenSaved ? 'true' : 'false');")
    private static native void markCampaignProgressState(boolean conveyor, boolean junction, boolean router, boolean frozenReady, boolean frozenSaved);

    @JSBody(params = {"drill", "coal", "combustion", "powerNode", "mender", "craterReady", "craterSaved"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-crater-ui','ready'); document.documentElement.setAttribute('data-mindustry-campaign-mechanical-drill-unlocked',drill ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-coal-unlocked',coal ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-combustion-generator-unlocked',combustion ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-power-node-unlocked',powerNode ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-mender-unlocked',mender ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-cratered-battleground-ready',craterReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-cratered-battleground-save',craterSaved ? 'true' : 'false');")
    private static native void markCampaignCraterState(boolean drill, boolean coal, boolean combustion, boolean powerNode, boolean mender, boolean craterReady, boolean craterSaved);

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
print("Enabled lean Campaign/Continue menu and campaign-aware desktop/mobile Back controls")
