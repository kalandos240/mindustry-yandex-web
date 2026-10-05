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
web_methods = '''    /** Lightweight CI/runtime breadcrumb for the lean Web playing loop. */
    public int webPhase;
    /** Subphase inside a LogicBlock update when diagnosing privileged map processors. */
    public int webBuildPhase;

    /** Web transition path: exact stock Logic.update semantics while state is menu. */
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

        webPhase = 1;
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
        webPhase = 2;
        state.teams.updateTeamStats();
        state.enemies = state.teams.webWaveEnemies;
        // Web never installs the desktop/network MapPreviewLoader reflection callbacks;
        // do not retain or poll that no-op preview bridge in the gameplay hot path.

        webPhase = 4;
        Time.update();
        logicVars.update();

        if(!state.isEditor()){
            state.rules.objectives.update();
        }

        // Weather is asserted absent above; retain the stock base rule attributes.
        state.envAttrs.clear();
        state.envAttrs.add(state.rules.attributes);

        webPhase = 6;
        updateEntities();

        webPhase = 7;
        Events.fire(Trigger.afterGameUpdate);

        PerfCounter.stateUpdate.end(PerfCounter.entityUpdate.latestValueNs());
    }

    @Override
    public void update(){
'''
if marker not in text:
    raise SystemExit("Logic Web transition-path insertion no longer matches pinned upstream")
text = text.replace(marker, web_methods, 1)

# Refine the lean Web entity-update failure boundary without retaining stack traces.
# Values 61..68 identify the last entered stock updateEntities slice.
old_entities = '''    protected void updateEntities(){
        PerfCounter.entityUpdate.begin();

        PerfCounter.entityMisc.begin();
        Groups.updatePooling();
        Groups.bullet.updatePhysics();
        Groups.unit.updatePhysics();
        Groups.all.update();
        PerfCounter.entityMisc.end();

        PerfCounter.unitUpdate.begin();
        Groups.unit.update();
        PerfCounter.unitUpdate.end();

        PerfCounter.powerUpdate.begin();
        if(!state.isEditor()) Groups.powerGraph.update();
        PerfCounter.powerUpdate.end();

        PerfCounter.buildingUpdate.begin();
        if(!state.isEditor()) Groups.build.update();
        PerfCounter.buildingUpdate.end();

        PerfCounter.bulletUpdate.begin();
        Groups.bullet.update();

        Groups.bullet.collide();
        PerfCounter.bulletUpdate.end();

        PerfCounter.entityUpdate.end();
    }
'''
new_entities = '''    protected void updateEntities(){
        PerfCounter.entityUpdate.begin();

        PerfCounter.entityMisc.begin();
        webPhase = 61;
        Groups.updatePooling();
        webPhase = 62;
        Groups.bullet.updatePhysics();
        webPhase = 63;
        Groups.unit.updatePhysics();
        webPhase = 64;
        Groups.all.update();
        PerfCounter.entityMisc.end();

        webPhase = 65;
        PerfCounter.unitUpdate.begin();
        Groups.unit.update();
        PerfCounter.unitUpdate.end();

        webPhase = 66;
        PerfCounter.powerUpdate.begin();
        if(!state.isEditor()) Groups.powerGraph.update();
        PerfCounter.powerUpdate.end();

        webPhase = 67;
        PerfCounter.buildingUpdate.begin();
        if(!state.isEditor()) Groups.build.update();
        PerfCounter.buildingUpdate.end();

        webPhase = 68;
        PerfCounter.bulletUpdate.begin();
        Groups.bullet.update();

        Groups.bullet.collide();
        PerfCounter.bulletUpdate.end();

        PerfCounter.entityUpdate.end();
    }
'''
if text.count(old_entities) != 1:
    raise SystemExit("Logic Web entity subphase trace anchor no longer matches pinned upstream")
text = text.replace(old_entities, new_entities, 1)

PATH.write_text(text, encoding="utf-8")

LOGIC_BLOCK = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "world" / "blocks" / "logic" / "LogicBlock.java"
logic_block = LOGIC_BLOCK.read_text(encoding="utf-8")
logic_block_replacements = [
    (
        '''        public void updateTile(){
            checkReadCode();

            executor.team = team;
''',
        '''        public void updateTile(){
            if(logic != null) logic.webBuildPhase = 1;
            checkReadCode();

            if(logic != null) logic.webBuildPhase = 2;
            executor.team = team;
''',
        "logic-build read/team",
    ),
    (
        '''            //check for previously invalid links to add after configuration
            boolean changed = false, updates = true;
''',
        '''            //check for previously invalid links to add after configuration
            if(logic != null) logic.webBuildPhase = 3;
            boolean changed = false, updates = true;
''',
        "logic-build links",
    ),
    (
        '''            if(changed){
                updateLinks();
            }

            if(!privileged){
''',
        '''            if(changed){
                if(logic != null) logic.webBuildPhase = 4;
                updateLinks();
            }

            if(logic != null) logic.webBuildPhase = 5;
            if(!privileged){
''',
        "logic-build link refresh",
    ),
    (
        '''                while(accumulator >= 1f){
                    executor.runOnce();
''',
        '''                while(accumulator >= 1f){
                    // Encode the exact mlog instruction about to execute without retaining
                    // instruction class names or another diagnostic field in TeaVM output.
                    if(logic != null) logic.webBuildPhase = 6000 + (int)executor.counter.numval;
                    executor.runOnce();
                    if(logic != null) logic.webBuildPhase = 7;
''',
        "logic-build executor",
    ),
]
for old, new, label in logic_block_replacements:
    if logic_block.count(old) != 1:
        raise SystemExit(f"LogicBlock Web subphase trace anchor no longer matches pinned upstream ({label})")
    logic_block = logic_block.replace(old, new, 1)
LOGIC_BLOCK.write_text(logic_block, encoding="utf-8")

# Basin's pinned world processor reaches its nuclear strike through stock privileged
# mlog. Keep this temporary trace numeric-only so it is cheap in TeaVM: it narrows
# the exact synchronous explosion subphase and, if block damage throws, encodes the
# current tile as 700000 + x*1024 + y.
LEXECUTOR = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "logic" / "LExecutor.java"
lexecutor = LEXECUTOR.read_text(encoding="utf-8")
old_explosion = '''        @Override
        public void run(LExecutor exec){
            if(net.client()) return;

            Team t = team.team();
            //note that there is a radius cap
            Call.logicExplosion(t, World.unconv(x.numf()), World.unconv(y.numf()), World.unconv(Math.min(radius.numf(), 100)), damage.numf(), air.bool(), ground.bool(), pierce.bool(), effect.bool());
        }
'''
new_explosion = '''        @Override
        public void run(LExecutor exec){
            if(net.client()) return;

            if(logic != null) logic.webBuildPhase = 6400;
            Team t = team.team();
            //note that there is a radius cap
            if(logic != null) logic.webBuildPhase = 6401;
            Call.logicExplosion(t, World.unconv(x.numf()), World.unconv(y.numf()), World.unconv(Math.min(radius.numf(), 100)), damage.numf(), air.bool(), ground.bool(), pierce.bool(), effect.bool());
            if(logic != null) logic.webBuildPhase = 6405;
        }
'''
if lexecutor.count(old_explosion) != 1:
    raise SystemExit("LExecutor Web explosion trace anchor no longer matches pinned upstream")
lexecutor = lexecutor.replace(old_explosion, new_explosion, 1)

old_logic_explosion = '''    public static void logicExplosion(Team team, float x, float y, float radius, float damage, boolean air, boolean ground, boolean pierce, boolean effect){
        if(damage < 0f) return;

        Damage.damage(team, x, y, radius, damage, pierce, air, ground, true, null);
        if(effect){
            if(pierce){
                Fx.spawnShockwave.at(x, y, World.conv(radius));
            }else{
                Fx.dynamicExplosion.at(x, y, World.conv(radius) / 8f);
            }
        }
    }
'''
new_logic_explosion = '''    public static void logicExplosion(Team team, float x, float y, float radius, float damage, boolean air, boolean ground, boolean pierce, boolean effect){
        if(damage < 0f) return;

        if(logic != null) logic.webBuildPhase = 6402;
        Damage.damage(team, x, y, radius, damage, pierce, air, ground, true, null);
        if(logic != null) logic.webBuildPhase = 6403;
        if(effect){
            if(pierce){
                Fx.spawnShockwave.at(x, y, World.conv(radius));
            }else{
                Fx.dynamicExplosion.at(x, y, World.conv(radius) / 8f);
            }
        }
        if(logic != null) logic.webBuildPhase = 6404;
    }
'''
if lexecutor.count(old_logic_explosion) != 1:
    raise SystemExit("LExecutor Web logicExplosion trace anchor no longer matches pinned upstream")
LEXECUTOR.write_text(lexecutor.replace(old_logic_explosion, new_logic_explosion, 1), encoding="utf-8")

DAMAGE = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "entities" / "Damage.java"
damage_text = DAMAGE.read_text(encoding="utf-8")
old_damage_tail = '''        rect.setSize(radius * 2).setCenter(x, y);
        if(team != null){
            Units.nearbyEnemies(team, rect, cons);
        }else{
            Units.nearby(rect, cons);
        }

        if(ground){
            if(!complete){
                tileDamage(team, World.toTile(x), World.toTile(y), radius / tilesize, damage * (source == null ? 1f : source.type.buildingDamageMultiplier), source);
            }else{
                completeDamage(team, x, y, radius, damage * (source == null ? 1f : source.type.buildingDamageMultiplier));
            }
        }
'''
new_damage_tail = '''        rect.setSize(radius * 2).setCenter(x, y);
        if(logic != null) logic.webBuildPhase = 6501;
        if(team != null){
            Units.nearbyEnemies(team, rect, cons);
        }else{
            Units.nearby(rect, cons);
        }

        if(logic != null) logic.webBuildPhase = 6502;
        if(ground){
            if(!complete){
                if(logic != null) logic.webBuildPhase = 6510;
                tileDamage(team, World.toTile(x), World.toTile(y), radius / tilesize, damage * (source == null ? 1f : source.type.buildingDamageMultiplier), source);
            }else{
                if(logic != null) logic.webBuildPhase = 6520;
                completeDamage(team, x, y, radius, damage * (source == null ? 1f : source.type.buildingDamageMultiplier));
                if(logic != null) logic.webBuildPhase = 6522;
            }
        }
'''
if damage_text.count(old_damage_tail) != 1:
    raise SystemExit("Damage Web explosion trace anchor no longer matches pinned upstream")
damage_text = damage_text.replace(old_damage_tail, new_damage_tail, 1)

old_complete_hit = '''                if(tile != null && tile.build != null && (team == null || team != tile.team()) && dx*dx + dy*dy <= trad*trad){
                    tile.build.damage(team, damage);
                }
'''
new_complete_hit = '''                if(tile != null && tile.build != null && (team == null || team != tile.team()) && dx*dx + dy*dy <= trad*trad){
                    if(logic != null) logic.webBuildPhase = 700000 + tile.x * 1024 + tile.y;
                    tile.build.damage(team, damage);
                }
'''
if damage_text.count(old_complete_hit) != 1:
    raise SystemExit("Damage Web completeDamage tile trace anchor no longer matches pinned upstream")
DAMAGE.write_text(damage_text.replace(old_complete_hit, new_complete_hit, 1), encoding="utf-8")

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
