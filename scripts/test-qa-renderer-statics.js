#!/usr/bin/env node
// Regression for stock Mindustry Renderer opacity fields: they are static,
// unlike other Renderer fields. Test-only QA instrumentation must not mistake
// a working native setting for a missing field on Vars.renderer.
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const globals = [
    'm_Vars_state', 'm_Vars_control', 'm_Vars_ui',
    'a_Core_audio', 'a_Core_camera', 'a_Core_settings',
    'a_Core_graphics', 'a_Core_input', 'a_Core_scene', 'a_Core_atlas'
];
const scope = Object.fromEntries(globals.map(name => [name, null]));
scope.m_Vars_renderer = {
    $laserOpacity: 99, $unitLaserOpacity: 99, $bridgeOpacity: 99,
    $enableEffects: true
};
scope.mc_Renderer_laserOpacity = 0.52;
scope.mc_Renderer_unitLaserOpacity = 0.81;
scope.mc_Renderer_bridgeOpacity = 0.73;
const source = fs.readFileSync(path.join(__dirname, 'browser-qa-probe.js'), 'utf8');
vm.runInNewContext(source, scope, {timeout: 2000});

const read = scope.__mindustryQaRead;
assert.equal(typeof read, 'function');
assert.equal(read('renderer', 'laserOpacity'), 0.52);
assert.equal(read('renderer', 'unitLaserOpacity'), 0.81);
assert.equal(read('renderer', 'bridgeOpacity'), 0.73);
assert.equal(read('renderer', 'enableEffects'), true);
scope.mc_Renderer_laserOpacity = 0.24;
assert.equal(read('renderer', 'laserOpacity'), 0.24, 'read the live renderer static');
assert.throws(() => read('renderer', 'unknownField'), /missing or ambiguous/);
console.log('Renderer static opacity settings QA regression PASS');
