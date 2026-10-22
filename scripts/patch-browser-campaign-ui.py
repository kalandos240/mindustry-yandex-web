#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserUiRuntime.java"

if not UI.is_file():
    raise SystemExit(f"Missing browser UI runtime: {UI}")

text = UI.read_text(encoding="utf-8")

old_menu = '''        root.add(Core.bundle.get("customgame", "Custom Game")).padBottom(8f);
        root.row();
        localContinueButton = new TextButton(Core.bundle.get("continue", "Continue"));
        localContinueButton.clicked(BrowserLocalMapRuntime::continueSaved);
        localContinueButton.setDisabled(!BrowserSaveRuntime.hasLocalSession());
        root.add(localContinueButton)
            .width(mobile ? 320f : 380f)
            .height(mobile ? 54f : 46f)
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
        boolean[] campaignContinue = {Campaign.hasSave(mindustry.content.SectorPresets.groundZero)};
        campaignButton.setText(Core.bundle.get(campaignContinue[0] ? "continue" : "play",
            campaignContinue[0] ? "Continue" : "Play"));
        // Production cold-boot verification needs to observe the actual menu state
        // without enabling any smoke/diagnostic query. This marker changes only when the
        // button state changes, so it is not part of the frame hot path.
        markCampaignUiAction(campaignContinue[0] ? "continue" : "play");
        campaignButton.clicked(Campaign::playGroundZero);
                root.add(campaignButton).width(campaignWidth).height(campaignHeight).padBottom(8f);
        root.row();

        // Early Serpulo progression uses the exact stock TechNode requirements/objectives,
        // but presents them in a compact Yandex-friendly surface instead of constructing
        // the heavyweight desktop ResearchDialog tree.
        BrowserCampaignResearch.refreshUnlocks();
        final int[] campaignUiRefreshFrame = {7};
        Table campaignProgress = new Table();
                campaignProgress.defaults().pad(2f);
        campaignProgress.add(Core.bundle.get("research", "Research")).colspan(2).padBottom(2f);
        campaignProgress.row();

        TextButton conveyorResearch = new TextButton("");
        conveyorResearch.clicked(() -> BrowserCampaignResearch.spend(mindustry.content.Blocks.conveyor));
        arc.func.Boolp conveyorResearchDisabled = () -> mindustry.content.Blocks.conveyor.unlocked()
            || !BrowserCampaignResearch.canSpend(mindustry.content.Blocks.conveyor);
        conveyorResearch.setDisabled(conveyorResearchDisabled.get());
                campaignProgress.add(conveyorResearch).colspan(2).width(campaignWidth).height(mobile ? 48f : 40f);
        campaignProgress.row();

        TextButton junctionResearch = new TextButton("");
        junctionResearch.clicked(() -> BrowserCampaignResearch.spend(mindustry.content.Blocks.junction));
        arc.func.Boolp junctionResearchDisabled = () -> mindustry.content.Blocks.junction.unlocked()
            || !BrowserCampaignResearch.canSpend(mindustry.content.Blocks.junction);
        junctionResearch.setDisabled(junctionResearchDisabled.get());
                campaignProgress.add(junctionResearch).width(campaignWidth / 2f - 3f).height(mobile ? 50f : 42f);

        TextButton routerResearch = new TextButton("");
        routerResearch.clicked(() -> BrowserCampaignResearch.spend(mindustry.content.Blocks.router));
        arc.func.Boolp routerResearchDisabled = () -> mindustry.content.Blocks.router.unlocked()
            || !BrowserCampaignResearch.canSpend(mindustry.content.Blocks.router);
        routerResearch.setDisabled(routerResearchDisabled.get());
                campaignProgress.add(routerResearch).width(campaignWidth / 2f - 3f).height(mobile ? 50f : 42f);
        campaignProgress.row();

        TextButton frozenForestButton = new TextButton("");
        frozenForestButton.clicked(Campaign::playFrozenForest);
        arc.func.Boolp frozenForestButtonDisabled = () ->
            !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.frozenForest);
        frozenForestButton.setDisabled(frozenForestButtonDisabled.get());
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
                Campaign.playRuinousShores();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.ruinousShores)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.windsweptIslands)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextWindsweptResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.windsweptIslands)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.windsweptIslands)){
                Campaign.playWindsweptIslands();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.windsweptIslands)
            && BrowserCampaignResearch.ready(mindustry.content.SectorPresets.biomassFacility)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.biomassFacility)){
                Campaign.playBiomassFacility();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.biomassFacility)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.fungalPass)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextFungalResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.fungalPass)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.fungalPass)){
                Campaign.playFungalPass();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.fungalPass)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.frontier)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextFrontierResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.frontier)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.frontier)){
                Campaign.playFrontier();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.frontier)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.saltFlats)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextSaltResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.saltFlats)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.saltFlats)){
                Campaign.playSaltFlats();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.saltFlats)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.tarFields)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextTarResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.tarFields)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.tarFields)){
                Campaign.playTarFields();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.tarFields)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.impact0078)){
                BrowserCampaignResearch.spendNextImpactResearch();
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.impact0078)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.impact0078)){
                Campaign.playImpact0078();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.impact0078)
            && BrowserCampaignResearch.ready(mindustry.content.SectorPresets.stainedMountains)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.stainedMountains)){
                Campaign.playStainedMountains();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.stainedMountains)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.infestedCanyons)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextInfestedResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.infestedCanyons)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.infestedCanyons)){
                Campaign.playInfestedCanyons();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.infestedCanyons)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.nuclearComplex)){
                BrowserCampaignResearch.spendNextNuclearResearch();
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.nuclearComplex)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.nuclearComplex)){
                Campaign.playNuclearComplex();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.nuclearComplex)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.desolateRift)){
                BrowserCampaignResearch.spendNextDesolateResearch();
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.desolateRift)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.desolateRift)){
                Campaign.playDesolateRift();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.desolateRift)
            && BrowserCampaignResearch.ready(mindustry.content.SectorPresets.facility32m)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.facility32m)){
                Campaign.playFacility32m();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.facility32m)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.perilousHarbor)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextPerilousResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.perilousHarbor)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.perilousHarbor)){
                Campaign.playPerilousHarbor();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.perilousHarbor)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.extractionOutpost)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextExtractionResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.extractionOutpost)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.extractionOutpost)){
                Campaign.playExtractionOutpost();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.extractionOutpost)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.coastline)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextCoastlineResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.coastline)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.coastline)){
                Campaign.playCoastline();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.coastline)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.navalFortress)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextNavalFortressResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.navalFortress)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.navalFortress)){
                Campaign.playNavalFortress();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.navalFortress)
            && BrowserCampaignResearch.ready(mindustry.content.SectorPresets.overgrowth)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.overgrowth)){
                Campaign.playOvergrowth();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.overgrowth)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.mycelialBastion)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextMycelialResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.mycelialBastion)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.mycelialBastion)){
                Campaign.playMycelialBastion();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.mycelialBastion)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.littoralShipyard)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextLittoralResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.littoralShipyard)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.littoralShipyard)){
                Campaign.playLittoralShipyard();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.littoralShipyard)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.planetaryTerminal)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextTerminalResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.planetaryTerminal)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.planetaryTerminal)){
                Campaign.playPlanetaryTerminal();
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.taintedWoods)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.taintedWoods)){
                Campaign.playTaintedWoods();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.taintedWoods)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.atolls)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextAtollsResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.atolls)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.atolls)){
                Campaign.playAtolls();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.atolls)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.testingGrounds)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextTestingGroundsResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.testingGrounds)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.testingGrounds)){
                Campaign.playTestingGrounds();
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.sunkenPier)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.sunkenPier)){
                Campaign.playSunkenPier();
            }else if(BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.sunkenPier)
            && !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.weatheredChannels)){
                BrowserCampaignResearch.spendNext(BrowserCampaignResearch.nextWeatheredResearch());
            }else if(BrowserCampaignResearch.ready(mindustry.content.SectorPresets.weatheredChannels)
            && !BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.weatheredChannels)){
                Campaign.playWeatheredChannels();
            }
        });
        arc.func.Boolp craterResearchDisabled = () -> {
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
        };
        craterResearch.setDisabled(craterResearchDisabled.get());
        
        TextButton craterButton = new TextButton("");
        craterButton.clicked(Campaign::playCrateredBattleground);
        arc.func.Boolp craterButtonDisabled = () -> !BrowserCampaignResearch.ready(mindustry.content.SectorPresets.crateredBattleground);
        craterButton.setDisabled(craterButtonDisabled.get());
        
        // Web: one throttled updater for all Serpulo/general campaign labels.
        // This replaces per-Actor update callbacks while preserving the same state logic.
        // MenuFragment-like Web home hides the heavy sector selector until
        // Play is clicked. Keep campaign metadata/Continue state up-to-date
        // in a visible, zero-size, non-interactive actor attached directly to
        // the menu root; hidden Play child actors are not acted by Arc Scene.
        Table campaignUiTick = new Table();
        campaignUiTick.touchable = Touchable.disabled;
        ui.menuGroup.addChild(campaignUiTick);
        campaignUiTick.update(() -> {
            if(state == null || !state.isMenu()) return;
            int frame = ++campaignUiRefreshFrame[0];
            if((frame & 7) != 0) return;
            if((frame & 31) == 0){
                BrowserCampaignResearch.refreshUnlocks();
            }

            conveyorResearch.setDisabled(conveyorResearchDisabled.get());
            junctionResearch.setDisabled(junctionResearchDisabled.get());
            routerResearch.setDisabled(routerResearchDisabled.get());
            frozenForestButton.setDisabled(frozenForestButtonDisabled.get());
            craterResearch.setDisabled(craterResearchDisabled.get());
            craterButton.setDisabled(craterButtonDisabled.get());

            // campaignButton
            boolean hasSave = Campaign.hasSave(mindustry.content.SectorPresets.groundZero);
                        if(hasSave != campaignContinue[0]){
                            campaignContinue[0] = hasSave;
                            campaignButton.setText(Core.bundle.get(hasSave ? "continue" : "play",
                                hasSave ? "Continue" : "Play"));
                            markCampaignUiAction(hasSave ? "continue" : "play");
                        }

            // conveyorResearch
            conveyorResearch.setText(
                            mindustry.content.Blocks.conveyor.localizedName + " — " +
                            (mindustry.content.Blocks.conveyor.unlocked()
                                ? Core.bundle.get("unlocked", "Unlocked")
                                : Core.bundle.get("research", "Research") + " " +
                                    BrowserCampaignResearch.remaining(mindustry.content.Blocks.conveyor))
                        );

            // junctionResearch
            junctionResearch.setText(
                            mindustry.content.Blocks.junction.localizedName + " — " +
                            (mindustry.content.Blocks.junction.unlocked()
                                ? Core.bundle.get("unlocked", "Unlocked")
                                : Core.bundle.get("research", "Research") + " " +
                                    BrowserCampaignResearch.remaining(mindustry.content.Blocks.junction))
                        );

            // routerResearch
            routerResearch.setText(
                            mindustry.content.Blocks.router.localizedName + " — " +
                            (mindustry.content.Blocks.router.unlocked()
                                ? Core.bundle.get("unlocked", "Unlocked")
                                : Core.bundle.get("research", "Research") + " " +
                                    BrowserCampaignResearch.remaining(mindustry.content.Blocks.router))
                        );

            // frozenForestButton
            boolean frozenReady = BrowserCampaignResearch.ready(mindustry.content.SectorPresets.frozenForest);
                        boolean frozenSaved = Campaign.hasSave(mindustry.content.SectorPresets.frozenForest);
                        frozenForestButton.setText(Core.bundle.get("sector.frozenForest.name", "Frozen Forest") + " — " +
                            (frozenReady
                                ? Core.bundle.get(frozenSaved ? "continue" : "play", frozenSaved ? "Continue" : "Play")
                                : Core.bundle.get("locked", "Locked")));
                        if(Campaign.diagnosticsEnabled()){
                            markCampaignProgressState(
                                mindustry.content.Blocks.conveyor.unlocked(),
                                mindustry.content.Blocks.junction.unlocked(),
                                mindustry.content.Blocks.router.unlocked(),
                                frozenReady,
                                frozenSaved
                            );
                        }

            // craterResearch
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
                            boolean saved = Campaign.hasSave(mindustry.content.SectorPresets.ruinousShores);
                            craterResearch.setText(Core.bundle.get("sector.ruinousShores.name", "Ruinous Shores") + " — " +
                                Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
                        }else if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.windsweptIslands)){
                            mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextWindsweptResearch();
                            craterResearch.setText(next == null
                                ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                                : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                                    BrowserCampaignResearch.remaining(next));
                        }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.windsweptIslands)){
                            boolean saved = Campaign.hasSave(mindustry.content.SectorPresets.windsweptIslands);
                            craterResearch.setText(Core.bundle.get("sector.windsweptIslands.name", "Windswept Islands") + " — " +
                                Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
                        }else if(!BrowserCampaignResearch.isCaptured(mindustry.content.SectorPresets.biomassFacility)){
                            boolean ready = BrowserCampaignResearch.ready(mindustry.content.SectorPresets.biomassFacility);
                            boolean saved = Campaign.hasSave(mindustry.content.SectorPresets.biomassFacility);
                            craterResearch.setText(Core.bundle.get("sector.biomassFacility.name", "Biomass Synthesis Facility") + " — " +
                                (ready ? Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play")
                                    : Core.bundle.get("locked", "Locked")));
                        }else if(!BrowserCampaignResearch.ready(mindustry.content.SectorPresets.fungalPass)){
                            mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextFungalResearch();
                            craterResearch.setText(next == null
                                ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                                : next.localizedName + " — " + Core.bundle.get("r…65650 tokens truncated…e update callbacks. Observe both
        // transitions in the visibility provider, including modal dismissal.
        overlay.visible(() -> {
            boolean show = (BrowserLocalMapRuntime.active() || Campaign.active())
                && state.isPaused() && !hudOnlyPause && !state.gameOver && !Core.scene.hasDialog();
            if(show != wasVisible[0]){
                wasVisible[0] = show;
                if(show){
                    // A modal pause menu must also cover the native mobile
                    // command controls, which normally sit above other HUD actors.
                    overlay.toFront();
                    scroll.setScrollY(0f);
                    scroll.updateVisualScroll();
                }else{
                    markPauseHidden();
                }
            }
            return show;
        });
        String[] paired = {"resume", "settings", "save", "research", "schematics", "controls"};
        String[] wide = {"saves", "menu"};

        // The stock menu is not active while playing, so its visibility
        // sampler cannot observe Settings/Controls/Saves shown from Pause.
        // Update native Arc hitboxes and the real dialog visibility here,
        // at one sample per 16 Scene frames rather than every frame.
        int[] sampling = {0};
        overlay.update(() -> {
            float sw = Core.scene.getWidth(), sh = Core.scene.getHeight();
            if(viewport[0] != sw || viewport[1] != sh){
                viewport[0] = sw;
                viewport[1] = sh;
                float buttonWidth = Math.min(width, Math.max(70f, (sw - 70f) / 2f));
                for(String action : paired){
                    TextButton button = dialog.find("web-pause-" + action);
                    float fittedHeight = fitPauseCaption(button, buttonWidth, height);
                    dialog.getCell(button).width(buttonWidth).height(fittedHeight);
                }
                for(String action : wide){
                    TextButton button = dialog.find("web-pause-" + action);
                    button.getLabel().setWrap(true);
                    button.getLabelCell().minWidth(0f);
                    dialog.getCell(button).width(buttonWidth * 2f + 10f);
                }
                dialog.invalidateHierarchy();
                float contentHeight = dialog.getPrefHeight();
                float barWidth = contentHeight > sh - 10f
                    ? Math.max(scroll.getStyle().vScroll.getMinWidth(), scroll.getStyle().vScrollKnob.getMinWidth()) : 0f;
                scrollCell.width(Math.min(sw - 10f, dialog.getPrefWidth() + barWidth))
                    .height(Math.min(sh - 10f, contentHeight));
                overlay.invalidateHierarchy();
            }
            if((++sampling[0] & 15) != 0) return;
            markPauseHitPoint(dialog.find("web-pause-resume"), "resume");
            markPauseHitPoint(dialog.find("web-pause-settings"), "settings");
            markPauseHitPoint(dialog.find("web-pause-save"), "save");
            if(Campaign.active() && state.isCampaign()){
                markPauseHitPoint(dialog.find("web-pause-research"), "research");
            }
            markPauseHitPoint(dialog.find("web-pause-schematics"), "schematics");
            markPauseHitPoint(dialog.find("web-pause-controls"), "controls");
            markPauseHitPoint(dialog.find("web-pause-saves"), "saves");
            markPauseHitPoint(dialog.find("web-pause-menu"), "menu");
            BrowserVanillaDialogs.sampleVisibility();
            markPauseVisible();
        });
        ui.hudGroup.addChild(overlay);
        markPauseUiReady();
        markPauseSettingsUiReady();
        markStockPauseUiReady(mobile ? "mobile" : "desktop");
    }

    private static void markPauseHitPoint(Element button, String action){
        if(button == null || !button.visible || Core.scene == null
            || button.getWidth() <= 1f || button.getHeight() <= 1f) return;
        Vec2 center = button.localToStageCoordinates(
            new Vec2(button.getWidth() / 2f, button.getHeight() / 2f));
        Vec2 screen = Core.scene.getViewport().project(center);
        markPauseHitPointNative(action, screen.x, screen.y);
        if(captionDiagnostics && button instanceof TextButton textButton){
            textButton.validate();
            Label label = textButton.getLabel();
            label.validate();
            int lines = 0;
            float lastY = 0f;
            for(var run : label.getGlyphLayout().runs){
                if(lines == 0 || Math.abs(run.y - lastY) > 0.1f){ lines++; lastY = run.y; }
            }
            Vec2 lower = label.localToAscendantCoordinates(button, new Vec2());
            Vec2 upper = label.localToAscendantCoordinates(button, new Vec2(label.getWidth(), label.getHeight()));
            boolean contained = lower.x >= -0.5f && lower.y >= -0.5f
                && upper.x <= button.getWidth() + 0.5f && upper.y <= button.getHeight() + 0.5f
                && label.getGlyphLayout().height <= label.getHeight() + 0.5f
                && label.getGlyphLayout().width <= label.getWidth() + 0.5f;
            markPauseCaption(action, label.getText().toString(), lines, contained);
        }
    }

    @JSBody(script = "return new URLSearchParams(location.search).has('mindustryCaptionSmoke');")
    private static native boolean captionSmokeRequested();

    @JSBody(params = {"action", "caption", "lines", "contained"}, script = """
        const r=document.documentElement, p='data-mindustry-pause-caption-'+action;
        r.setAttribute(p, JSON.stringify({text:caption,lines:lines,contained:contained}));
        """)
    private static native void markPauseCaption(String action, String caption, int lines, boolean contained);

    @JSBody(params = {"action", "x", "y"}, script = """
        const r = document.documentElement;
        r.setAttribute('data-mindustry-pause-button-' + action + '-x', String(x));
        r.setAttribute('data-mindustry-pause-button-' + action + '-y', String(y));
        """)
    private static native void markPauseHitPointNative(String action, float x, float y);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-pause-dialog-visible','yes');")
    private static native void markPauseVisible();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-pause-dialog-visible','no');")
    private static native void markPauseHidden();

    /**
     * The original game-over screen has a dedicated, modal action instead of
     * loose, unframed text floating above the running level.
     */
    private static void buildLocalGameOverOverlay(){
        Table overlay = new Table();
        overlay.setFillParent(true);
        overlay.setBackground(Styles.black6);
        overlay.touchable = Touchable.enabled;
        overlay.visible(() -> (BrowserLocalMapRuntime.active() || Campaign.active())
            && state.isGame() && state.gameOver);

        Table dialog = new Table(Tex.pane2);
        dialog.name = "web-gameover-dialog";
        dialog.defaults().pad(8f);
        dialog.image(Icon.cancel).size(42f).padTop(12f).row();
        dialog.label(() -> state.isCampaign()
            ? Core.bundle.format("sector.lost", state.getSector().name())
            : Core.bundle.get("gameover", "Game Over"))
            .style(Styles.outlineLabel).wrap().width(mobile ? 240f : 300f).padBottom(12f).row();
        dialog.label(() -> Core.bundle.get("stats.wave") + ": " + state.stats.wavesLasted).row();
        dialog.button(Core.bundle.get("back", "Back"), Icon.exit,
            BrowserUiRuntime::returnToMenuWithAd)
            .size(mobile ? 240f : 300f, mobile ? 58f : 52f)
            .name("web-gameover-back").padBottom(12f);
        overlay.add(dialog).center();
        int[] sample = {0};
        overlay.update(() -> {
            overlay.toFront();
            if((++sample[0] & 15) != 0) return;
            TextButton back = dialog.find("web-gameover-back");
            Vec2 point = Core.scene.getViewport().project(back.localToStageCoordinates(
                new Vec2(back.getWidth() / 2f, back.getHeight() / 2f)));
            markGameOverHit(point.x, point.y);
        });
        ui.hudGroup.addChild(overlay);
        markGameOverUiReady();
    }

    @JSBody(params={"x", "y"}, script="document.documentElement.setAttribute('data-mindustry-defeat-visible','yes'); document.documentElement.setAttribute('data-mindustry-defeat-back-x',String(x)); document.documentElement.setAttribute('data-mindustry-defeat-back-y',String(y));")
    private static native void markGameOverHit(float x, float y);

    public static boolean initialized(){
        return initialized;
    }

    public static void syncLocalSaveUiState(){
        if(!initialized) return;
        boolean available = BrowserSaveRuntime.hasLocalSession();
        if(localContinueButton != null) localContinueButton.setDisabled(!available);
        markLocalSaveUiReady(available ? "available" : "empty");
    }

    public static void syncLocalModeUi(){
        if(!initialized) return;
        setLocalModeUi(BrowserLocalMapRuntime.customModeCode());
        if(localModeButton != null) localModeButton.setText(localModeLabel());
    }

    @JSBody(params={"x","y"}, script="document.documentElement.setAttribute('data-mindustry-custom-native-x',String(x));document.documentElement.setAttribute('data-mindustry-custom-native-y',String(y));")
    private static native void markNativeCustomHitPoint(float x, float y);

    private static String localModeLabel(){
        return Core.bundle.get(BrowserLocalMapRuntime.customModeCode() == 1
            ? "mode.sandbox.name" : "mode.survival.name");
    }

    @JSBody(params = {"source"}, script = "const r=document.documentElement;r.setAttribute('data-mindustry-hud-commands','stock-PlacementFragment');r.setAttribute('data-mindustry-hud-command-toggle',source);r.setAttribute('data-mindustry-hud-command-mode','off');")
    private static native void markCommandToggleReady(String source);

    @JSBody(params = {"mode"}, script = "document.documentElement.setAttribute('data-mindustry-hud-command-input-mode',mode);")
    private static native void markCommandInputMode(String mode);

    @JSBody(params = {"mode"}, script = "document.documentElement.setAttribute('data-mindustry-hud-command-mode',mode);")
    private static native void markCommandToggleState(String mode);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-ui', 'ready'); document.documentElement.setAttribute('data-mindustry-input-ui', 'bound'); document.documentElement.setAttribute('data-mindustry-input-ui-fragments', 'deferred');")
    private static native void markReady();

    @JSBody(params = {"sfx", "music", "effects", "quality"}, script = "globalThis.__mindustryInstallSettings(null,sfx,music,effects,quality);")
    private static native void installSettingsUi(int sfx, int music, boolean effects, boolean quality);

    @JSBody(params = {"mode"}, script = "globalThis.__mindustryInstallLocalMode(null,mode);")
    private static native void installLocalModeUi(int mode);

    @JSBody(params = {"mode"}, script = "if(globalThis.__mindustrySetLocalMode){ globalThis.__mindustrySetLocalMode(mode); }")
    private static native void setLocalModeUi(int mode);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-map-ui', 'ready'); document.documentElement.setAttribute('data-mindustry-local-map-menu', 'builtin-selector'); document.documentElement.setAttribute('data-mindustry-local-map-back', 'ready');")
    private static native void markLocalMapUiReady();

    @JSBody(params = {"slot"}, script = "document.documentElement.setAttribute('data-mindustry-local-save-ui', 'ready'); document.documentElement.setAttribute('data-mindustry-local-continue-ui', 'ready'); document.documentElement.setAttribute('data-mindustry-local-continue-slot', slot);")
    private static native void markLocalSaveUiReady(String slot);

    @JSBody(script = """
        const launch=document.getElementById('mindustry-settings-toggle');
        if(launch && launch.style.display !== 'none') launch.click();
        """)
    static native void openMenuSettings();

    @JSBody(script = "if(globalThis.__mindustryOpenPauseSettings){globalThis.__mindustryOpenPauseSettings();}")
    private static native void openPauseSettings();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-pause-settings-entry','ready');")
    private static native void markPauseSettingsUiReady();

    @JSBody(params={"mode"}, script="document.documentElement.setAttribute('data-mindustry-stock-pause-dialog',mode);")
    private static native void markStockPauseUiReady(String mode);

    @JSBody(params = {"x", "y"}, script = """
        const root = document.documentElement;
        root.setAttribute('data-mindustry-hud-research-x', String(x));
        root.setAttribute('data-mindustry-hud-research-y', String(y));
        """)
    private static native void markHudResearchHitPoint(float x, float y);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-pause-ui', 'ready');")
    private static native void markPauseUiReady();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-gameover-ui', 'ready');")
    private static native void markGameOverUiReady();
}
