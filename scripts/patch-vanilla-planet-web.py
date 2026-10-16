#!/usr/bin/env python3
"""Pinned-v159.7 vanilla PlanetDialog: leave original 3D view and sector UI intact.

Only three desktop-only assumptions are adapted. Web uses an intentionally
minimal UI shell without a minimap fragment or campaign rules editor.
"""
from pathlib import Path

path = Path(__file__).resolve().parents[1] / "work/Mindustry/core/src/mindustry/ui/dialogs/PlanetDialog.java"
if not path.is_file():
    raise SystemExit(f"PlanetDialog source missing: {path}")
text = path.read_text(encoding="utf-8")
changes = {
    "        ui.minimapfrag.hide();": "        if(ui.minimapfrag != null) ui.minimapfrag.hide();",
    "                Vars.ui.campaignRules.show(state.planet);": "                if(Vars.ui.campaignRules != null) Vars.ui.campaignRules.show(state.planet);",
    ".visible(() -> state.planet.allowCampaignRules && mode != planetLaunch)":
        ".visible(() -> ui.campaignRules != null && state.planet.allowCampaignRules && mode != planetLaunch)",
}
for old, new in changes.items():
    if text.count(old) != 1:
        raise SystemExit(f"PlanetDialog pinned shape mismatch: {old!r}; count={text.count(old)}")
    text = text.replace(old, new, 1)

# CI-only construction phase diagnostics. The Web renderer has a much
# smaller UI object graph than the desktop launcher and JavaScript stack traces
# are not reliably available from TeaVM. The preference is an ephemeral marker
# read by BrowserVanillaDialogs if an exception interrupts constructor startup.
phase_changes = [
    ('        super("", Styles.fullDialog);\n',
     '        super("", Styles.fullDialog);\n        settings.put("web-planet-ctor-phase", "body-start");\n'),
    ('        state.renderer = this;\n',
     '        settings.put("web-planet-ctor-phase", "state");\n        state.renderer = this;\n'),
    ('        hoverLabel.setAlignment(Align.center);\n\n        rebuildButtons();\n',
     '        hoverLabel.setAlignment(Align.center);\n\n'
     '        settings.put("web-planet-ctor-phase", "buttons");\n        rebuildButtons();\n'
     '        settings.put("web-planet-ctor-phase", "buttons-ready");\n'),
    ('        planetTextures = new Texture[2];\n',
     '        settings.put("web-planet-ctor-phase", "planet-textures");\n'
     '        planetTextures = new Texture[2];\n'),
    ('            assets.finishLoadingAsset(names[i]);\n',
     '            settings.put("web-planet-ctor-phase", "loading-portrait-" + i);\n'
     '            assets.finishLoadingAsset(names[i]);\n'
     '            settings.put("web-planet-ctor-phase", "portrait-loaded-" + i);\n'),
]
for old, new in phase_changes:
    if text.count(old) != 1:
        raise SystemExit(f"PlanetDialog constructor phase marker mismatch: {old!r}; count={text.count(old)}")
    text = text.replace(old, new, 1)

# Use the actual upstream planet-switch buttons for browser pointer testing.
# Giving each button an Arc Scene name does not alter its layout or behavior.
old_planet_button = '''                        planetButton.getChildren().get(1).setColor(planet.iconColor);'''
new_planet_button = '''                        planetButton.name = "web-planet-choice-" + planet.name;
                        planetButton.getChildren().get(1).setColor(planet.iconColor);'''
if text.count(old_planet_button) != 1:
    raise SystemExit("Pinned PlanetDialog planet buttons changed")
text = text.replace(old_planet_button, new_planet_button, 1)

# In the lean Yandex viewport the original star/planet list can exceed its
# allotted column, placing later buttons beneath other native scene buttons.
# Bound the *same upstream ScrollPane* to the actual Arc scaled viewport. This
# keeps all original list content scrollable and fixes hitbox occlusion.
old_pane = "            t.add(pane).colspan(2).row();"
new_pane = ("            t.add(pane).colspan(2).width(228f)"
            ".height(Math.max(112f, Math.min(216f,"
            " Core.graphics.getHeight() / Scl.scl(1f) * 0.43f))).row();")
if text.count(old_pane) != 1:
    raise SystemExit(f"Pinned planet selector scroll-pane anchor changed: {text.count(old_pane)}")
text = text.replace(old_pane, new_pane, 1)

# Yandex/TeaVM viewport: the upstream full-screen BaseDialog footer has a
# larger touch region than the planet-selector column and captures taps at
# its scroll-list entries. Keep the original footer controls, but prevent its
# empty Table area from taking touches away from the upstream selection stack.
# Actual child Back and TechTree buttons remain Touchable.enabled.
old_footer = '        buttons.bottom();'
new_footer = '        buttons.bottom();\n        buttons.touchable = Touchable.childrenOnly;'
if text.count(old_footer) != 1:
    raise SystemExit(f"Pinned PlanetDialog footer anchor mismatch: {text.count(old_footer)}")
text = text.replace(old_footer, new_footer, 1)

# In the original Stack, noninteractive layout containers should pass pointer
# hits through their unused bounds while their real child buttons stay active.
old_selector = '''        new Table(t -> {
            t.top().left();
            ScrollPane pane = new ScrollPane(null, Styles.smallPane);'''
new_selector = '''        new Table(t -> {
            t.touchable = Touchable.childrenOnly;
            t.top().left();
            ScrollPane pane = new ScrollPane(null, Styles.smallPane);'''
if text.count(old_selector) != 1:
    raise SystemExit("Pinned planet selector table anchor mismatch")
text = text.replace(old_selector, new_selector, 1)

# Preserve the upstream first-time campaign chooser. It is an extra
# BaseDialog above the 3D map until the user picks Serpulo or Erekir, so
# Chrome must click it before accessing the map's own planet selector.

# Capture Arc Scene touchDown without consuming any events. DOM pointer
# delivery alone cannot prove that Scene received the button input.
old_intro_dialog = '                var diag = new BaseDialog("@campaign.select");'
new_intro_dialog = '''                var diag = new BaseDialog("@campaign.select");
                // Capture the actual Scene target before child actors can
                // stop event bubbling, then unregister when chooser closes.
                InputListener rootProbe = new InputListener(){
                    @Override
                    public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                        String target = event.targetActor == null ? "null" :
                            event.targetActor.name == null ? event.targetActor.getClass().getSimpleName() :
                            event.targetActor.name;
                        settings.put("web-first-root-down", target + "@" +
                            Math.round(event.stageX) + "," + Math.round(event.stageY));
                        return false;
                    }
                };
                scene.root.addCaptureListener(rootProbe);
                diag.hidden(() -> scene.root.removeCaptureListener(rootProbe));
                diag.addListener(new InputListener(){
                    @Override
                    public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                        String target = event.targetActor == null ? "null" :
                            event.targetActor.name == null ? event.targetActor.getClass().getSimpleName() :
                            event.targetActor.name;
                        settings.put("web-first-scene-down", target + "@" +
                            Math.round(event.stageX) + "," + Math.round(event.stageY));
                        return false;
                    }
                });'''
if text.count(old_intro_dialog) != 1:
    raise SystemExit("Pinned first-launch native dialog changed")
text = text.replace(old_intro_dialog, new_intro_dialog, 1)

old_intro_choice = '''                    }, Styles.togglet, () -> selected[0] = planet).size(mobile ? 220f : 320f).group(group);'''
new_intro_choice = '''                    }, Styles.togglet, () -> {
                        selected[0] = planet;
                        settings.put("web-first-choice", planet.name);
                    }).size(Math.min(mobile ? 220f : 320f,
                        Math.max(110f, Core.graphics.getHeight() / Scl.scl(1f) * 0.28f)))
                        .group(group).name("web-campaign-first-choice-" + planet.name);'''
if text.count(old_intro_choice) != 1:
    raise SystemExit("Pinned first-campaign vanilla choice shape changed")
text = text.replace(old_intro_choice, new_intro_choice, 1)

old_intro_confirm = '''                }).size(300f, 64f).disabled(b -> selected[0] == null);'''
new_intro_confirm = '''                }).size(300f, 64f).disabled(b -> selected[0] == null).name("web-campaign-first-confirm");'''
if text.count(old_intro_confirm) != 1:
    raise SystemExit("Pinned first-campaign vanilla confirm shape changed")
text = text.replace(old_intro_confirm, new_intro_confirm, 1)

# Trace the actual first-run campaign choice and confirmation callbacks,
# rather than assuming that pointer events reached the desired Arc controls.
# These temporary settings are browser-local diagnostic keys.
old_intro_ok = '''                diag.buttons.button("@ok", Icon.ok, () -> {
                    state.planet = selected[0];'''
new_intro_ok = '''                diag.buttons.button("@ok", Icon.ok, () -> {
                    settings.put("web-first-ok", selected[0] == null ? "no-selection" : selected[0].name);
                    state.planet = selected[0];'''
if text.count(old_intro_ok) != 1:
    raise SystemExit("Pinned campaign first-run confirm callback changed")
text = text.replace(old_intro_ok, new_intro_ok, 1)

# If the first-run OK handler throws before diag.hide(), the overlapping
# dialog remains visible. Mark every native operation to identify the exact
# failing campaign transition without bypassing any stock logic.
old_intro_steps = """                    state.planet = selected[0];
                    lookAt(state.planet.getStartSector());
                    selectSector(state.planet.getStartSector());
                    settings.put("campaignselect", true);
                    diag.hide();"""
new_intro_steps = """                    try{
                        settings.put("web-first-stage", "setting-planet");
                        state.planet = selected[0];
                        settings.put("web-first-stage", "looking-at-start-sector");
                        lookAt(state.planet.getStartSector());
                        settings.put("web-first-stage", "selecting-start-sector");
                        selectSector(state.planet.getStartSector());
                        settings.put("web-first-stage", "saving-campaign-selection");
                        settings.put("campaignselect", true);
                        settings.put("web-first-stage", "hiding-dialog");
                        diag.hide();
                        settings.put("web-first-stage", "dialog-hidden");
                    }catch(Throwable failure){
                        settings.put("web-first-stage", "failed-" + failure.getClass().getName()
                            + "-" + failure.getMessage());
                        Log.err(failure);
                    }"""
if text.count(old_intro_steps) != 1:
    raise SystemExit(f"Original first-run OK operation sequence changed: {text.count(old_intro_steps)}")
text = text.replace(old_intro_steps, new_intro_steps, 1)

# TeaVM's Arc input correctly clicks the intro campaign portrait (inside
# dialog.cont), but native desktop footer buttons in dialog.buttons never
# receive a completed ClickListener click on the small browser viewport.
# Keep the same original OK button and its untouched sector-selection lambda;
# mount it as a final row in the already interactive dialog.cont table.
old_intro_ok_container = '''                diag.buttons.button("@ok", Icon.ok, () -> {'''
new_intro_ok_container = '''                diag.cont.row();
                diag.cont.button("@ok", Icon.ok, () -> {'''
if text.count(old_intro_ok_container) != 1:
    raise SystemExit("Pinned first-run campaign OK footer unexpectedly changed")
text = text.replace(old_intro_ok_container, new_intro_ok_container, 1)
old_intro_ok_cell = '''                }).size(300f, 64f).disabled(b -> selected[0] == null).name("web-campaign-first-confirm");'''
new_intro_ok_cell = '''                }).size(260f, 48f).colspan(2).padTop(4f)
                    .disabled(b -> selected[0] == null).name("web-campaign-first-confirm");'''
if text.count(old_intro_ok_cell) != 1:
    raise SystemExit("Pinned first-run campaign OK button cell changed")
text = text.replace(old_intro_ok_cell, new_intro_ok_cell, 1)


# After choosing a planet the localized campaign description expands, and
# the stock OK button at the bottom becomes inaccessible on short viewports.
# Reorder only the same original OK button row before the description.
ok_begin = '                diag.cont.row();\n                diag.cont.button("@ok", Icon.ok, () -> {'
ok_end = '.disabled(b -> selected[0] == null).name("web-campaign-first-confirm");'
if text.count(ok_begin) != 1 or text.count(ok_end) != 1:
    raise SystemExit("Pinned first-campaign confirm relocation anchors changed")
ok_start = text.index(ok_begin)
ok_stop = text.index(ok_end, ok_start) + len(ok_end)
ok_row = text[ok_start:ok_stop]
text = text[:ok_start] + text[ok_stop:]
description_row = ('                diag.cont.row();\n'
                   '                diag.cont.label(() -> selected[0] == null ? "@campaign.none" : "@campaign." + selected[0].name)')
if text.count(description_row) != 1:
    raise SystemExit("Pinned first-campaign description anchor changed")
text = text.replace(description_row, ok_row + '\n\n' + description_row, 1)

path.write_text(text, encoding="utf-8")
print("Pinned stock 3D PlanetDialog: minimized UI shell compatibility; full draw/camera/pick preserved")
