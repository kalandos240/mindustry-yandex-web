#!/usr/bin/env python3
"""Hide bindings for absent browser systems without changing saved bindings."""
from pathlib import Path

UNSUPPORTED = ('fullscreen', 'screenshot', 'player_list', 'chat', 'chat_history_prev',
               'chat_history_next', 'chat_scroll', 'chat_mode', 'console', 'performance_metrics')


def patch(source):
    if "webSupportsKeybind" in source:
        raise SystemExit("Browser keybind policy already present; require clean pinned source")
    declaration = 'public class KeybindDialog extends Dialog{\n'
    loop = '        for(KeyBind keybind : KeyBind.all){\n'
    for anchor in (declaration, loop):
        if source.count(anchor) != 1:
            raise SystemExit('Pinned KeybindDialog browser filter anchor changed: ' + repr(anchor))
    cases = ', '.join('"' + name + '"' for name in UNSUPPORTED)
    policy = '''
    // Web single-player has no desktop screenshot/fullscreen handler, peer/chat
    // fragments, developer console or performance fragment. Keep the registry
    // intact so existing saved mappings and every gameplay binding survive.
    private static boolean webSupportsKeybind(String name){
        return switch(name){
            case ''' + cases + ''' -> false;
            default -> true;
        };
    }
'''
    source = source.replace(declaration, declaration + policy, 1)
    return source.replace(loop, loop + '            if(!webSupportsKeybind(keybind.name)) continue;\n', 1)


if __name__ == '__main__':
    root = Path(__file__).resolve().parents[1]
    path = root / 'work/Mindustry/core/src/mindustry/ui/dialogs/KeybindDialog.java'
    path.write_text(patch(path.read_text(encoding='utf-8')), encoding='utf-8')
    print('Browser keybinds: absent desktop/multiplayer/developer actions hidden; gameplay bindings retained')
