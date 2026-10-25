#!/usr/bin/env python3
"""Execute the actual injected Java policy and verify pinned patch guards."""
import importlib.util,re,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('keybind_patch',root/'scripts/patch-browser-keybinds-web.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
fixture='public class KeybindDialog extends Dialog{\n        for(KeyBind keybind : KeyBind.all){\n'
patched=module.patch(fixture)
assert 'if(!webSupportsKeybind(keybind.name)) continue;' in patched
for invalid in ['',fixture+fixture,patched]:
    try:module.patch(invalid)
    except SystemExit:pass
    else:raise AssertionError('Changed/duplicate upstream anchor accepted')
method=re.search(r'    private static boolean webSupportsKeybind\(String name\)\{.*?\n    }',patched,re.S).group()
# All ordinary gameplay categories must remain configurable, including actions
# with contextual consumers (single-player pings, debug hitboxes, editor teleport).
retained=['move_x','move_y','select','deselect','pause','menu','research','planet_map','minimap',
 'skip_wave','debug_hitboxes','ping','teleport_cursor','toggle_menus','block_info','schematic_menu',
 'unit_command_move','unit_stance_hold_fire','pickupCargo','dropCargo','block_select_01']
def strings(items):return ','.join('"'+x+'"' for x in items)
java='''public class KeybindPolicy {
'''+method+'''
 public static void main(String[] args){
  for(String name:new String[]{'''+strings(module.UNSUPPORTED)+'''})
   if(webSupportsKeybind(name)) throw new AssertionError("Unsupported exposed: "+name);
  for(String name:new String[]{'''+strings(retained)+'''})
   if(!webSupportsKeybind(name)) throw new AssertionError("Gameplay hidden: "+name);
  System.out.println("Actual browser keybind policy: absent systems hidden; gameplay retained; strict patch anchors PASS");
 }
}
'''
with tempfile.TemporaryDirectory() as temp:
    path=Path(temp)/'KeybindPolicy.java';path.write_text(java)
    subprocess.run(['java',str(path)],check=True)
