const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
(async()=>{
 const s=fs.readFileSync(__dirname+'/../web-runtime/src/main/java/mindustry/ui/dialogs/BrowserPlanetDialog.java','utf8');
 const start=s.indexOf('@JSBody(params={"path","sector"}');
 const script=s.slice(s.indexOf('script="""',start)+10,s.indexOf('""")',start));
 const checkpoint=Uint8Array.from([77,83,65,86,0,255,128,1]);
 const settings=Uint8Array.from([77,87,83,49,124]);
 const path='saves/serpulo-15.msav';
 const files={[path]:checkpoint,[path+'.lost-backup']:checkpoint,'settings/browser.mws1':settings,'saves/unrelated.msav':checkpoint};
 let blob,download,clicked=false,mark;
 const context={path,sector:'groundZero',Uint8Array,Math,String,JSON,Blob,
  btoa:b=>Buffer.from(b,'binary').toString('base64'),
  setTimeout:(f,ms)=>assert.equal(ms,60000),
  URL:{createObjectURL:b=>{blob=b;return 'blob:local';}},
  document:{createElement:()=>({set download(v){download=v;},click:()=>clicked=true,remove(){}}),body:{appendChild(){}},documentElement:{setAttribute:(k,v)=>mark=[k,v]}},
  __mindustryStorage:{get:p=>files[p]||null,paths:()=>Object.keys(files)}};
 vm.runInNewContext("(function(){"+script+"})()",context);
 assert(clicked);assert.equal(download,'Mindustry-sector-groundZero.json');
 const exported=JSON.parse(await blob.text());
 assert.equal(exported.format,'mindustry-web-sector-v1');assert.equal(exported.savePath,path);
 assert.deepEqual(Object.keys(exported.files).sort(),[path,path+'.lost-backup','settings/browser.mws1'].sort());
 assert.deepEqual(Buffer.from(exported.files[path],'base64'),Buffer.from(checkpoint));
 assert.deepEqual(Buffer.from(exported.files['settings/browser.mws1'],'base64'),Buffer.from(settings));
 assert.equal(mark[1],'ready');
 context.path='missing';clicked=false;vm.runInNewContext("(function(){"+script+"})()",context);assert(!clicked);
 console.log('Sector export: exact bytes, backup, settings, selected-save isolation and missing-save handling PASS');
})().catch(e=>{console.error(e);process.exitCode=1;});
