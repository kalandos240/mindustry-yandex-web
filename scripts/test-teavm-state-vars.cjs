const assert=require('node:assert/strict'),path=require('node:path'),vm=require('node:vm');
const acorn=require(path.resolve(process.argv[2])),{compact}=require('./compact-teavm-state-vars.cjs');
const input=String.raw`// $ptr in comment stays untouched
function f(n){let $ptr=0;while(true){switch($ptr){case 0:$ptr=1;break;case 1:return n+" $ptr "+/\$ptr/.source;}}}
function g(){let $ptr=3;return ()=>$ptr+1;}
const literal={name:"$ptr",other:/["']/};
const result=[f(7),g()(),literal.name];`;
const result=compact(input,acorn),before={},after={};
vm.runInNewContext(input+'globalThis.out=result;',before);vm.runInNewContext(result.code+'globalThis.out=result;',after);
assert.deepEqual(JSON.parse(JSON.stringify(before.out)),JSON.parse(JSON.stringify(after.out)));
assert(result.code.includes('// $ptr in comment stays untouched'));assert(result.code.includes('" $ptr "'));
assert(result.saved>0);assert.equal(result.code.split('\n').length,input.split('\n').length);
assert.equal(compact(result.code,acorn).code,result.code);
assert.notEqual(compact('let $P=9;function f(){let $ptr=1;return $ptr+$P;}',acorn).name,'$P');
for(const s of ['x.$ptr=1;','const x={$ptr:1};','const x={$ptr};','let $ptr=1;eval("$ptr");'])assert.throws(()=>compact(s,acorn));
console.log('TeaVM state compaction: real behavior, literals/regexp/comments, nested scopes, token equivalence, collisions, reflection/property guards and line preservation PASS');
