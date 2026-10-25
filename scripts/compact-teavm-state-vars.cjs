// Alpha-rename one private TeaVM coroutine local. Preserve all source layout,
// public/function/field names, string/regexp literals and line numbers.
const fs=require('node:fs'),path=require('node:path');
function compact(source,acorn){
    const target='$ptr',names=new Set(),locations=[];
    const tokens=acorn.tokenizer(source,{ecmaVersion:'latest'});
    let previous=null,current=tokens.getToken(),reflective=false,literalTarget=false;
    while(current.type.label!=='eof'){
        const next=tokens.getToken();
        if(current.type.label==='name'){
            names.add(current.value);
            if(current.value==='eval')reflective=true;
            if(current.value===target){
                if(['.','?.'].includes(previous?.type.label)||[':', '}'].includes(next.type.label)){
                    throw Error('Unexpected public/property/label use of private coroutine state');
                }
                locations.push([current.start,current.end]);
            }
        }else if(current.type.label==='string'){
            if(current.value.includes(target))literalTarget=true;
            if(current.value==='eval')reflective=true;
        }
        previous=current;current=next;
    }
    if(reflective&&literalTarget)throw Error('Reflective coroutine name reference needs review');
    if(!locations.length)return{code:source,replacements:0,saved:0,name:null};
    const name=['$P','$Q','$R','$U','$Z'].find(candidate=>!names.has(candidate));
    if(!name)throw Error('No collision-free private coroutine identifier');
    const pieces=[];let cursor=0;
    for(const [start,end] of locations){pieces.push(source.slice(cursor,start),name);cursor=end;}
    pieces.push(source.slice(cursor));const code=pieces.join('');
    // Check the entire lexical stream independently. Only the selected local
    // identifier may differ, including inside nested scopes/template expressions.
    const left=acorn.tokenizer(source,{ecmaVersion:'latest'}),right=acorn.tokenizer(code,{ecmaVersion:'latest'});
    while(true){
        const a=left.getToken(),b=right.getToken();
        const expected=a.type.label==='name'&&a.value===target?name:a.value;
        if(a.type.label!==b.type.label||JSON.stringify(expected)!==JSON.stringify(b.value))throw Error('Compaction changed another JavaScript token');
        if(a.type.label==='eof')break;
    }
    return{code,replacements:locations.length,saved:Buffer.byteLength(source)-Buffer.byteLength(code),name};
}
module.exports={compact};
if(require.main===module){
    const file=path.resolve(process.argv[2]),parser=path.resolve(process.argv[3]);
    const result=compact(fs.readFileSync(file,'utf8'),require(parser));
    if(result.replacements<10000)throw Error('Expected pinned TeaVM coroutine state locals');
    fs.writeFileSync(file,result.code);
    console.log('TeaVM private state alpha-rename:',result.replacements,'tokens;',result.saved,'bytes saved; lexical equivalence PASS');
}
