#!/usr/bin/env node
'use strict';
// MSAV/Schematic bytes cross JavaScript's signed Int8Array / Python's unsigned
// bytes boundary. Preserve all eight bits rather than treating -1 as invalid.
const assert = require('node:assert/strict');
const signed = Int8Array.from([-128, -1, 0, 127]);
const unsigned = Array.from(Uint8Array.from(signed));
assert.deepEqual(unsigned, [128, 255, 0, 127]);
const original = Uint8Array.from([128, 255, 0, 127]);
assert.deepEqual(Buffer.from(unsigned), Buffer.from(original));
assert.equal(JSON.stringify(unsigned), JSON.stringify(Array.from(original)));
console.log('Unsigned MSAV/schematic browser QA conversion PASS');
