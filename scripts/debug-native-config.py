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
