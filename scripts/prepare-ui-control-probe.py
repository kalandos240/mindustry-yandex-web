#!/usr/bin/env python3
"""Inject a read-only Arc control inventory into a *test copy* of the TeaVM bundle.

Install after prepare-qa-probe.py and restore in reverse order. No Java compiler
changes or gameplay smoke flags are used; the packaged runtime must be restored
and hash checked by the caller before delivery.
"""
import argparse
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANCHOR = '$rt_exports.main = $rt_export_main;'
MARKER = '/* MINDUSTRY_TEST_UI_CONTROL_PROBE */'


def constructor_fields(source, name, required=True):
    match = re.search(r'function ' + re.escape(name) + r'\(\)\s*\{(.*?)\n\}', source, re.S)
    if not match:
        if required:
            raise ValueError('Missing compiled constructor: ' + name)
        return {}
    out = {}
    for field in re.findall(r'\b(?:a|this)\.\$(\w+)\s*=', match.group(1)):
        key = re.sub(r'\d+$', '', field)
        if key in out:
            raise ValueError(f'Ambiguous field {name}.{key}')
        out[key] = '$' + field
    return out


def schema(source):
    classes = {
        'element': 'as_Element', 'group': 'as_Group', 'seq': 'as_Seq', 'scene': 'as_Scene',
        'button': 'asu_Button', 'slider': 'asu_ProgressBar',
        'textField': 'asu_TextField', 'label': 'asu_Label',
        'builder': 'jl_AbstractStringBuilder', 'dialog': 'asu_Dialog',
        'image': 'asu_Image', 'scroll': 'asu_ScrollPane', 'color': 'ag_Color',
        'rect': 'amg_Rect', 'vec': 'amg_Vec2',
        'setting': 'mud_SettingsMenuDialog$SettingsTable$Setting',
        'contentLoader': 'mc_ContentLoader', 'mappable': 'mc_MappableContent',
        'unlockable': 'mc_UnlockableContent', 'regionDrawable': 'ass_TextureRegionDrawable',
    }
    result = {key: constructor_fields(source, value) for key, value in classes.items()}
    required = {
        'element': ['name', 'visible', 'parent', 'width', 'height', 'touchable', 'color', 'listeners'],
        'group': ['children'], 'seq': ['items', 'size'], 'scene': ['root'],
        'button': ['isChecked', 'isDisabled'],
        'slider': ['value', 'min', 'max', 'stepSize', 'vertical', 'disabled'],
        'textField': ['text'], 'label': ['text'], 'builder': ['buffer', 'length'],
        'dialog': ['title', 'isModal'], 'image': ['drawable'],
        'scroll': ['widgetAreaBounds', 'maxX', 'maxY', 'amountX', 'amountY'],
        'color': ['a'], 'rect': ['x', 'y', 'width', 'height'], 'vec': ['x', 'y'],
        'setting': ['name', 'title'],
        'contentLoader': ['contentMap'], 'mappable': ['name'],
        'unlockable': ['uiIcon', 'fullIcon', 'localizedName'], 'regionDrawable': ['region'],
    }
    for kind, fields in required.items():
        for field in fields:
            if field not in result[kind]:
                raise ValueError(f'Missing compiled field {kind}.{field}')
    # These routines only read actor transforms / viewport dimensions and mutate
    # the newly allocated Vec2. A changed asynchronous implementation must be
    # reviewed instead of calling a Java coroutine from the JS inventory bridge.
    for fn in ['as_Element_localToStageCoordinates', 'as_Element_localToAscendantCoordinates',
               'as_Element_localToParentCoordinates', 'amg_Vec2__init_0',
               'as_Scene_getWidth', 'as_Scene_getHeight']:
        match = re.search(r'\b' + re.escape(fn) + r'\s*=.*?\{(.*?)\n\}', source, re.S)
        if not match or '$rt_suspending' in match.group(1):
            raise ValueError('Expected pure compiled transform/getter: ' + fn)
    return result


def install(runtime):
    original = runtime.with_name(runtime.name + '.ui-control-probe-original')
    raw = runtime.read_bytes()
    source = raw.decode()
    if MARKER in source:
        raise ValueError('UI probe already installed; restore before reinjection')
    if original.exists():
        raise ValueError('Pristine UI probe backup already exists: ' + str(original))
    if source.count(ANCHOR) != 1:
        raise ValueError('Compiled closure export anchor changed')
    helper = (ROOT / 'scripts/browser-ui-control-probe.js').read_text()
    icons = sorted(set(re.findall(r'\b(mg_Icon_\w+)\s*=\s*null\b', source)))
    icon_expression = '[' + ','.join('[' + name + ',' + json.dumps(name[8:]) + ']' for name in icons) + ']'
    helper = helper.replace('__UI_SCHEMA__', json.dumps(schema(source), separators=(',', ':')))
    helper = helper.replace('__UI_ICONS__', icon_expression)
    original.write_bytes(raw)
    runtime.write_text(source.replace(ANCHOR, MARKER + '\n' + helper + '\n' + ANCHOR, 1))
    original.with_name(original.name + '.installed-sha256').write_text(hashlib.sha256(runtime.read_bytes()).hexdigest())
    print('Installed read-only Arc UI control inventory; restore in LIFO order before packaging')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime', type=Path, default=ROOT / 'web-runtime/build/web/mindustry.js')
    parser.add_argument('--restore', action='store_true')
    args = parser.parse_args()
    original = args.runtime.with_name(args.runtime.name + '.ui-control-probe-original')
    if args.restore:
        if not original.exists():
            raise SystemExit('Missing pristine UI probe backup: ' + str(original))
        current = args.runtime.read_text()
        if MARKER not in current:
            raise SystemExit('UI probe marker is missing; refusing out-of-order restore')
        installed_hash = original.with_name(original.name + '.installed-sha256')
        if not installed_hash.exists() or hashlib.sha256(args.runtime.read_bytes()).hexdigest() != installed_hash.read_text():
            raise SystemExit('UI probe bundle changed; restore later fixtures first (LIFO)')
        args.runtime.write_bytes(original.read_bytes())
        original.unlink()
        installed_hash.unlink()
        print('Restored exact bundle bytes from UI probe backup')
    else:
        install(args.runtime)


if __name__ == '__main__':
    main()
