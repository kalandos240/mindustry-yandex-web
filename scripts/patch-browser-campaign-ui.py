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
        craterResearch.clicked(() -> {
            if(!BrowserCampaignResearch.crateredBattlegroundReady()){
                BrowserCampaignResearch.spendNextCraterResearch();
            }else if(BrowserCampaignResearch.crateredBattlegroundCaptured()
            && !BrowserCampaignResearch.ruinousShoresReady()){
                BrowserCampaignResearch.spendNextRuinousResearch();
            }else if(BrowserCampaignResearch.ruinousShoresReady()
            && !BrowserCampaignResearch.ruinousShoresCaptured()){
                BrowserCampaignRuntime.playRuinousShores();
            }else if(BrowserCampaignResearch.ruinousShoresCaptured()
            && !BrowserCampaignResearch.windsweptIslandsReady()){
                BrowserCampaignResearch.spendNextWindsweptResearch();
            }else if(BrowserCampaignResearch.windsweptIslandsReady()
            && !BrowserCampaignResearch.windsweptIslandsCaptured()){
                BrowserCampaignRuntime.playWindsweptIslands();
            }else if(BrowserCampaignResearch.windsweptIslandsCaptured()
            && BrowserCampaignResearch.biomassFacilityReady()
            && !BrowserCampaignResearch.biomassFacilityCaptured()){
                BrowserCampaignRuntime.playBiomassFacility();
            }else if(BrowserCampaignResearch.biomassFacilityCaptured()
            && !BrowserCampaignResearch.fungalPassReady()){
                BrowserCampaignResearch.spendNextFungalResearch();
            }else if(BrowserCampaignResearch.fungalPassReady()
            && !BrowserCampaignResearch.fungalPassCaptured()){
                BrowserCampaignRuntime.playFungalPass();
            }else if(BrowserCampaignResearch.fungalPassCaptured()
            && !BrowserCampaignResearch.frontierReady()){
                BrowserCampaignResearch.spendNextFrontierResearch();
            }else if(BrowserCampaignResearch.frontierReady()
            && !BrowserCampaignResearch.frontierCaptured()){
                BrowserCampaignRuntime.playFrontier();
            }else if(BrowserCampaignResearch.frontierCaptured()
            && !BrowserCampaignResearch.saltFlatsReady()){
                BrowserCampaignResearch.spendNextSaltResearch();
            }else if(BrowserCampaignResearch.saltFlatsReady()
            && !BrowserCampaignResearch.saltFlatsCaptured()){
                BrowserCampaignRuntime.playSaltFlats();
            }else if(BrowserCampaignResearch.saltFlatsCaptured()
            && !BrowserCampaignResearch.tarFieldsReady()){
                BrowserCampaignResearch.spendNextTarResearch();
            }else if(BrowserCampaignResearch.tarFieldsReady()
            && !BrowserCampaignResearch.tarFieldsCaptured()){
                BrowserCampaignRuntime.playTarFields();
            }else if(BrowserCampaignResearch.tarFieldsCaptured()
            && !BrowserCampaignResearch.impact0078Ready()){
                BrowserCampaignResearch.spendNextImpactResearch();
            }else if(BrowserCampaignResearch.impact0078Ready()
            && !BrowserCampaignResearch.impact0078Captured()){
                BrowserCampaignRuntime.playImpact0078();
            }else if(BrowserCampaignResearch.impact0078Captured()
            && BrowserCampaignResearch.stainedMountainsReady()
            && !BrowserCampaignResearch.stainedMountainsCaptured()){
                BrowserCampaignRuntime.playStainedMountains();
            }else if(BrowserCampaignResearch.stainedMountainsCaptured()
            && !BrowserCampaignResearch.infestedCanyonsReady()){
                BrowserCampaignResearch.spendNextInfestedResearch();
            }else if(BrowserCampaignResearch.infestedCanyonsReady()
            && !BrowserCampaignResearch.infestedCanyonsCaptured()){
                BrowserCampaignRuntime.playInfestedCanyons();
            }else if(BrowserCampaignResearch.infestedCanyonsCaptured()
            && !BrowserCampaignResearch.nuclearComplexReady()){
                BrowserCampaignResearch.spendNextNuclearResearch();
            }else if(BrowserCampaignResearch.nuclearComplexReady()
            && !BrowserCampaignResearch.nuclearComplexCaptured()){
                BrowserCampaignRuntime.playNuclearComplex();
            }else if(BrowserCampaignResearch.nuclearComplexCaptured()
            && !BrowserCampaignResearch.desolateRiftReady()){
                BrowserCampaignResearch.spendNextDesolateResearch();
            }else if(BrowserCampaignResearch.desolateRiftReady()
            && !BrowserCampaignResearch.desolateRiftCaptured()){
                BrowserCampaignRuntime.playDesolateRift();
            }else if(BrowserCampaignResearch.desolateRiftCaptured()
            && BrowserCampaignResearch.facility32mReady()
            && !BrowserCampaignResearch.facility32mCaptured()){
                BrowserCampaignRuntime.playFacility32m();
            }else if(BrowserCampaignResearch.facility32mCaptured()
            && !BrowserCampaignResearch.perilousHarborReady()){
                BrowserCampaignResearch.spendNextPerilousResearch();
            }else if(BrowserCampaignResearch.perilousHarborReady()
            && !BrowserCampaignResearch.perilousHarborCaptured()){
                BrowserCampaignRuntime.playPerilousHarbor();
            }else if(BrowserCampaignResearch.perilousHarborCaptured()
            && !BrowserCampaignResearch.extractionOutpostReady()){
                BrowserCampaignResearch.spendNextExtractionResearch();
            }else if(BrowserCampaignResearch.extractionOutpostReady()
            && !BrowserCampaignResearch.extractionOutpostCaptured()){
                BrowserCampaignRuntime.playExtractionOutpost();
            }else if(BrowserCampaignResearch.extractionOutpostCaptured()
            && !BrowserCampaignResearch.coastlineReady()){
                BrowserCampaignResearch.spendNextCoastlineResearch();
            }else if(BrowserCampaignResearch.coastlineReady()
            && !BrowserCampaignResearch.coastlineCaptured()){
                BrowserCampaignRuntime.playCoastline();
            }else if(BrowserCampaignResearch.coastlineCaptured()
            && !BrowserCampaignResearch.navalFortressReady()){
                BrowserCampaignResearch.spendNextNavalFortressResearch();
            }else if(BrowserCampaignResearch.navalFortressReady()
            && !BrowserCampaignResearch.navalFortressCaptured()){
                BrowserCampaignRuntime.playNavalFortress();
            }else if(BrowserCampaignResearch.navalFortressCaptured()
            && BrowserCampaignResearch.overgrowthReady()
            && !BrowserCampaignResearch.overgrowthCaptured()){
                BrowserCampaignRuntime.playOvergrowth();
            }else if(BrowserCampaignResearch.overgrowthCaptured()
            && !BrowserCampaignResearch.mycelialBastionReady()){
                BrowserCampaignResearch.spendNextMycelialResearch();
            }else if(BrowserCampaignResearch.mycelialBastionReady()
            && !BrowserCampaignResearch.mycelialBastionCaptured()){
                BrowserCampaignRuntime.playMycelialBastion();
            }else if(BrowserCampaignResearch.mycelialBastionCaptured()
            && !BrowserCampaignResearch.littoralShipyardReady()){
                BrowserCampaignResearch.spendNextLittoralResearch();
            }else if(BrowserCampaignResearch.littoralShipyardReady()
            && !BrowserCampaignResearch.littoralShipyardCaptured()){
                BrowserCampaignRuntime.playLittoralShipyard();
            }else if(BrowserCampaignResearch.littoralShipyardCaptured()
            && !BrowserCampaignResearch.planetaryTerminalReady()){
                BrowserCampaignResearch.spendNextTerminalResearch();
            }else if(BrowserCampaignResearch.planetaryTerminalReady()
            && !BrowserCampaignResearch.planetaryTerminalCaptured()){
                BrowserCampaignRuntime.playPlanetaryTerminal();
            }else if(BrowserCampaignResearch.taintedWoodsReady()
            && !BrowserCampaignResearch.taintedWoodsCaptured()){
                BrowserCampaignRuntime.playTaintedWoods();
            }else if(BrowserCampaignResearch.taintedWoodsCaptured()
            && !BrowserCampaignResearch.atollsReady()){
                BrowserCampaignResearch.spendNextAtollsResearch();
            }else if(BrowserCampaignResearch.atollsReady()
            && !BrowserCampaignResearch.atollsCaptured()){
                BrowserCampaignRuntime.playAtolls();
            }else if(BrowserCampaignResearch.atollsCaptured()
            && !BrowserCampaignResearch.testingGroundsReady()){
                BrowserCampaignResearch.spendNextTestingGroundsResearch();
            }else if(BrowserCampaignResearch.testingGroundsReady()
            && !BrowserCampaignResearch.testingGroundsCaptured()){
                BrowserCampaignRuntime.playTestingGrounds();
            }else if(BrowserCampaignResearch.sunkenPierReady()
            && !BrowserCampaignResearch.sunkenPierCaptured()){
                BrowserCampaignRuntime.playSunkenPier();
            }else if(BrowserCampaignResearch.sunkenPierCaptured()
            && !BrowserCampaignResearch.weatheredChannelsReady()){
                BrowserCampaignResearch.spendNextWeatheredResearch();
            }else if(BrowserCampaignResearch.weatheredChannelsReady()
            && !BrowserCampaignResearch.weatheredChannelsCaptured()){
                BrowserCampaignRuntime.playWeatheredChannels();
            }
        });
        craterResearch.setDisabled(() -> {
            if(!BrowserCampaignResearch.crateredBattlegroundReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextCraterResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.crateredBattlegroundCaptured()) return true;

            if(!BrowserCampaignResearch.ruinousShoresReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextRuinousResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.ruinousShoresCaptured()) return false;

            if(!BrowserCampaignResearch.windsweptIslandsReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextWindsweptResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.windsweptIslandsCaptured()) return false;

            if(!BrowserCampaignResearch.biomassFacilityReady()) return true;
            if(!BrowserCampaignResearch.biomassFacilityCaptured()) return false;

            if(!BrowserCampaignResearch.fungalPassReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextFungalResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.fungalPassCaptured()) return false;

            if(!BrowserCampaignResearch.frontierReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextFrontierResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.frontierCaptured()) return false;

            if(!BrowserCampaignResearch.saltFlatsReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextSaltResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.saltFlatsCaptured()) return false;

            if(!BrowserCampaignResearch.tarFieldsReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextTarResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.tarFieldsCaptured()) return false;

            if(!BrowserCampaignResearch.impact0078Ready()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextImpactResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.impact0078Captured()) return false;

            if(!BrowserCampaignResearch.stainedMountainsReady()) return true;
            if(!BrowserCampaignResearch.stainedMountainsCaptured()) return false;

            if(!BrowserCampaignResearch.infestedCanyonsReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextInfestedResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.infestedCanyonsCaptured()) return false;

            if(!BrowserCampaignResearch.nuclearComplexReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextNuclearResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.nuclearComplexCaptured()) return false;

            if(!BrowserCampaignResearch.desolateRiftReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextDesolateResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.desolateRiftCaptured()) return false;

            if(!BrowserCampaignResearch.facility32mReady()) return true;
            if(!BrowserCampaignResearch.facility32mCaptured()) return false;

            if(!BrowserCampaignResearch.perilousHarborReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextPerilousResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.perilousHarborCaptured()) return false;

            if(!BrowserCampaignResearch.extractionOutpostReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextExtractionResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.extractionOutpostCaptured()) return false;

            if(!BrowserCampaignResearch.coastlineReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextCoastlineResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.coastlineCaptured()) return false;

            if(!BrowserCampaignResearch.navalFortressReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextNavalFortressResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.navalFortressCaptured()) return false;

            if(!BrowserCampaignResearch.overgrowthReady()) return true;
            if(!BrowserCampaignResearch.overgrowthCaptured()) return false;

            if(!BrowserCampaignResearch.mycelialBastionReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextMycelialResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.mycelialBastionCaptured()) return false;

            if(!BrowserCampaignResearch.littoralShipyardReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextLittoralResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.littoralShipyardCaptured()) return false;

            if(!BrowserCampaignResearch.planetaryTerminalReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextTerminalResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.planetaryTerminalCaptured()) return false;

            if(!BrowserCampaignResearch.taintedWoodsReady()) return true;
            if(!BrowserCampaignResearch.taintedWoodsCaptured()) return false;

            if(!BrowserCampaignResearch.atollsReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextAtollsResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.atollsCaptured()) return false;

            if(!BrowserCampaignResearch.testingGroundsReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextTestingGroundsResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.testingGroundsCaptured()) return false;

            if(!BrowserCampaignResearch.sunkenPierReady()) return true;
            if(!BrowserCampaignResearch.sunkenPierCaptured()) return false;

            if(!BrowserCampaignResearch.weatheredChannelsReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextWeatheredResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            return BrowserCampaignResearch.weatheredChannelsCaptured();
        });
        craterResearch.update(() -> {
            if(!BrowserCampaignResearch.crateredBattlegroundReady()){
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
            }else if(!BrowserCampaignResearch.crateredBattlegroundCaptured()){
                craterResearch.setText(Core.bundle.get("sector.crateredBattleground.name", "Cratered Battleground") +
                    " — " + Core.bundle.get("locked", "Locked"));
            }else if(!BrowserCampaignResearch.ruinousShoresReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextRuinousResearch();
                craterResearch.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.ruinousShoresCaptured()){
                boolean saved = BrowserCampaignRuntime.hasRuinousShoresSave();
                craterResearch.setText(Core.bundle.get("sector.ruinousShores.name", "Ruinous Shores") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.windsweptIslandsReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextWindsweptResearch();
                craterResearch.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.windsweptIslandsCaptured()){
                boolean saved = BrowserCampaignRuntime.hasWindsweptIslandsSave();
                craterResearch.setText(Core.bundle.get("sector.windsweptIslands.name", "Windswept Islands") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.biomassFacilityCaptured()){
                boolean ready = BrowserCampaignResearch.biomassFacilityReady();
                boolean saved = BrowserCampaignRuntime.hasBiomassFacilitySave();
                craterResearch.setText(Core.bundle.get("sector.biomassFacility.name", "Biomass Synthesis Facility") + " — " +
                    (ready ? Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play")
                        : Core.bundle.get("locked", "Locked")));
            }else if(!BrowserCampaignResearch.fungalPassReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextFungalResearch();
                craterResearch.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.fungalPassCaptured()){
                boolean saved = BrowserCampaignRuntime.hasFungalPassSave();
                craterResearch.setText(Core.bundle.get("sector.fungalPass.name", "Fungal Pass") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.frontierReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextFrontierResearch();
                craterResearch.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.frontierCaptured()){
                boolean saved = BrowserCampaignRuntime.hasFrontierSave();
                craterResearch.setText(Core.bundle.get("sector.frontier.name", "Frontier") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.saltFlatsReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextSaltResearch();
                craterResearch.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.saltFlatsCaptured()){
                boolean saved = BrowserCampaignRuntime.hasSaltFlatsSave();
                craterResearch.setText(Core.bundle.get("sector.saltFlats.name", "Salt Flats") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.tarFieldsReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextTarResearch();
                craterResearch.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.tarFieldsCaptured()){
                boolean saved = BrowserCampaignRuntime.hasTarFieldsSave();
                craterResearch.setText(Core.bundle.get("sector.tarFields.name", "Tar Fields") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.impact0078Ready()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextImpactResearch();
                if(BrowserCampaignResearch.waitingForImpactThorium()){
                    craterResearch.setText(Core.bundle.format("requirement.produce", mindustry.content.Items.thorium.localizedName));
                }else{
                    craterResearch.setText(next == null
                        ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                        : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                            BrowserCampaignResearch.remaining(next));
                }
            }else if(!BrowserCampaignResearch.impact0078Captured()){
                boolean saved = BrowserCampaignRuntime.hasImpact0078Save();
                craterResearch.setText(Core.bundle.get("sector.impact0078.name", "Impact 0078") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.stainedMountainsCaptured()){
                boolean saved = BrowserCampaignRuntime.hasStainedMountainsSave();
                craterResearch.setText(Core.bundle.get("sector.stainedMountains.name", "Stained Mountains") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.infestedCanyonsReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextInfestedResearch();
                craterResearch.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.infestedCanyonsCaptured()){
                boolean saved = BrowserCampaignRuntime.hasInfestedCanyonsSave();
                craterResearch.setText(Core.bundle.get("sector.infestedCanyons.name", "Infested Canyons") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.nuclearComplexReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextNuclearResearch();
                if(BrowserCampaignResearch.waitingForNuclearPlastanium()){
                    craterResearch.setText(Core.bundle.format("requirement.produce", mindustry.content.Items.plastanium.localizedName));
                }else{
                    craterResearch.setText(next == null
                        ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                        : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                            BrowserCampaignResearch.remaining(next));
                }
            }else if(!BrowserCampaignResearch.nuclearComplexCaptured()){
                boolean saved = BrowserCampaignRuntime.hasNuclearComplexSave();
                craterResearch.setText(Core.bundle.get("sector.nuclearComplex.name", "Nuclear Production Complex") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.desolateRiftReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextDesolateResearch();
                if(BrowserCampaignResearch.waitingForDesolateCryofluid()){
                    craterResearch.setText(Core.bundle.format("requirement.produce", mindustry.content.Liquids.cryofluid.localizedName));
                }else{
                    craterResearch.setText(next == null
                        ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                        : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                            BrowserCampaignResearch.remaining(next));
                }
            }else if(!BrowserCampaignResearch.desolateRiftCaptured()){
                boolean saved = BrowserCampaignRuntime.hasDesolateRiftSave();
                craterResearch.setText(Core.bundle.get("sector.desolateRift.name", "Desolate Rift") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.facility32mCaptured()){
                boolean saved = BrowserCampaignRuntime.hasFacility32mSave();
                craterResearch.setText(Core.bundle.get("sector.facility32m.name", "Facility 32M") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.perilousHarborReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextPerilousResearch();
                craterResearch.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.perilousHarborCaptured()){
                boolean saved = BrowserCampaignRuntime.hasPerilousHarborSave();
                craterResearch.setText(Core.bundle.get("sector.perilousHarbor.name", "Perilous Harbor") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.extractionOutpostReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextExtractionResearch();
                craterResearch.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.extractionOutpostCaptured()){
                boolean saved = BrowserCampaignRuntime.hasExtractionOutpostSave();
                craterResearch.setText(Core.bundle.get("sector.extractionOutpost.name", "Extraction Outpost") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.coastlineReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextCoastlineResearch();
                craterResearch.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.coastlineCaptured()){
                boolean saved = BrowserCampaignRuntime.hasCoastlineSave();
                craterResearch.setText(Core.bundle.get("sector.coastline.name", "Coastline") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.navalFortressReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextNavalFortressResearch();
                craterResearch.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.navalFortressCaptured()){
                boolean saved = BrowserCampaignRuntime.hasNavalFortressSave();
                craterResearch.setText(Core.bundle.get("sector.navalFortress.name", "Naval Fortress") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.overgrowthCaptured()){
                boolean saved = BrowserCampaignRuntime.hasOvergrowthSave();
                craterResearch.setText(Core.bundle.get("sector.overgrowth.name", "Overgrowth") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.mycelialBastionReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextMycelialResearch();
                craterResearch.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.mycelialBastionCaptured()){
                boolean saved = BrowserCampaignRuntime.hasMycelialBastionSave();
                craterResearch.setText(Core.bundle.get("sector.mycelialBastion.name", "Mycelial Bastion") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.littoralShipyardReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextLittoralResearch();
                craterResearch.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.littoralShipyardCaptured()){
                boolean saved = BrowserCampaignRuntime.hasLittoralShipyardSave();
                craterResearch.setText(Core.bundle.get("sector.littoralShipyard.name", "Littoral Shipyard") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.planetaryTerminalReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextTerminalResearch();
                craterResearch.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.planetaryTerminalCaptured()){
                boolean saved = BrowserCampaignRuntime.hasPlanetaryTerminalSave();
                craterResearch.setText(Core.bundle.get("sector.planetaryTerminal.name", "Planetary Launch Terminal") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.taintedWoodsReady()){
                craterResearch.setText(Core.bundle.format("requirement.produce", mindustry.content.Items.sporePod.localizedName));
            }else if(!BrowserCampaignResearch.taintedWoodsCaptured()){
                boolean saved = BrowserCampaignRuntime.hasTaintedWoodsSave();
                craterResearch.setText(Core.bundle.get("sector.taintedWoods.name", "Tainted Woods") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.atollsReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextAtollsResearch();
                craterResearch.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.atollsCaptured()){
                boolean saved = BrowserCampaignRuntime.hasAtollsSave();
                craterResearch.setText(Core.bundle.get("sector.atolls.name", "Atolls") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.testingGroundsReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextTestingGroundsResearch();
                craterResearch.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.testingGroundsCaptured()){
                boolean saved = BrowserCampaignRuntime.hasTestingGroundsSave();
                craterResearch.setText(Core.bundle.get("sector.testingGrounds.name", "Testing Grounds") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.sunkenPierCaptured()){
                boolean saved = BrowserCampaignRuntime.hasSunkenPierSave();
                craterResearch.setText(Core.bundle.get("sector.sunkenPier.name", "Sunken Pier") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.weatheredChannelsReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextWeatheredResearch();
                craterResearch.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.weatheredChannelsCaptured()){
                boolean saved = BrowserCampaignRuntime.hasWeatheredChannelsSave();
                craterResearch.setText(Core.bundle.get("sector.weatheredChannels.name", "Weathered Channels") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else{
                craterResearch.setText(Core.bundle.get("planet.serpulo.name", "Serpulo") + " — " +
                    Core.bundle.get("complete", "Complete"));
            }

            markCampaignRuinousState(
                mindustry.content.Blocks.graphitePress.unlocked(),
                mindustry.content.Blocks.siliconSmelter.unlocked(),
                mindustry.content.Blocks.kiln.unlocked(),
                mindustry.content.Blocks.mechanicalPump.unlocked(),
                BrowserCampaignResearch.ruinousShoresReady(),
                BrowserCampaignRuntime.hasRuinousShoresSave()
            );
            markCampaignWindsweptState(
                mindustry.content.Blocks.pneumaticDrill.unlocked(),
                mindustry.content.Blocks.duo.unlocked(),
                mindustry.content.Blocks.scatter.unlocked(),
                mindustry.content.Blocks.hail.unlocked(),
                mindustry.content.Blocks.steamGenerator.unlocked(),
                BrowserCampaignResearch.windsweptIslandsReady(),
                BrowserCampaignRuntime.hasWindsweptIslandsSave()
            );
            markCampaignSaltBranchState(
                BrowserCampaignResearch.biomassFacilityReady(),
                BrowserCampaignResearch.biomassFacilityCaptured(),
                BrowserCampaignResearch.fungalPassReady(),
                BrowserCampaignResearch.fungalPassCaptured(),
                BrowserCampaignResearch.frontierReady(),
                BrowserCampaignResearch.frontierCaptured(),
                BrowserCampaignResearch.saltFlatsReady(),
                BrowserCampaignResearch.saltFlatsCaptured(),
                BrowserCampaignResearch.tarFieldsReady(),
                BrowserCampaignRuntime.hasTarFieldsSave()
            );
            markCampaignImpactState(
                mindustry.content.Blocks.laserDrill.unlocked(),
                mindustry.content.Items.thorium.unlocked(),
                mindustry.content.Blocks.lancer.unlocked(),
                mindustry.content.Blocks.salvo.unlocked(),
                mindustry.content.Blocks.coreFoundation.unlocked(),
                BrowserCampaignResearch.impact0078Ready(),
                BrowserCampaignRuntime.hasImpact0078Save()
            );
            markCampaignLateState(
                BrowserCampaignResearch.stainedMountainsReady(),
                BrowserCampaignResearch.stainedMountainsCaptured(),
                BrowserCampaignResearch.infestedCanyonsReady(),
                BrowserCampaignResearch.infestedCanyonsCaptured(),
                BrowserCampaignResearch.nuclearComplexReady(),
                BrowserCampaignResearch.nuclearComplexCaptured(),
                BrowserCampaignResearch.desolateRiftReady(),
                BrowserCampaignRuntime.hasDesolateRiftSave()
            );
            markCampaignFinalInfraState(
                BrowserCampaignResearch.facility32mReady(),
                BrowserCampaignResearch.facility32mCaptured(),
                BrowserCampaignResearch.perilousHarborReady(),
                BrowserCampaignResearch.perilousHarborCaptured(),
                BrowserCampaignResearch.extractionOutpostReady(),
                BrowserCampaignResearch.extractionOutpostCaptured(),
                BrowserCampaignResearch.coastlineReady(),
                BrowserCampaignResearch.coastlineCaptured(),
                BrowserCampaignResearch.navalFortressReady(),
                BrowserCampaignRuntime.hasNavalFortressSave()
            );
            markCampaignTerminalState(
                BrowserCampaignResearch.overgrowthReady(),
                BrowserCampaignResearch.overgrowthCaptured(),
                BrowserCampaignResearch.mycelialBastionReady(),
                BrowserCampaignResearch.mycelialBastionCaptured(),
                BrowserCampaignResearch.littoralShipyardReady(),
                BrowserCampaignResearch.littoralShipyardCaptured(),
                BrowserCampaignResearch.planetaryTerminalReady(),
                BrowserCampaignRuntime.hasPlanetaryTerminalSave()
            );
            markCampaignOptionalState(
                mindustry.content.Items.sporePod.unlocked(),
                BrowserCampaignResearch.taintedWoodsReady(),
                BrowserCampaignResearch.taintedWoodsCaptured(),
                BrowserCampaignResearch.atollsReady(),
                BrowserCampaignResearch.atollsCaptured(),
                BrowserCampaignResearch.testingGroundsReady(),
                BrowserCampaignResearch.testingGroundsCaptured(),
                BrowserCampaignResearch.sunkenPierReady(),
                BrowserCampaignResearch.sunkenPierCaptured(),
                BrowserCampaignResearch.weatheredChannelsReady(),
                BrowserCampaignResearch.weatheredChannelsCaptured()
            );
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

        // Erekir uses the same compact one-action-at-a-time pattern so mobile does not
        // grow a permanent row for every Erekir prerequisite.
        TextButton erekirProgress = new TextButton("");
        erekirProgress.clicked(() -> {
            if(!BrowserCampaignResearch.onsetCaptured()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextOnsetResearch();
                if(BrowserCampaignRuntime.hasOnsetSave() && next != null
                && BrowserCampaignResearch.canSpend(next)){
                    BrowserCampaignResearch.spendNextOnsetResearch();
                }else{
                    BrowserCampaignRuntime.playOnset();
                }
            }else if(!BrowserCampaignResearch.aegisReady()){
                BrowserCampaignResearch.spendNextAegisResearch();
            }else if(!BrowserCampaignResearch.aegisCaptured()){
                BrowserCampaignRuntime.playAegis();
            }else if(BrowserCampaignResearch.lakeReady()
            && !BrowserCampaignResearch.lakeCaptured()){
                BrowserCampaignRuntime.playLake();
            }else if(BrowserCampaignResearch.lakeCaptured()
            && !BrowserCampaignResearch.intersectReady()){
                BrowserCampaignResearch.spendNextIntersectResearch();
            }else if(BrowserCampaignResearch.intersectReady()
            && !BrowserCampaignResearch.intersectCaptured()){
                BrowserCampaignRuntime.playIntersect();
            }else if(BrowserCampaignResearch.intersectCaptured()
            && !BrowserCampaignResearch.atlasReady()){
                BrowserCampaignResearch.spendNextAtlasResearch();
            }else if(BrowserCampaignResearch.atlasReady()
            && !BrowserCampaignResearch.atlasCaptured()){
                BrowserCampaignRuntime.playAtlas();
            }else if(BrowserCampaignResearch.atlasCaptured()
            && !BrowserCampaignResearch.splitReady()){
                BrowserCampaignResearch.spendNextSplitResearch();
            }else if(BrowserCampaignResearch.splitReady()
            && !BrowserCampaignResearch.splitCaptured()){
                BrowserCampaignRuntime.playSplit();
            }else if(BrowserCampaignResearch.splitCaptured()
            && BrowserCampaignResearch.basinReady()
            && !BrowserCampaignResearch.basinCaptured()){
                BrowserCampaignRuntime.playBasin();
            }else if(BrowserCampaignResearch.basinCaptured()
            && BrowserCampaignResearch.marshReady()
            && !BrowserCampaignResearch.marshCaptured()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextMarshResearch();
                if(BrowserCampaignRuntime.hasMarshSave() && next != null
                && BrowserCampaignResearch.canSpend(next)){
                    BrowserCampaignResearch.spendNextMarshResearch();
                }else{
                    BrowserCampaignRuntime.playMarsh();
                }
            }else if(BrowserCampaignResearch.marshCaptured()
            && BrowserCampaignResearch.peaksReady()
            && !BrowserCampaignResearch.peaksCaptured()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextPeaksResearch();
                if(BrowserCampaignRuntime.hasPeaksSave() && next != null
                && BrowserCampaignResearch.canSpend(next)){
                    BrowserCampaignResearch.spendNextPeaksResearch();
                }else{
                    BrowserCampaignRuntime.playPeaks();
                }
            }else if(BrowserCampaignResearch.peaksCaptured()
            && !BrowserCampaignResearch.ravineReady()){
                BrowserCampaignRuntime.playPeaks();
            }else if(BrowserCampaignResearch.ravineReady()
            && !BrowserCampaignResearch.ravineCaptured()){
                BrowserCampaignRuntime.playRavine();
            }else if(BrowserCampaignResearch.ravineCaptured()
            && !BrowserCampaignResearch.calderaReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextCalderaResearch();
                if(next != null && BrowserCampaignResearch.canSpend(next)){
                    BrowserCampaignResearch.spendNextCalderaResearch();
                }else{
                    BrowserCampaignRuntime.playRavine();
                }
            }else if(BrowserCampaignResearch.calderaReady()
            && !BrowserCampaignResearch.calderaCaptured()){
                BrowserCampaignRuntime.playCaldera();
            }else if(BrowserCampaignResearch.calderaCaptured()
            && !BrowserCampaignResearch.strongholdReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextStrongholdResearch();
                if(next != null && BrowserCampaignResearch.canSpend(next)){
                    BrowserCampaignResearch.spendNextStrongholdResearch();
                }else{
                    BrowserCampaignRuntime.playCaldera();
                }
            }else if(BrowserCampaignResearch.strongholdReady()
            && !BrowserCampaignResearch.strongholdCaptured()){
                BrowserCampaignRuntime.playStronghold();
            }else if(BrowserCampaignResearch.creviceReady()
            && !BrowserCampaignResearch.creviceCaptured()){
                BrowserCampaignRuntime.playCrevice();
            }else if(BrowserCampaignResearch.siegeReady()
            && !BrowserCampaignResearch.siegeCaptured()){
                BrowserCampaignRuntime.playSiege();
            }else if(BrowserCampaignResearch.crossroadsReady()
            && !BrowserCampaignResearch.crossroadsCaptured()){
                BrowserCampaignRuntime.playCrossroads();
            }else if(BrowserCampaignResearch.crossroadsCaptured()
            && !BrowserCampaignResearch.karstReady()){
                BrowserCampaignResearch.spendNextKarstResearch();
            }else if(BrowserCampaignResearch.karstReady()
            && !BrowserCampaignResearch.karstCaptured()){
                BrowserCampaignRuntime.playKarst();
            }else if(BrowserCampaignResearch.karstCaptured()
            && !BrowserCampaignResearch.originReady()){
                BrowserCampaignResearch.spendNextOriginResearch();
            }else if(BrowserCampaignResearch.originReady()){
                BrowserCampaignRuntime.playOrigin();
            }
        });
        erekirProgress.setDisabled(() -> {
            if(!BrowserCampaignResearch.onsetCaptured()) return false;

            if(!BrowserCampaignResearch.aegisReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextAegisResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.aegisCaptured()) return false;

            if(!BrowserCampaignResearch.lakeReady()) return true;
            if(!BrowserCampaignResearch.lakeCaptured()) return false;

            if(!BrowserCampaignResearch.intersectReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextIntersectResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.intersectCaptured()) return false;

            if(!BrowserCampaignResearch.atlasReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextAtlasResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.atlasCaptured()) return false;

            if(!BrowserCampaignResearch.splitReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextSplitResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.splitCaptured()) return false;

            if(!BrowserCampaignResearch.basinReady()) return true;
            if(!BrowserCampaignResearch.basinCaptured()) return false;

            if(!BrowserCampaignResearch.marshReady()) return true;
            if(!BrowserCampaignResearch.marshCaptured()) return false;

            if(!BrowserCampaignResearch.peaksReady()) return true;
            if(!BrowserCampaignResearch.peaksCaptured()) return false;

            if(!BrowserCampaignResearch.ravineReady()){
                return !BrowserCampaignResearch.waitingForRavineSlag();
            }
            if(!BrowserCampaignResearch.ravineCaptured()) return false;

            if(!BrowserCampaignResearch.calderaReady()) return false;
            if(!BrowserCampaignResearch.calderaCaptured()) return false;

            if(!BrowserCampaignResearch.strongholdReady()) return false;
            if(!BrowserCampaignResearch.strongholdCaptured()) return false;
            if(!BrowserCampaignResearch.creviceReady()) return true;
            if(!BrowserCampaignResearch.creviceCaptured()) return false;
            if(!BrowserCampaignResearch.siegeReady()) return true;
            if(!BrowserCampaignResearch.siegeCaptured()) return false;
            if(!BrowserCampaignResearch.crossroadsReady()) return true;
            if(!BrowserCampaignResearch.crossroadsCaptured()) return false;

            if(!BrowserCampaignResearch.karstReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextKarstResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            if(!BrowserCampaignResearch.karstCaptured()) return false;

            if(!BrowserCampaignResearch.originReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextOriginResearch();
                return next == null || !BrowserCampaignResearch.canSpend(next);
            }
            return false;
        });
        erekirProgress.update(() -> {
            if(!BrowserCampaignResearch.onsetCaptured()){
                boolean saved = BrowserCampaignRuntime.hasOnsetSave();
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextOnsetResearch();
                if(saved && next != null && BrowserCampaignResearch.canSpend(next)){
                    erekirProgress.setText(next.localizedName + " — " +
                        Core.bundle.get("research", "Research") + " " + BrowserCampaignResearch.remaining(next));
                }else{
                    erekirProgress.setText(Core.bundle.get("sector.onset.name", "Onset") + " — " +
                        Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
                }
            }else if(!BrowserCampaignResearch.aegisReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextAegisResearch();
                erekirProgress.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.aegisCaptured()){
                boolean saved = BrowserCampaignRuntime.hasAegisSave();
                erekirProgress.setText(Core.bundle.get("sector.aegis.name", "Aegis") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.lakeCaptured()){
                boolean saved = BrowserCampaignRuntime.hasLakeSave();
                erekirProgress.setText(Core.bundle.get("sector.lake.name", "Lake") + " — " +
                    (BrowserCampaignResearch.lakeReady()
                        ? Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play")
                        : Core.bundle.get("locked", "Locked")));
            }else if(!BrowserCampaignResearch.intersectReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextIntersectResearch();
                erekirProgress.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.intersectCaptured()){
                boolean saved = BrowserCampaignRuntime.hasIntersectSave();
                erekirProgress.setText(Core.bundle.get("sector.intersect.name", "Intersect") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.atlasReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextAtlasResearch();
                erekirProgress.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.atlasCaptured()){
                boolean saved = BrowserCampaignRuntime.hasAtlasSave();
                erekirProgress.setText(Core.bundle.get("sector.atlas.name", "Atlas") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.splitReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextSplitResearch();
                erekirProgress.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.splitCaptured()){
                boolean saved = BrowserCampaignRuntime.hasSplitSave();
                erekirProgress.setText(Core.bundle.get("sector.split.name", "Split") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.basinCaptured()){
                boolean saved = BrowserCampaignRuntime.hasBasinSave();
                erekirProgress.setText(Core.bundle.get("sector.basin.name", "Basin") + " — " +
                    (BrowserCampaignResearch.basinReady()
                        ? Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play")
                        : Core.bundle.get("locked", "Locked")));
            }else if(!BrowserCampaignResearch.marshCaptured()){
                boolean saved = BrowserCampaignRuntime.hasMarshSave();
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
            }else if(!BrowserCampaignResearch.peaksCaptured()){
                boolean saved = BrowserCampaignRuntime.hasPeaksSave();
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextPeaksResearch();
                if(saved && next != null && BrowserCampaignResearch.canSpend(next)){
                    erekirProgress.setText(next.localizedName + " — " +
                        Core.bundle.get("research", "Research") + " " + BrowserCampaignResearch.remaining(next));
                }else{
                    erekirProgress.setText(Core.bundle.get("sector.peaks.name", "Peaks") + " — " +
                        Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
                }
            }else if(!BrowserCampaignResearch.ravineReady()){
                erekirProgress.setText(mindustry.content.Liquids.slag.localizedName + " — " +
                    Core.bundle.get("produce", "Produce"));
            }else if(!BrowserCampaignResearch.ravineCaptured()){
                boolean saved = BrowserCampaignRuntime.hasRavineSave();
                erekirProgress.setText(Core.bundle.get("sector.ravine.name", "Ravine") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.calderaReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextCalderaResearch();
                if(next != null && BrowserCampaignResearch.canSpend(next)){
                    erekirProgress.setText(next.localizedName + " — " +
                        Core.bundle.get("research", "Research") + " " + BrowserCampaignResearch.remaining(next));
                }else{
                    erekirProgress.setText(Core.bundle.get("sector.ravine.name", "Ravine") + " — " +
                        Core.bundle.get("continue", "Continue"));
                }
            }else if(!BrowserCampaignResearch.calderaCaptured()){
                boolean saved = BrowserCampaignRuntime.hasCalderaSave();
                erekirProgress.setText(Core.bundle.get("sector.caldera-erekir.name", "Caldera") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.strongholdReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextStrongholdResearch();
                if(next != null && BrowserCampaignResearch.canSpend(next)){
                    erekirProgress.setText(next.localizedName + " — " +
                        Core.bundle.get("research", "Research") + " " + BrowserCampaignResearch.remaining(next));
                }else{
                    erekirProgress.setText(Core.bundle.get("sector.caldera-erekir.name", "Caldera") + " — " +
                        Core.bundle.get("continue", "Continue"));
                }
            }else if(!BrowserCampaignResearch.strongholdCaptured()){
                boolean saved = BrowserCampaignRuntime.hasStrongholdSave();
                erekirProgress.setText(Core.bundle.get("sector.stronghold.name", "Stronghold") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.creviceCaptured()){
                boolean saved = BrowserCampaignRuntime.hasCreviceSave();
                erekirProgress.setText(Core.bundle.get("sector.crevice.name", "Crevice") + " — " +
                    (BrowserCampaignResearch.creviceReady()
                        ? Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play")
                        : Core.bundle.get("locked", "Locked")));
            }else if(!BrowserCampaignResearch.siegeCaptured()){
                boolean saved = BrowserCampaignRuntime.hasSiegeSave();
                erekirProgress.setText(Core.bundle.get("sector.siege.name", "Siege") + " — " +
                    (BrowserCampaignResearch.siegeReady()
                        ? Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play")
                        : Core.bundle.get("locked", "Locked")));
            }else if(!BrowserCampaignResearch.crossroadsCaptured()){
                boolean saved = BrowserCampaignRuntime.hasCrossroadsSave();
                erekirProgress.setText(Core.bundle.get("sector.crossroads.name", "Crossroads") + " — " +
                    (BrowserCampaignResearch.crossroadsReady()
                        ? Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play")
                        : Core.bundle.get("locked", "Locked")));
            }else if(!BrowserCampaignResearch.karstReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextKarstResearch();
                erekirProgress.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else if(!BrowserCampaignResearch.karstCaptured()){
                boolean saved = BrowserCampaignRuntime.hasKarstSave();
                erekirProgress.setText(Core.bundle.get("sector.karst.name", "Karst") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }else if(!BrowserCampaignResearch.originReady()){
                mindustry.ctype.UnlockableContent next = BrowserCampaignResearch.nextOriginResearch();
                erekirProgress.setText(next == null
                    ? Core.bundle.get("research", "Research") + " — " + Core.bundle.get("complete", "Complete")
                    : next.localizedName + " — " + Core.bundle.get("research", "Research") + " " +
                        BrowserCampaignResearch.remaining(next));
            }else{
                boolean saved = BrowserCampaignRuntime.hasOriginSave();
                erekirProgress.setText(Core.bundle.get("sector.origin.name", "Origin") + " — " +
                    Core.bundle.get(saved ? "continue" : "play", saved ? "Continue" : "Play"));
            }

            markCampaignErekirState(
                BrowserCampaignResearch.onsetCaptured(),
                BrowserCampaignRuntime.hasOnsetSave(),
                BrowserCampaignResearch.aegisReady(),
                BrowserCampaignResearch.aegisCaptured(),
                BrowserCampaignRuntime.hasAegisSave(),
                BrowserCampaignResearch.lakeReady(),
                BrowserCampaignResearch.lakeCaptured(),
                BrowserCampaignRuntime.hasLakeSave(),
                BrowserCampaignResearch.intersectReady(),
                BrowserCampaignResearch.intersectCaptured(),
                BrowserCampaignRuntime.hasIntersectSave()
            );
            markCampaignErekirMidState(
                BrowserCampaignResearch.atlasReady(),
                BrowserCampaignResearch.atlasCaptured(),
                BrowserCampaignRuntime.hasAtlasSave(),
                BrowserCampaignResearch.splitReady(),
                BrowserCampaignResearch.splitCaptured(),
                BrowserCampaignRuntime.hasSplitSave(),
                BrowserCampaignResearch.basinReady(),
                BrowserCampaignResearch.basinCaptured(),
                BrowserCampaignRuntime.hasBasinSave()
            );
            markCampaignErekirBranchState(
                BrowserCampaignResearch.marshReady(),
                BrowserCampaignResearch.marshCaptured(),
                BrowserCampaignRuntime.hasMarshSave(),
                BrowserCampaignResearch.peaksReady(),
                BrowserCampaignResearch.peaksCaptured(),
                BrowserCampaignRuntime.hasPeaksSave(),
                BrowserCampaignResearch.ravineReady(),
                BrowserCampaignResearch.ravineCaptured(),
                BrowserCampaignRuntime.hasRavineSave(),
                BrowserCampaignResearch.calderaReady(),
                BrowserCampaignResearch.calderaCaptured(),
                BrowserCampaignRuntime.hasCalderaSave()
            );
            markCampaignErekirLateState(
                BrowserCampaignResearch.strongholdReady(),
                BrowserCampaignResearch.strongholdCaptured(),
                BrowserCampaignRuntime.hasStrongholdSave(),
                BrowserCampaignResearch.creviceReady(),
                BrowserCampaignResearch.creviceCaptured(),
                BrowserCampaignRuntime.hasCreviceSave(),
                BrowserCampaignResearch.siegeReady(),
                BrowserCampaignResearch.siegeCaptured(),
                BrowserCampaignRuntime.hasSiegeSave(),
                BrowserCampaignResearch.crossroadsReady(),
                BrowserCampaignResearch.crossroadsCaptured(),
                BrowserCampaignRuntime.hasCrossroadsSave()
            );
            markCampaignErekirFinalState(
                BrowserCampaignResearch.karstReady(),
                BrowserCampaignResearch.karstCaptured(),
                BrowserCampaignRuntime.hasKarstSave(),
                BrowserCampaignResearch.originReady(),
                BrowserCampaignRuntime.hasOriginSave()
            );
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

    @JSBody(params = {"karstReady", "karstCaptured", "karstSaved", "originReady", "originSaved"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-erekir-final-ui','ready'); document.documentElement.setAttribute('data-mindustry-erekir-ui-karst-ready',karstReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-karst-captured',karstCaptured ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-karst-save',karstSaved ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-origin-ready',originReady ? 'true' : 'false'); document.documentElement.setAttribute('data-mindustry-erekir-ui-origin-save',originSaved ? 'true' : 'false');")
    private static native void markCampaignErekirFinalState(boolean karstReady, boolean karstCaptured, boolean karstSaved, boolean originReady, boolean originSaved);

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
