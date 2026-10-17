from pathlib import Path
p=Path("web-runtime/build/web/mindustry.js")
s=p.read_text()
injections={
"mg_Building_configure = ($this, $value) => {": "console.info('CONFIG_CALL', $value && $value.constructor.name, $this.$sortItem0 && $this.$sortItem0.constructor.name);",
"mi_InputHandler_tileConfig = ($player, $build, $value) => {": "console.info('CONFIG_HANDLER', $value && $value.constructor.name);",
"mg_Building_configured = ($this, $builder, $value) => {": "console.info('CONFIGURED', $value && $value.constructor.name);",
"mwb_ItemSelection$lambda$buildTable$4$lambda$_13_2_run = var$0 => {": "console.info('ITEM_CHANGED', var$0.$_1161.$isChecked, var$0.$_268 && var$0.$_268.constructor.name);",
"asu_Button_setChecked0 = ($this, $isChecked, $fireEvent) => {": "if($fireEvent && document.documentElement.hasAttribute('data-mindustry-building-config-fixture'))console.info('BUTTON_CLICK', $this.constructor.name, $isChecked, $this.$name, $this.$x, $this.$y);"
}
for anchor,extra in injections.items():
    assert s.count(anchor)==1,anchor
    s=s.replace(anchor,anchor+"\n    "+extra)
p.write_text(s)
p=Path("scripts/verify-browser-wave-hud-stress.sh")
s=p.read_text().replace("&mindustryWaveHudSmoke=1","&mindustryCampaignMapMatrix=1&mindustryWaveHudSmoke=1")
p.write_text(s)

# Observe the actual native target and listener without redirecting input.
s=p.read_text() if False else Path("web-runtime/build/web/mindustry.js").read_text()
for anchor,label in [
("as_Scene_touchDown = ($this, $screenX, $screenY, $pointer, $button) => {","SCENE_DOWN"),
("as_Scene_touchUp = ($this, $screenX, $screenY, $pointer, $button) => {","SCENE_UP")]:
    assert s.count(anchor)==1
    s=s.replace(anchor,anchor+"\n if(document.documentElement.hasAttribute('data-mindustry-building-config-fixture'))console.info('"+label+"', $screenX, $screenY);")
a=s.index("as_Scene_touchDown =")
b=s.index("as_Scene_touchDragged =",a)
part=s[a:b]
anchor="        $target = $tmp;" + chr(10) + "        if ($target !== null)"
assert part.count(anchor)==1
part=part.replace(anchor,"        $target = $tmp;\n if(document.documentElement.hasAttribute('data-mindustry-building-config-fixture'))console.info('SCENE_TARGET', $target && $target.constructor.name, $target && $target.$name, document.documentElement.getAttribute('data-mindustry-cdp-click-request'));\n        if ($target !== null)")
s=s[:a]+part+s[b:]
anchor="ase_ClickListener_touchUp = ($this, $event, $x, $y, $pointer, $button) => {"
assert s.count(anchor)==1
s=s.replace(anchor,anchor+"\n if(document.documentElement.hasAttribute('data-mindustry-building-config-fixture'))console.info('LISTENER_UP', $x,$y,$this.$pressed);")
Path("web-runtime/build/web/mindustry.js").write_text(s)

# Diagnostic A/B only: refresh the cached hover at the native hit boundary.
p=Path("web-runtime/build/web/mindustry.js")
s=p.read_text()
a=s.index("as_Scene_touchDown =")
b=s.index("as_Scene_touchDragged =",a)
part=s[a:b]
anchor="        $target = $tmp;" + chr(10) + " if(document.documentElement"
assert part.count(anchor)==1
part=part.replace(anchor,"        $target = $tmp;" + chr(10) + "        $this.$mouseOverElement = $target;" + chr(10) + " if(document.documentElement")
p.write_text(s[:a]+part+s[b:])

# Save the actual stopped surface before Chrome is terminated, including failures.
p=Path("scripts/chrome-wait-dom.py")
s=p.read_text()
anchor="    finally:" + chr(10) + "        input_bridge_stop.set()"
replacement="""    finally:
        if ws is not None:
            try:
                name = Path(args.profile).name
                diagnostic = Path('work/diagnose')
                diagnostic.mkdir(parents=True, exist_ok=True)
                dom = evaluate(ws, message_id + 100000, 'document.documentElement.outerHTML')
                (diagnostic / (name + '.html')).write_text(dom)
                screenshot = cdp_command(ws, message_id + 100001, 'Page.captureScreenshot', {'format':'png'})
                (diagnostic / (name + '.png')).write_bytes(base64.b64decode(screenshot['result']['data']))
            except Exception as error:
                sys.stderr.write('Diagnostic capture failed: ' + str(error) + '\\n')
        input_bridge_stop.set()"""
assert s.count(anchor)==1
p.write_text(s.replace(anchor,replacement))
