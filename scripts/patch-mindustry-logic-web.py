#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "core" / "Logic.java"

if not PATH.is_file():
    raise SystemExit(f"Missing pinned Mindustry Logic source: {PATH}")

text = PATH.read_text(encoding="utf-8")
replacements = [
    (
        '''        if(Core.settings.modified() && !state.isPlaying()){
            netServer.admins.forceSave();
            Core.settings.forceSave();
        }
''',
        '''        if(Core.settings.modified() && !state.isPlaying()){
            // Web bootstrap reaches the stock Logic frame loop without any multiplayer
            // server object. Browser settings persistence remains active regardless.
            if(netServer != null) netServer.admins.forceSave();
            Core.settings.forceSave();
        }
''',
        "menu settings/admin save",
    ),
    (
        '''        }else if(netServer.isWaitingForPlayers() && runStateCheck){
            checkGameState();
        }
''',
        '''        }else if(netServer != null && netServer.isWaitingForPlayers() && runStateCheck){
            checkGameState();
        }
''',
        "waiting-for-players server check",
    ),
]

for old, new, label in replacements:
    if old not in text:
        raise SystemExit(f"Logic Web patch no longer matches pinned upstream ({label})")
    text = text.replace(old, new, 1)

# Do not call the complete update() until every optional gameplay subsystem is enabled
# on Web. TeaVM performs whole-program reachability: even rules that are deterministically
# false at runtime retain weather/wave/campaign/BaseBuilderAI/RtsAI/prebuild code when the
# stock method is called. Expose exact stock slices with explicit invariants instead.
marker = '''    @Override
    public void update(){
'''
web_methods = '''    /** Web transition path: exact stock Logic.update semantics while state is menu. */
    public void updateWebMenu(){
        if(!state.isMenu()){
            throw new IllegalStateException("updateWebMenu may only run in menu state");
        }

        PerfCounter.frame.end();
        PerfCounter.frame.begin();

        PerfCounter.stateUpdate.begin();

        Events.fire(Trigger.update);
        universe.updateGlobal();

        if(Core.settings.modified()){
            if(netServer != null) netServer.admins.forceSave();
            Core.settings.forceSave();
        }

        PerfCounter.stateUpdate.end(PerfCounter.entityUpdate.latestValueNs());
    }

    /**
     * Web transition smoke for the stock GameState clock portion of an unpaused tick.
     * Uses a temporary isolated GameState so no transition events fire and no test
     * state leaks into the real browser session. GlobalVars, team/entity aggregation,
     * fog, waves and AI are deliberately separate milestones.
     * @return the temporary state's updateId after exactly one state tick.
     */
    public long updateWebGameStateSmoke(){
        if(!state.isMenu()){
            throw new IllegalStateException("updateWebGameStateSmoke requires the real browser state to remain menu");
        }

        GameState previous = state;
        GameState smoke = new GameState();
        smoke.rules.fog = false;
        smoke.rules.waves = false;
        smoke.rules.canGameOver = false;
        smoke.rules.editor = false;

        state = smoke;
        try{
            float delta = Core.graphics.getDeltaTime();
            state.tick += Float.isNaN(delta) || Float.isInfinite(delta) ? 0f : delta * 60f;
            state.updateId ++;

            if(state.updateId != 1L || state.tick < 0d){
                throw new IllegalStateException("Web GameState tick smoke produced invalid state");
            }
            return state.updateId;
        }finally{
            state = previous;
        }
    }

    /**
     * Real single-player playing tick for the first Web gameplay gate.
     *
     * This is copied in-order from the production Logic.update() playing branch, but
     * omits only optional branches that are asserted impossible for this deterministic
     * browser world. The method still advances the real GameState clock, team stats,
     * GlobalVars, Time, objectives, environment attributes, entity physics/updates and
     * before/after update events. Keeping impossible AI/weather/wave/campaign branches
     * out of this entry point prevents TeaVM from retaining several MiB of code that
     * cannot execute in this milestone.
     */
    public void updateWebPlayingCore(){
        if(!state.isPlaying()){
            throw new IllegalStateException("updateWebPlayingCore requires real playing state");
        }
        if(state.isCampaign() || state.rules.fog || state.rules.waves || state.rules.attackMode
        || state.rules.pvp || state.rules.canGameOver || state.rules.weather.size != 0
        || Groups.weather.size() != 0){
            throw new IllegalStateException("Web playing core received an optional gameplay subsystem that is not enabled yet");
        }
        for(TeamData data : state.teams.getActive()){
            var rules = data.team.rules();
            if(rules.fillItems || rules.buildAi || rules.rtsAi || rules.prebuildAi){
                throw new IllegalStateException("Web playing core received team AI/fill rules before that milestone is enabled");
            }
        }

        PerfCounter.frame.end();
        PerfCounter.frame.begin();

        PerfCounter.stateUpdate.begin();

        Events.fire(Trigger.update);
        universe.updateGlobal();

        Events.fire(Trigger.beforeGameUpdate);

        float delta = Core.graphics.getDeltaTime();
        state.tick += Float.isNaN(delta) || Float.isInfinite(delta) ? 0f : delta * 60f;
        state.updateId ++;
        // updateTeamStats already walks Groups.unit; the Web Teams patch folds the exact
        // top-level wave-team/isEnemy count into that mandatory pass.
        state.teams.updateTeamStats();
        state.enemies = state.teams.webWaveEnemies;
        MapPreviewLoader.checkPreviews();

        Time.update();
        logicVars.update();

        if(!state.isEditor()){
            state.rules.objectives.update();
        }

        // Weather is asserted absent above; retain the stock base rule attributes.
        state.envAttrs.clear();
        state.envAttrs.add(state.rules.attributes);

        updateEntities();

        Events.fire(Trigger.afterGameUpdate);

        PerfCounter.stateUpdate.end(PerfCounter.entityUpdate.latestValueNs());
    }

    @Override
    public void update(){
'''
if marker not in text:
    raise SystemExit("Logic Web transition-path insertion no longer matches pinned upstream")
text = text.replace(marker, web_methods, 1)

PATH.write_text(text, encoding="utf-8")

# Stock Logic scans Groups.unit once for state.enemies immediately before Teams scans
# the same group again for per-team caches. Web folds the exact top-level wave-team
# enemy count into updateTeamStats(), avoiding a second O(total units) pass each frame.
TEAMS = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "game" / "Teams.java"
teams = TEAMS.read_text(encoding="utf-8")

old_field = '''    /** Current boss units. */
    public Seq<Unit> bosses = new Seq<>();
'''
new_field = '''    /** Current boss units. */
    public Seq<Unit> bosses = new Seq<>();
    /** Web: top-level wave-team units whose UnitType is marked enemy, refreshed with team stats. */
    public int webWaveEnemies;
'''
if old_field not in teams:
    raise SystemExit("Teams Web enemy-count field anchor no longer matches pinned upstream")
teams = teams.replace(old_field, new_field, 1)

old_clear = '''    public void updateTeamStats(){
        present.clear();
        bosses.clear();
'''
new_clear = '''    public void updateTeamStats(){
        present.clear();
        bosses.clear();
        webWaveEnemies = 0;
'''
if old_clear not in teams:
    raise SystemExit("Teams Web enemy-count reset anchor no longer matches pinned upstream")
teams = teams.replace(old_clear, new_clear, 1)

old_loop = '''        for(Unit unit : Groups.unit){
            if(unit.type == null) continue;
            TeamData data = unit.team.data();
'''
new_loop = '''        for(Unit unit : Groups.unit){
            if(unit.type == null) continue;
            if(unit.team == state.rules.waveTeam && unit.isEnemy()) webWaveEnemies++;
            TeamData data = unit.team.data();
'''
if old_loop not in teams:
    raise SystemExit("Teams Web enemy-count loop anchor no longer matches pinned upstream")
teams = teams.replace(old_loop, new_loop, 1)
TEAMS.write_text(teams, encoding="utf-8")

print("Applied Web-safe Logic + single-pass Teams enemy counting")
