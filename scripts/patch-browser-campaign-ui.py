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
        boolean[] campaignContinue = {BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.groundZero)};
        campaignButton.setText(Core.bundle.get(campaignContinue[0] ? "continue" : "play",
            campaignContinue[0] ? "Continue" : "Play"));
        if(BrowserCampaignRuntime.diagnosticsEnabled()){
            markCampaignUiAction(campaignContinue[0] ? "continue" : "play");
        }
        campaignButton.clicked(BrowserCampaignRuntime::playGroundZero);
        campaignButton.update(() -> {
            boolean hasSave = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.groundZero);
            if(hasSave != campaignContinue[0]){
                campaignContinue[0] = hasSave;
                campaignButton.setText(Core.bundle.get(hasSave ? "continue" : "play",
                    hasSave ? "Continue" : "Play"));
                if(BrowserCampaignRuntime.diagnosticsEnabled()){
                    markCampaignUiAction(hasSave ? "continue" : "play");
                }
            }
        });
        root.add(campaignButton).width(campaignWidth).height(campaignHeight).padBottom(8f);
        root.row();

        // Early Serpulo progression uses the exact stock TechNode requirements/objectives,
        // but presents them in a compact Yandex-friendly surface instead of constructing
        // the heavyweight desktop ResearchDialog tree.
        BrowserCampaignResearch.refreshUnlocks();
        final int[] campaignUnlockRefreshFrame = {0};
        Table campaignProgress = new Table();
        campaignProgress.update(() -> {
            if((++campaignUnlockRefreshFrame[0] & 31) == 0){
                BrowserCampaignResearch.refreshUnlocks();
            }
        });
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
        frozenForestButton.setDisabled(() -> !BrowserCampaignRuntime.campaignAssetsReady()
            || !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.frozenForest));
        frozenForestButton.update(() -> {
            boolean ready = BrowserCampaignResearch.ready(mindustry.content.SectorPresets.frozenForest);
            boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.frozenForest);
            frozenForestButton.setText(Core.bundle.get("sector.frozenForest.name", "Frozen Forest") + " — " +
                (ready
                    ? Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play")
                    : Core.bundle.get("locked", "Locked")));
            if(BrowserCampaignRuntime.diagnosticsEnabled()){
                markCampaignProgressState(
                    mindustry.content.Blocks.conveyor.unlocked(),
                    mindustry.content.Blocks.junction.unlocked(),
                    mindustry.content.Blocks.router.unlocked(),
                    ready,
                    saved
                );
            }
        });
        campaignProgress.add(frozenForestButton).colspan(2).width(campaignWidth).height(mobile ? 54f : 44f).padTop(2f);
        campaignProgress.row();

        // Keep later research compact on phones: expose one true TechTree prerequisite
        // at a time instead of adding a permanent button for every early power node.
        TextButton craterResearch = new TextButton("");
        craterResearch.clicked(() -> {
            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.crateredBattleground)){
                BrowserCampaignResearch.spendNextCraterResearch();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.crateredBattleground)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.ruinousShores)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextRuinousResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.ruinousShores)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.ruinousShores)){
                BrowserCampaignRuntime.playRuinousShores();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.ruinousShores)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.windsweptIslands)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextWindsweptResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.windsweptIslands)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.windsweptIslands)){
                BrowserCampaignRuntime.playWindsweptIslands();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.windsweptIslands)
            && BrowserCampaignResearch.ready(mindustry.content.SectorPresets.biomassFacility)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.biomassFacility)){
                BrowserCampaignRuntime.playBiomassFacility();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.biomassFacility)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.fungalPass)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextFungalResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.fungalPass)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.fungalPass)){
                BrowserCampaignRuntime.playFungalPass();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.fungalPass)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.frontier)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextFrontierResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.frontier)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.frontier)){
                BrowserCampaignRuntime.playFrontier();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.frontier)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.saltFlats)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextSaltResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.saltFlats)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.saltFlats)){
                BrowserCampaignRuntime.playSaltFlats();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.saltFlats)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.tarFields)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextTarResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.tarFields)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.tarFields)){
                BrowserCampaignRuntime.playTarFields();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.tarFields)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.impact0078)){
                BrowserCampaignResearch.spendNextImpactResearch();
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.impact0078)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.impact0078)){
                BrowserCampaignRuntime.playImpact0078();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.impact0078)
            && BrowserCampaignResearch.ready(mindustry.content.SectorPresets.stainedMountains)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.stainedMountains)){
                BrowserCampaignRuntime.playStainedMountains();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.stainedMountains)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.infestedCanyons)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextInfestedResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.infestedCanyons)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.infestedCanyons)){
                BrowserCampaignRuntime.playInfestedCanyons();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.infestedCanyons)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.nuclearComplex)){
                BrowserCampaignResearch.spendNextNuclearResearch();
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.nuclearComplex)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.nuclearComplex)){
                BrowserCampaignRuntime.playNuclearComplex();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.nuclearComplex)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.desolateRift)){
                BrowserCampaignResearch.spendNextDesolateResearch();
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.desolateRift)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.desolateRift)){
                BrowserCampaignRuntime.playDesolateRift();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.desolateRift)
            && BrowserCampaignResearch.ready(mindustry.content.SectorPresets.facility32m)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.facility32m)){
                BrowserCampaignRuntime.playFacility32m();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.facility32m)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.perilousHarbor)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextPerilousResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.perilousHarbor)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.perilousHarbor)){
                BrowserCampaignRuntime.playPerilousHarbor();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.perilousHarbor)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.extractionOutpost)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextExtractionResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.extractionOutpost)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.extractionOutpost)){
                BrowserCampaignRuntime.playExtractionOutpost();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.extractionOutpost)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.coastline)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextCoastlineResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.coastline)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.coastline)){
                BrowserCampaignRuntime.playCoastline();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.coastline)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.navalFortress)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextNavalFortressResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.navalFortress)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.navalFortress)){
                BrowserCampaignRuntime.playNavalFortress();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.navalFortress)
            && BrowserCampaignResearch.ready(mindustry.content.SectorPresets.overgrowth)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.overgrowth)){
                BrowserCampaignRuntime.playOvergrowth();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.overgrowth)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.mycelialBastion)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextMycelialResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.mycelialBastion)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.mycelialBastion)){
                BrowserCampaignRuntime.playMycelialBastion();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.mycelialBastion)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.littoralShipyard)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextLittoralResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.littoralShipyard)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.littoralShipyard)){
                BrowserCampaignRuntime.playLittoralShipyard();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.littoralShipyard)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.planetaryTerminal)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextTerminalResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.planetaryTerminal)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.planetaryTerminal)){
                BrowserCampaignRuntime.playPlanetaryTerminal();
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.taintedWoods)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.taintedWoods)){
                BrowserCampaignRuntime.playTaintedWoods();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.taintedWoods)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.atolls)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextAtollsResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.atolls)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.atolls)){
                BrowserCampaignRuntime.playAtolls();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.atolls)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.testingGrounds)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextTestingGroundsResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.testingGrounds)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.testingGrounds)){
                BrowserCampaignRuntime.playTestingGrounds();
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.sunkenPier)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.sunkenPier)){
                BrowserCampaignRuntime.playSunkenPier();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.sunkenPier)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.weatheredChannels)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextWeatheredResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.weatheredChannels)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.weatheredChannels)){
                BrowserCampaignRuntime.playWeatheredChannels();
            }
        });
        craterResearch.setDisabled(() -> {
            if(!BrowserCampaignRuntime.campaignAssetsReady()) return true;
            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.crateredBattleground)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextCraterResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.crateredBattleground)) return true;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.ruinousShores)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextRuinousResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.ruinousShores)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.windsweptIslands)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextWindsweptResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.windsweptIslands)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.biomassFacility)) return true;
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.biomassFacility)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.fungalPass)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextFungalResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.fungalPass)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.frontier)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextFrontierResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.frontier)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.saltFlats)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextSaltResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.saltFlats)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.tarFields)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextTarResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.tarFields)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.impact0078)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextImpactResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.impact0078)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.stainedMountains)) return true;
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.stainedMountains)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.infestedCanyons)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextInfestedResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.infestedCanyons)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.nuclearComplex)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextNuclearResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.nuclearComplex)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.desolateRift)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextDesolateResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.desolateRift)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.facility32m)) return true;
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.facility32m)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.perilousHarbor)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextPerilousResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.perilousHarbor)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.extractionOutpost)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextExtractionResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.extractionOutpost)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.coastline)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextCoastlineResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.coastline)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.navalFortress)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextNavalFortressResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.navalFortress)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.overgrowth)) return true;
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.overgrowth)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.mycelialBastion)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextMycelialResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.mycelialBastion)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.littoralShipyard)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextLittoralResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.littoralShipyard)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.planetaryTerminal)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextTerminalResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.planetaryTerminal)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.taintedWoods)) return true;
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.taintedWoods)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.atolls)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextAtollsResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.atolls)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.testingGrounds)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextTestingGroundsResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.testingGrounds)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.sunkenPier)) return true;
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.sunkenPier)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.weatheredChannels)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextWeatheredResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            return BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.weatheredChannels);
        });
        craterResearch.update(() -> {
            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.crateredBattleground)){
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
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.crateredBattleground)){
                craterResearch.setText(Core.bundle.get("sector.crateredBattleground.name", "Cratered Battleground") +
                    " — " + Core.bundle.get("locked", "Locked"));
            }else if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.ruinousShores)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextRuinousResearch();
                craterResearch.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.ruinousShores)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.ruinousShores);
                craterResearch.setText(Core.bundle.get("sector.ruinousShores.name", "Ruinous Shores") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.windsweptIslands)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextWindsweptResearch();
                craterResearch.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.windsweptIslands)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.windsweptIslands);
                craterResearch.setText(Core.bundle.get("sector.windsweptIslands.name", "Windswept Islands") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.biomassFacility)){
                boolean ready = BrowserCampaignResearch.ready(mindustry.content.SectorPresets.biomassFacility);
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.biomassFacility);
                craterResearch.setText(Core.bundle.get("sector.biomassFacility.name", "Biomass Synthesis Facility") + " — " +
                    (ready ? Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play")
                        : Core.bundle.get("locked", "Locked")));
            }else if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.fungalPass)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextFungalResearch();
                craterResearch.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.fungalPass)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.fungalPass);
                craterResearch.setText(Core.bundle.get("sector.fungalPass.name", "Fungal Pass") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.frontier)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextFrontierResearch();
                craterResearch.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.frontier)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.frontier);
                craterResearch.setText(Core.bundle.get("sector.frontier.name", "Frontier") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.saltFlats)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextSaltResearch();
                craterResearch.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.saltFlats)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.saltFlats);
                craterResearch.setText(Core.bundle.get("sector.saltFlats.name", "Salt Flats") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.tarFields)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextTarResearch();
                craterResearch.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.tarFields)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.tarFields);
                craterResearch.setText(Core.bundle.get("sector.tarFields.name", "Tar Fields") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.impact0078)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextImpactResearch();
                if(BrowserCampaignResearch.waitingForImpactThorium()){
                    craterResearch.setText(Core.bundle.format("requirement.produce", mindustry.content.Items.thorium.localizedName));
                }else{
                    craterResearch.setText(next == null
                        ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                        : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                            BrowserCampaignResearch.remaining(next));
                }
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.impact0078)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.impact0078);
                craterResearch.setText(Core.bundle.get("sector.impact0078.name", "Impact 0078") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.stainedMountains)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.stainedMountains);
                craterResearch.setText(Core.bundle.get("sector.stainedMountains.name", "Stained Mountains") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.infestedCanyons)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextInfestedResearch();
                craterResearch.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.infestedCanyons)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.infestedCanyons);
                craterResearch.setText(Core.bundle.get("sector.infestedCanyons.name", "Infested Canyons") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.nuclearComplex)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextNuclearResearch();
                if(BrowserCampaignResearch.waitingForNuclearPlastanium()){
                    craterResearch.setText(Core.bundle.format("requirement.produce", mindustry.content.Items.plastanium.localizedName));
                }else{
                    craterResearch.setText(next == null
                        ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                        : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                            BrowserCampaignResearch.remaining(next));
                }
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.nuclearComplex)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.nuclearComplex);
                craterResearch.setText(Core.bundle.get("sector.nuclearComplex.name", "Nuclear Production Complex") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.desolateRift)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextDesolateResearch();
                if(BrowserCampaignResearch.waitingForDesolateCryofluid()){
                    craterResearch.setText(Core.bundle.format("requirement.produce", mindustry.content.Liquids.cryofluid.localizedName));
                }else{
                    craterResearch.setText(next == null
                        ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                        : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                            BrowserCampaignResearch.remaining(next));
                }
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.desolateRift)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.desolateRift);
                craterResearch.setText(Core.bundle.get("sector.desolateRift.name", "Desolate Rift") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.facility32m)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.facility32m);
                craterResearch.setText(Core.bundle.get("sector.facility32m.name", "Facility 32M") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.perilousHarbor)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextPerilousResearch();
                craterResearch.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.perilousHarbor)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.perilousHarbor);
                craterResearch.setText(Core.bundle.get("sector.perilousHarbor.name", "Perilous Harbor") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.extractionOutpost)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextExtractionResearch();
                craterResearch.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.extractionOutpost)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.extractionOutpost);
                craterResearch.setText(Core.bundle.get("sector.extractionOutpost.name", "Extraction Outpost") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.coastline)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextCoastlineResearch();
                craterResearch.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.coastline)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.coastline);
                craterResearch.setText(Core.bundle.get("sector.coastline.name", "Coastline") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.navalFortress)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextNavalFortressResearch();
                craterResearch.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.navalFortress)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.navalFortress);
                craterResearch.setText(Core.bundle.get("sector.navalFortress.name", "Naval Fortress") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.overgrowth)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.overgrowth);
                craterResearch.setText(Core.bundle.get("sector.overgrowth.name", "Overgrowth") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.mycelialBastion)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextMycelialResearch();
                craterResearch.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.mycelialBastion)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.mycelialBastion);
                craterResearch.setText(Core.bundle.get("sector.mycelialBastion.name", "Mycelial Bastion") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.littoralShipyard)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextLittoralResearch();
                craterResearch.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.littoralShipyard)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.littoralShipyard);
                craterResearch.setText(Core.bundle.get("sector.littoralShipyard.name", "Littoral Shipyard") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.planetaryTerminal)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextTerminalResearch();
                craterResearch.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.planetaryTerminal)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.planetaryTerminal);
                craterResearch.setText(Core.bundle.get("sector.planetaryTerminal.name", "Planetary Launch Terminal") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.taintedWoods)){
                craterResearch.setText(Core.bundle.format("requirement.produce", mindustry.content.Items.sporePod.localizedName));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.taintedWoods)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.taintedWoods);
                craterResearch.setText(Core.bundle.get("sector.taintedWoods.name", "Tainted Woods") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.atolls)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextAtollsResearch();
                craterResearch.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.atolls)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.atolls);
                craterResearch.setText(Core.bundle.get("sector.atolls.name", "Atolls") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.testingGrounds)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextTestingGroundsResearch();
                craterResearch.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.testingGrounds)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.testingGrounds);
                craterResearch.setText(Core.bundle.get("sector.testingGrounds.name", "Testing Grounds") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.sunkenPier)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.sunkenPier);
                craterResearch.setText(Core.bundle.get("sector.sunkenPier.name", "Sunken Pier") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.weatheredChannels)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextWeatheredResearch();
                craterResearch.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.weatheredChannels)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.weatheredChannels);
                craterResearch.setText(Core.bundle.get("sector.weatheredChannels.name", "Weathered Channels") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else{
                craterResearch.setText(Core.bundle.get("planet.serpulo.name", "Serpulo") + " — " +
                    Core.bundle.get("complete", "Complete"));
            }

            if(BrowserCampaignRuntime.diagnosticsEnabled()){
                markCampaignRuinousState(
                    mindustry.content.Blocks.graphitePress.unlocked(),
                    mindustry.content.Blocks.siliconSmelter.unlocked(),
                    mindustry.content.Blocks.kiln.unlocked(),
                    mindustry.content.Blocks.mechanicalPump.unlocked(),
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.ruinousShores),
                    BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.ruinousShores)
                );
                markCampaignWindsweptState(
                    mindustry.content.Blocks.pneumaticDrill.unlocked(),
                    mindustry.content.Blocks.duo.unlocked(),
                    mindustry.content.Blocks.scatter.unlocked(),
                    mindustry.content.Blocks.hail.unlocked(),
                    mindustry.content.Blocks.steamGenerator.unlocked(),
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.windsweptIslands),
                    BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.windsweptIslands)
                );
                markCampaignSaltBranchState(
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.biomassFacility),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.biomassFacility),
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.fungalPass),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.fungalPass),
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.frontier),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.frontier),
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.saltFlats),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.saltFlats),
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.tarFields),
                    BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.tarFields)
                );
                markCampaignImpactState(
                    mindustry.content.Blocks.laserDrill.unlocked(),
                    mindustry.content.Items.thorium.unlocked(),
                    mindustry.content.Blocks.lancer.unlocked(),
                    mindustry.content.Blocks.salvo.unlocked(),
                    mindustry.content.Blocks.coreFoundation.unlocked(),
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.impact0078),
                    BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.impact0078)
                );
                markCampaignLateState(
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.stainedMountains),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.stainedMountains),
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.infestedCanyons),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.infestedCanyons),
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.nuclearComplex),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.nuclearComplex),
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.desolateRift),
                    BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.desolateRift)
                );
                markCampaignFinalInfraState(
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.facility32m),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.facility32m),
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.perilousHarbor),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.perilousHarbor),
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.extractionOutpost),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.extractionOutpost),
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.coastline),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.coastline),
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.navalFortress),
                    BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.navalFortress)
                );
                markCampaignTerminalState(
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.overgrowth),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.overgrowth),
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.mycelialBastion),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.mycelialBastion),
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.littoralShipyard),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.littoralShipyard),
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.planetaryTerminal),
                    BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.planetaryTerminal)
                );
                markCampaignOptionalState(
                    mindustry.content.Items.sporePod.unlocked(),
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.taintedWoods),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.taintedWoods),
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.atolls),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.atolls),
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.testingGrounds),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.testingGrounds),
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.sunkenPier),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.sunkenPier),
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.weatheredChannels),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.weatheredChannels)
                );
            }
        });

        TextButton craterButton = new TextButton("");
        craterButton.clicked(BrowserCampaignRuntime::playCrateredBattleground);
        craterButton.setDisabled(() -> !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.crateredBattleground));
        craterButton.update(() -> {
            boolean ready = BrowserCampaignResearch.ready(mindustry.content.SectorPresets.crateredBattleground);
            boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.crateredBattleground);
            craterButton.setText(Core.bundle.get("sector.crateredBattleground.name", "Cratered Battleground") + " — " +
                (ready
                    ? Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play")
                    : Core.bundle.get("locked", "Locked")));
            if(BrowserCampaignRuntime.diagnosticsEnabled()){
                markCampaignCraterState(
                    mindustry.content.Blocks.mechanicalDrill.unlocked(),
                    mindustry.content.Items.coal.unlocked(),
                    mindustry.content.Blocks.combustionGenerator.unlocked(),
                    mindustry.content.Blocks.powerNode.unlocked(),
                    mindustry.content.Blocks.mender.unlocked(),
                    ready,
                    saved
                );
            }
        });

        campaignProgress.add(craterResearch).width(campaignWidth / 2f - 3f).height(mobile ? 50f : 42f).padTop(2f);
        campaignProgress.add(craterButton).width(campaignWidth / 2f - 3f).height(mobile ? 50f : 42f).padTop(2f);

        root.add(campaignProgress).width(campaignWidth).padBottom(8f);
        root.row();

        // Erekir uses the same compact one-action-at-a-time pattern so mobile does not
        // grow a permanent row for every Erekir prerequisite.
        TextButton erekirProgress = new TextButton("");
        erekirProgress.clicked(() -> {
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.onset)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextOnsetResearch();
                if(BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.onset) && next != null
                && BrowserCampaignResearch.canSpend(next)){
                    BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextOnsetResearch());
                }else{
                    BrowserCampaignRuntime.playOnset();
                }
            }else if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.aegis)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextAegisResearch());
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.aegis)){
                BrowserCampaignRuntime.playAegis();
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.lake)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.lake)){
                BrowserCampaignRuntime.playLake();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.lake)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.intersect)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextIntersectResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.intersect)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.intersect)){
                BrowserCampaignRuntime.playIntersect();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.intersect)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.atlas)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextAtlasResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.atlas)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.atlas)){
                BrowserCampaignRuntime.playAtlas();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.atlas)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.split)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextSplitResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.split)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.split)){
                BrowserCampaignRuntime.playSplit();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.split)
            && BrowserCampaignResearch.ready(mindustry.content.SectorPresets.basin)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.basin)){
                BrowserCampaignRuntime.playBasin();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.basin)
            && BrowserCampaignResearch.ready(mindustry.content.SectorPresets.marsh)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.marsh)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextMarshResearch();
                if(BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.marsh) && next != null
                && BrowserCampaignResearch.canSpend(next)){
                    BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextMarshResearch());
                }else{
                    BrowserCampaignRuntime.playMarsh();
                }
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.marsh)
            && BrowserCampaignResearch.ready(mindustry.content.SectorPresets.peaks)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.peaks)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextPeaksResearch();
                if(BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.peaks) && next != null
                && BrowserCampaignResearch.canSpend(next)){
                    BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextPeaksResearch());
                }else{
                    BrowserCampaignRuntime.playPeaks();
                }
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.peaks)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.ravine)){
                BrowserCampaignRuntime.playPeaks();
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.ravine)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.ravine)){
                BrowserCampaignRuntime.playRavine();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.ravine)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.caldera)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextCalderaResearch();
                if(next != null && BrowserCampaignResearch.canSpend(next)){
                    BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextCalderaResearch());
                }else{
                    BrowserCampaignRuntime.playRavine();
                }
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.caldera)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.caldera)){
                BrowserCampaignRuntime.playCaldera();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.caldera)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.stronghold)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextStrongholdResearch();
                if(next != null && BrowserCampaignResearch.canSpend(next)){
                    BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextStrongholdResearch());
                }else{
                    BrowserCampaignRuntime.playCaldera();
                }
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.stronghold)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.stronghold)){
                BrowserCampaignRuntime.playStronghold();
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.crevice)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.crevice)){
                BrowserCampaignRuntime.playCrevice();
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.siege)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.siege)){
                BrowserCampaignRuntime.playSiege();
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.crossroads)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.crossroads)){
                BrowserCampaignRuntime.playCrossroads();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.crossroads)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.karst)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextKarstResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.karst)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.karst)){
                BrowserCampaignRuntime.playKarst();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.karst)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.origin)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextOriginResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.origin)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.origin)){
                BrowserCampaignRuntime.playOrigin();
            }
        });
        erekirProgress.setDisabled(() -> {
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.onset)) return false;
            if(!BrowserCampaignRuntime.campaignAssetsReady()) return true;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.aegis)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextAegisResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.aegis)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.lake)) return true;
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.lake)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.intersect)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextIntersectResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.intersect)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.atlas)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextAtlasResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.atlas)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.split)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextSplitResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.split)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.basin)) return true;
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.basin)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.marsh)) return true;
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.marsh)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.peaks)) return true;
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.peaks)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.ravine)){
                return !BrowserCampaignResearch.waitingForRavineSlag();
            }
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.ravine)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.caldera)) return false;
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.caldera)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.stronghold)) return false;
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.stronghold)) return false;
            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.crevice)) return true;
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.crevice)) return false;
            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.siege)) return true;
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.siege)) return false;
            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.crossroads)) return true;
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.crossroads)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.karst)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextKarstResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.karst)) return false;

            if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.origin)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextOriginResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            return BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.origin);
        });
        erekirProgress.update(() -> {
            if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.onset)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.onset);
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextOnsetResearch();
                if(saved && next != null && BrowserCampaignResearch.canSpend(next)){
                    erekirProgress.setText(next.localizedName + " — " +
                        Core.bundle.get("research", "Research") + " " + BrowserCampaignResearch.remaining(next));
                }else{
                    erekirProgress.setText(Core.bundle.get("sector.onset.name", "Onset") + " — " +
                        Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
                }
            }else if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.aegis)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextAegisResearch();
                erekirProgress.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.aegis)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.aegis);
                erekirProgress.setText(Core.bundle.get("sector.aegis.name", "Aegis") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.lake)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.lake);
                erekirProgress.setText(Core.bundle.get("sector.lake.name", "Lake") + " — " +
                    (BrowserCampaignResearch.ready(mindustry.content.SectorPresets.lake)
                        ? Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play")
                        : Core.bundle.get("locked", "Locked")));
            }else if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.intersect)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextIntersectResearch();
                erekirProgress.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.intersect)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.intersect);
                erekirProgress.setText(Core.bundle.get("sector.intersect.name", "Intersect") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.atlas)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextAtlasResearch();
                erekirProgress.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.atlas)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.atlas);
                erekirProgress.setText(Core.bundle.get("sector.atlas.name", "Atlas") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.split)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextSplitResearch();
                erekirProgress.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.split)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.split);
                erekirProgress.setText(Core.bundle.get("sector.split.name", "Split") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.basin)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.basin);
                erekirProgress.setText(Core.bundle.get("sector.basin.name", "Basin") + " — " +
                    (BrowserCampaignResearch.ready(mindustry.content.SectorPresets.basin)
                        ? Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play")
                        : Core.bundle.get("locked", "Locked")));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.marsh)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.marsh);
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextMarshResearch();
                if(saved && next != null && BrowserCampaignResearch.canSpend(next)){
                    erekirProgress.setText(next.localizedName + " — " +
                        Core.bundle.get("research", "Research") + " " + BrowserCampaignResearch.remaining(next));
                }else if(saved && BrowserCampaignResearch.waitingForMarshProduction()){
                    mindustry.ctype.UnlockableContent produce = !mindustry.content.Items.oxide.unlocked()
                        ? mindustry.content.Items.oxide : mindustry.content.Liquids.arkycite;
                    erekirProgress.setText(produce.localizedName + " — " +
                        Core.bundle.get("produce", "Produce"));
                }else{
                    erekirProgress.setText(Core.bundle.get("sector.marsh.name", "Marsh") + " — " +
                        Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
                }
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.peaks)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.peaks);
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextPeaksResearch();
                if(saved && next != null && BrowserCampaignResearch.canSpend(next)){
                    erekirProgress.setText(next.localizedName + " — " +
                        Core.bundle.get("research", "Research") + " " + BrowserCampaignResearch.remaining(next));
                }else{
                    erekirProgress.setText(Core.bundle.get("sector.peaks.name", "Peaks") + " — " +
                        Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
                }
            }else if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.ravine)){
                erekirProgress.setText(mindustry.content.Liquids.slag.localizedName + " — " +
                    Core.bundle.get("produce", "Produce"));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.ravine)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.ravine);
                erekirProgress.setText(Core.bundle.get("sector.ravine.name", "Ravine") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.caldera)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextCalderaResearch();
                if(next != null && BrowserCampaignResearch.canSpend(next)){
                    erekirProgress.setText(next.localizedName + " — " +
                        Core.bundle.get("research", "Research") + " " + BrowserCampaignResearch.remaining(next));
                }else{
                    erekirProgress.setText(Core.bundle.get("sector.ravine.name", "Ravine") + " — " +
                        Core.bundle.get("continue", "Continue"));
                }
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.caldera)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.caldera);
                erekirProgress.setText(Core.bundle.get("sector.caldera-erekir.name", "Caldera") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.stronghold)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextStrongholdResearch();
                if(next != null && BrowserCampaignResearch.canSpend(next)){
                    erekirProgress.setText(next.localizedName + " — " +
                        Core.bundle.get("research", "Research") + " " + BrowserCampaignResearch.remaining(next));
                }else{
                    erekirProgress.setText(Core.bundle.get("sector.caldera-erekir.name", "Caldera") + " — " +
                        Core.bundle.get("continue", "Continue"));
                }
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.stronghold)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.stronghold);
                erekirProgress.setText(Core.bundle.get("sector.stronghold.name", "Stronghold") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.crevice)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.crevice);
                erekirProgress.setText(Core.bundle.get("sector.crevice.name", "Crevice") + " — " +
                    (BrowserCampaignResearch.ready(mindustry.content.SectorPresets.crevice)
                        ? Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play")
                        : Core.bundle.get("locked", "Locked")));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.siege)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.siege);
                erekirProgress.setText(Core.bundle.get("sector.siege.name", "Siege") + " — " +
                    (BrowserCampaignResearch.ready(mindustry.content.SectorPresets.siege)
                        ? Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play")
                        : Core.bundle.get("locked", "Locked")));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.crossroads)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.crossroads);
                erekirProgress.setText(Core.bundle.get("sector.crossroads.name", "Crossroads") + " — " +
                    (BrowserCampaignResearch.ready(mindustry.content.SectorPresets.crossroads)
                        ? Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play")
                        : Core.bundle.get("locked", "Locked")));
            }else if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.karst)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextKarstResearch();
                erekirProgress.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.karst)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.karst);
                erekirProgress.setText(Core.bundle.get("sector.karst.name", "Karst") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.origin)){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextOriginResearch();
                erekirProgress.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.origin)){
                boolean saved = BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.origin);
                erekirProgress.setText(Core.bundle.get("sector.origin.name", "Origin") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else{
                erekirProgress.setText(Core.bundle.get("planet.erekir.name", "Erekir") + " — " +
                    Core.bundle.get("complete", "Complete"));
            }

            if(BrowserCampaignRuntime.diagnosticsEnabled()){
                markCampaignErekirState(
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.onset),
                    BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.onset),
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.aegis),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.aegis),
                    BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.aegis),
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.lake),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.lake),
                    BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.lake),
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.intersect),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.intersect),
                    BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.intersect)
                );
                markCampaignErekirMidState(
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.atlas),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.atlas),
                    BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.atlas),
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.split),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.split),
                    BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.split),
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.basin),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.basin),
                    BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.basin)
                );
                markCampaignErekirBranchState(
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.marsh),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.marsh),
                    BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.marsh),
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.peaks),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.peaks),
                    BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.peaks),
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.ravine),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.ravine),
                    BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.ravine),
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.caldera),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.caldera),
                    BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.caldera)
                );
                markCampaignErekirLateState(
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.stronghold),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.stronghold),
                    BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.stronghold),
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.crevice),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.crevice),
                    BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.crevice),
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.siege),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.siege),
                    BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.siege),
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.crossroads),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.crossroads),
                    BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.crossroads)
                );
                markCampaignErekirFinalState(
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.karst),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.karst),
                    BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.karst),
                    BrowserCampaignResearch.ready(mindustry.content.SectorPresets.origin),
                    BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.origin),
                    BrowserCampaignRuntime.hasSave(mindustry.content.SectorPresets.origin)
                );
            }
        });
        root.add(erekirProgress).width(campaignWidth).height(mobile ? 54f : 44f).padBottom(8f);
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
            ? Math.max(56f, Math.min(110f, Core.graphics.getHeight() - 500f))
            : 330f;
        Cell<ScrollPane> mapPaneCell = root.add(pane).width(mobile ? 320f : 380f).height(mapPaneHeight);
        final float[] lastMapPaneHeight = {mapPaneHeight};
        if(mobile){
            pane.update(() -> {
                float nextHeight = Math.max(56f, Math.min(110f, Core.graphics.getHeight() - 500f));
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

    @JSBody(params = {"graphitePress", "siliconSmelter", "kiln", "mechanicalPump", "ruinousReady", "ruinousSaved"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-ruinous-ui','ready'); document.documentElement.setAttribute('data-mindustry-campaign-graphite-press-unlocked',graphitePress ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-silicon-smelter-unlocked',siliconSmelter ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-kiln-unlocked',kiln ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-mechanical-pump-unlocked',mechanicalPump ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-ruinous-shores-ready',ruinousReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-ruinous-shores-save',ruinousSaved ? 'true' : 'false');")
    private static native void markCampaignRuinousState(boolean graphitePress, boolean siliconSmelter, boolean kiln, boolean mechanicalPump, boolean ruinousReady, boolean ruinousSaved);

    @JSBody(params = {"pneumaticDrill", "duo", "scatter", "hail", "steamGenerator", "windsweptReady", "windsweptSaved"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-windswept-ui','ready'); document.documentElement.setAttribute('data-mindustry-campaign-pneumatic-drill-unlocked',pneumaticDrill ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-duo-unlocked',duo ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-scatter-unlocked',scatter ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-hail-unlocked',hail ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-steam-generator-unlocked',steamGenerator ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-windswept-islands-ready',windsweptReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-windswept-islands-save',windsweptSaved ? 'true' : 'false');")
    private static native void markCampaignWindsweptState(boolean pneumaticDrill, boolean duo, boolean scatter, boolean hail, boolean steamGenerator, boolean windsweptReady, boolean windsweptSaved);

    @JSBody(params = {"biomassReady", "biomassCaptured", "fungalReady", "fungalCaptured", "frontierReady", "frontierCaptured", "saltReady", "saltCaptured", "tarReady", "tarSaved"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-salt-ui','ready'); document.documentElement.setAttribute('data-mindustry-campaign-biomass-facility-ready',biomassReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-biomass-facility-captured',biomassCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-fungal-pass-ready',fungalReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-fungal-pass-captured',fungalCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-frontier-ready',frontierReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-frontier-captured',frontierCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-salt-flats-ready',saltReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-salt-flats-captured',saltCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-tar-fields-ready',tarReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-tar-fields-save',tarSaved ? 'true' : 'false');")
    private static native void markCampaignSaltBranchState(boolean biomassReady, boolean biomassCaptured, boolean fungalReady, boolean fungalCaptured, boolean frontierReady, boolean frontierCaptured, boolean saltReady, boolean saltCaptured, boolean tarReady, boolean tarSaved);

    @JSBody(params = {"laserDrill", "thorium", "lancer", "salvo", "foundation", "impactReady", "impactSaved"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-impact-ui','ready'); document.documentElement.setAttribute('data-mindustry-campaign-laser-drill-unlocked',laserDrill ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-thorium-unlocked',thorium ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-lancer-unlocked',lancer ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-salvo-unlocked',salvo ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-core-foundation-unlocked',foundation ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-impact-0078-ready',impactReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-impact-0078-save',impactSaved ? 'true' : 'false');")
    private static native void markCampaignImpactState(boolean laserDrill, boolean thorium, boolean lancer, boolean salvo, boolean foundation, boolean impactReady, boolean impactSaved);

    @JSBody(params = {"stainedReady", "stainedCaptured", "infestedReady", "infestedCaptured", "nuclearReady", "nuclearCaptured", "desolateReady", "desolateSaved"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-late-ui','ready'); document.documentElement.setAttribute('data-mindustry-campaign-stained-mountains-ready',stainedReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-stained-mountains-captured',stainedCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-infested-canyons-ready',infestedReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-infested-canyons-captured',infestedCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-nuclear-complex-ready',nuclearReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-nuclear-complex-captured',nuclearCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-desolate-rift-ready',desolateReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-desolate-rift-save',desolateSaved ? 'true' : 'false');")
    private static native void markCampaignLateState(boolean stainedReady, boolean stainedCaptured, boolean infestedReady, boolean infestedCaptured, boolean nuclearReady, boolean nuclearCaptured, boolean desolateReady, boolean desolateSaved);

    @JSBody(params = {"facilityReady", "facilityCaptured", "perilousReady", "perilousCaptured", "extractionReady", "extractionCaptured", "coastlineReady", "coastlineCaptured", "navalReady", "navalSaved"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-final-infra-ui','ready'); document.documentElement.setAttribute('data-mindustry-campaign-facility32m-ready',facilityReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-facility32m-captured',facilityCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-perilous-harbor-ready',perilousReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-perilous-harbor-captured',perilousCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-extraction-outpost-ready',extractionReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-extraction-outpost-captured',extractionCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-coastline-ready',coastlineReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-coastline-captured',coastlineCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-naval-fortress-ready',navalReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-naval-fortress-save',navalSaved ? 'true' : 'false');")
    private static native void markCampaignFinalInfraState(boolean facilityReady, boolean facilityCaptured, boolean perilousReady, boolean perilousCaptured, boolean extractionReady, boolean extractionCaptured, boolean coastlineReady, boolean coastlineCaptured, boolean navalReady, boolean navalSaved);

    @JSBody(params = {"overgrowthReady", "overgrowthCaptured", "mycelialReady", "mycelialCaptured", "littoralReady", "littoralCaptured", "terminalReady", "terminalSaved"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-terminal-ui','ready'); document.documentElement.setAttribute('data-mindustry-campaign-overgrowth-ready',overgrowthReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-overgrowth-captured',overgrowthCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-mycelial-bastion-ready',mycelialReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-mycelial-bastion-captured',mycelialCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-littoral-shipyard-ready',littoralReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-littoral-shipyard-captured',littoralCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-planetary-terminal-ready',terminalReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-planetary-terminal-save',terminalSaved ? 'true' : 'false');")
    private static native void markCampaignTerminalState(boolean overgrowthReady, boolean overgrowthCaptured, boolean mycelialReady, boolean mycelialCaptured, boolean littoralReady, boolean littoralCaptured, boolean terminalReady, boolean terminalSaved);

    @JSBody(params = {"sporePod", "taintedReady", "taintedCaptured", "atollsReady", "atollsCaptured", "testingReady", "testingCaptured", "sunkenReady", "sunkenCaptured", "weatheredReady", "weatheredCaptured"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-optional-ui','ready'); document.documentElement.setAttribute('data-mindustry-campaign-spore-pod-unlocked',sporePod ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-tainted-woods-ready',taintedReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-tainted-woods-captured',taintedCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-atolls-ready',atollsReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-atolls-captured',atollsCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-testing-grounds-ready',testingReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-testing-grounds-captured',testingCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-sunken-pier-ready',sunkenReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-sunken-pier-captured',sunkenCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-weathered-channels-ready',weatheredReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-campaign-weathered-channels-captured',weatheredCaptured ? 'true' : 'false');")
    private static native void markCampaignOptionalState(boolean sporePod, boolean taintedReady, boolean taintedCaptured, boolean atollsReady, boolean atollsCaptured, boolean testingReady, boolean testingCaptured, boolean sunkenReady, boolean sunkenCaptured, boolean weatheredReady, boolean weatheredCaptured);

    @JSBody(params = {"onsetCaptured", "onsetSaved", "aegisReady", "aegisCaptured", "aegisSaved", "lakeReady", "lakeCaptured", "lakeSaved", "intersectReady", "intersectCaptured", "intersectSaved"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-erekir-ui','ready'); document.documentElement.setAttribute('data-mindustry-erekir-ui-onset-captured',onsetCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-onset-save',onsetSaved ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-aegis-ready',aegisReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-aegis-captured',aegisCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-aegis-save',aegisSaved ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-lake-ready',lakeReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-lake-captured',lakeCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-lake-save',lakeSaved ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-intersect-ready',intersectReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-intersect-captured',intersectCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-intersect-save',intersectSaved ? 'true' : 'false');")
    private static native void markCampaignErekirState(boolean onsetCaptured, boolean onsetSaved, boolean aegisReady, boolean aegisCaptured, boolean aegisSaved, boolean lakeReady, boolean lakeCaptured, boolean lakeSaved, boolean intersectReady, boolean intersectCaptured, boolean intersectSaved);

    @JSBody(params = {"atlasReady", "atlasCaptured", "atlasSaved", "splitReady", "splitCaptured", "splitSaved", "basinReady", "basinCaptured", "basinSaved"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-erekir-mid-ui','ready'); document.documentElement.setAttribute('data-mindustry-erekir-ui-atlas-ready',atlasReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-atlas-captured',atlasCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-atlas-save',atlasSaved ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-split-ready',splitReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-split-captured',splitCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-split-save',splitSaved ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-basin-ready',basinReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-basin-captured',basinCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-basin-save',basinSaved ? 'true' : 'false');")
    private static native void markCampaignErekirMidState(boolean atlasReady, boolean atlasCaptured, boolean atlasSaved, boolean splitReady, boolean splitCaptured, boolean splitSaved, boolean basinReady, boolean basinCaptured, boolean basinSaved);

    @JSBody(params = {"marshReady", "marshCaptured", "marshSaved", "peaksReady", "peaksCaptured", "peaksSaved", "ravineReady", "ravineCaptured", "ravineSaved", "calderaReady", "calderaCaptured", "calderaSaved"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-erekir-branch-ui','ready'); document.documentElement.setAttribute('data-mindustry-erekir-ui-marsh-ready',marshReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-marsh-captured',marshCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-marsh-save',marshSaved ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-peaks-ready',peaksReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-peaks-captured',peaksCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-peaks-save',peaksSaved ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-ravine-ready',ravineReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-ravine-captured',ravineCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-ravine-save',ravineSaved ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-caldera-ready',calderaReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-caldera-captured',calderaCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-caldera-save',calderaSaved ? 'true' : 'false');")
    private static native void markCampaignErekirBranchState(boolean marshReady, boolean marshCaptured, boolean marshSaved, boolean peaksReady, boolean peaksCaptured, boolean peaksSaved, boolean ravineReady, boolean ravineCaptured, boolean ravineSaved, boolean calderaReady, boolean calderaCaptured, boolean calderaSaved);

    @JSBody(params = {"strongholdReady", "strongholdCaptured", "strongholdSaved", "creviceReady", "creviceCaptured", "creviceSaved", "siegeReady", "siegeCaptured", "siegeSaved", "crossroadsReady", "crossroadsCaptured", "crossroadsSaved"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-erekir-late-ui','ready'); document.documentElement.setAttribute('data-mindustry-erekir-ui-stronghold-ready',strongholdReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-stronghold-captured',strongholdCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-stronghold-save',strongholdSaved ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-crevice-ready',creviceReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-crevice-captured',creviceCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-crevice-save',creviceSaved ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-siege-ready',siegeReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-siege-captured',siegeCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-siege-save',siegeSaved ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-crossroads-ready',crossroadsReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-crossroads-captured',crossroadsCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-crossroads-save',crossroadsSaved ? 'true' : 'false');")
    private static native void markCampaignErekirLateState(boolean strongholdReady, boolean strongholdCaptured, boolean strongholdSaved, boolean creviceReady, boolean creviceCaptured, boolean creviceSaved, boolean siegeReady, boolean siegeCaptured, boolean siegeSaved, boolean crossroadsReady, boolean crossroadsCaptured, boolean crossroadsSaved);

    @JSBody(params = {"karstReady", "karstCaptured", "karstSaved", "originReady", "originCaptured", "originSaved"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-erekir-final-ui','ready'); document.documentElement.setAttribute('data-mindustry-erekir-ui-karst-ready',karstReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-karst-captured',karstCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-karst-save',karstSaved ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-origin-ready',originReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-origin-captured',originCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-origin-save',originSaved ? 'true' : 'false');")
    private static native void markCampaignErekirFinalState(boolean karstReady, boolean karstCaptured, boolean karstSaved, boolean originReady, boolean originCaptured, boolean originSaved);

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
