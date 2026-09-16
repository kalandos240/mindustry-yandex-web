#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VARS = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "Vars.java"
BUILDER = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "entities" / "comp" / "BuilderComp.java"
APPLICATION = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserApplication.java"

for path in (VARS, BUILDER, APPLICATION):
    if not path.is_file():
        raise SystemExit(f"Missing builder-stage diagnostic source: {path}")

vars_text = VARS.read_text(encoding="utf-8")
vars_anchor = '''    /** Whether the game failed to launch last time. */
    public static boolean failedToLaunch = false;
'''
vars_replacement = vars_anchor + '''    /** Web-port diagnostic only: last reached stock builder stage; zero when no plan is active. */
    public static int webBuildStage = 0;
'''
if vars_text.count(vars_anchor) != 1:
    raise SystemExit("Vars builder-stage anchor no longer matches pinned upstream")
VARS.write_text(vars_text.replace(vars_anchor, vars_replacement, 1), encoding="utf-8")

builder = BUILDER.read_text(encoding="utf-8")
replacements = [
    (
        '''    public void updateBuildLogic(){
        if(type.buildSpeed <= 0f) return;
''',
        '''    public void updateBuildLogic(){
        Vars.webBuildStage = plans.size > 0 ? 1 : 0;
        if(type.buildSpeed <= 0f) return;
''',
        "entry",
    ),
    (
        '''        validatePlans();

        if(!updateBuilding || !canBuild()){
''',
        '''        validatePlans();
        if(plans.size > 0) Vars.webBuildStage = 2;

        if(!updateBuilding || !canBuild()){
''',
        "validated",
    ),
    (
        '''            BuildPlan current = buildPlan();
            Tile tile = current.tile();

            lastActive = current;
''',
        '''            Vars.webBuildStage = 3;
            BuildPlan current = buildPlan();
            Vars.webBuildStage = 4;
            Tile tile = current.tile();
            Vars.webBuildStage = 5;

            lastActive = current;
''',
        "plan-tile",
    ),
    (
        '''            if(!headless){
                Vars.control.sound.loop(Sounds.loopBuild, tile, 1.3f);
            }

            boolean allowBuildCurrent =''',
        '''            Vars.webBuildStage = 10;
            if(!headless){
                Vars.control.sound.loop(Sounds.loopBuild, tile, 1.3f);
            }
            Vars.webBuildStage = 20;

            boolean allowBuildCurrent =''',
        "sound",
    ),
    (
        '''            boolean allowBuildCurrent = current.block != null && (state.isEditor() || (state.rules.waves && team == state.rules.waveTeam && current.block.isVisible()) || (current.block.unlockedNowHost() && current.block.environmentBuildable() && current.block.isPlaceable()));

            if(!(tile.build instanceof ConstructBuild cb)){''',
        '''            boolean allowBuildCurrent = current.block != null && (state.isEditor() || (state.rules.waves && team == state.rules.waveTeam && current.block.isVisible()) || (current.block.unlockedNowHost() && current.block.environmentBuildable() && current.block.isPlaceable()));
            Vars.webBuildStage = 30;

            if(!(tile.build instanceof ConstructBuild cb)){
                Vars.webBuildStage = 35;''',
        "allow-build",
    ),
    (
        '''                            Build.beginPlace(self(), current.block, team, current.x, current.y, current.rotation, current.block.instantBuild ? current.config : null);

                            if(!net.client() && current.block.instantBuild){''',
        '''                            Vars.webBuildStage = 40;
                            Build.beginPlace(self(), current.block, team, current.x, current.y, current.rotation, current.block.instantBuild ? current.config : null);
                            Vars.webBuildStage = 50;

                            if(!net.client() && current.block.instantBuild){''',
        "begin-place",
    ),
    (
        '''            if(tile.build instanceof ConstructBuild && !current.initialized){
                Events.fire(new BuildSelectEvent(tile, team, self(), current.breaking));
''',
        '''            Vars.webBuildStage = 55;
            if(tile.build instanceof ConstructBuild && !current.initialized){
                Events.fire(new BuildSelectEvent(tile, team, self(), current.breaking));
''',
        "select-event",
    ),
    (
        '''            if(!(tile.build instanceof ConstructBuild entity)){
                continue;
            }

            float bs =''',
        '''            Vars.webBuildStage = 60;
            if(!(tile.build instanceof ConstructBuild entity)){
                continue;
            }
            Vars.webBuildStage = 65;

            float bs =''',
        "construct-entity",
    ),
    (
        '''            }else if(allowBuildCurrent){ //only allow building unlocked blocks
                entity.construct(self(), core, bs, current.config);
            }

            current.stuck =''',
        '''            }else if(allowBuildCurrent){ //only allow building unlocked blocks
                Vars.webBuildStage = 70;
                entity.construct(self(), core, bs, current.config);
                Vars.webBuildStage = 80;
            }

            current.stuck =''',
        "construct-call",
    ),
]
for old, new, label in replacements:
    if builder.count(old) != 1:
        raise SystemExit(f"Builder stage diagnostic anchor no longer matches patched source ({label})")
    builder = builder.replace(old, new, 1)
BUILDER.write_text(builder, encoding="utf-8")

application = APPLICATION.read_text(encoding="utf-8")
app_old = '''            BrowserCanvas.setStatus("error", "Mindustry Web frame loop failed at " + phase + " #" + callbackIndex + ": " + describe(error));
'''
app_new = '''            BrowserCanvas.setStatus("error", "Mindustry Web frame loop failed at " + phase + " #" + callbackIndex +
                " [webBuildStage=" + Vars.webBuildStage + "]: " + describe(error));
'''
if application.count(app_old) != 1:
    raise SystemExit("BrowserApplication builder-stage error anchor no longer matches source")
APPLICATION.write_text(application.replace(app_old, app_new, 1), encoding="utf-8")

print("Added zero-catch integer builder stage diagnostics to Vars/BuilderComp/browser frame error")
