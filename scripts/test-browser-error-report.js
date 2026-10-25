const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync(__dirname+'/browser-error-report.inline.js','utf8');
const attrs=new Map([['data-mindustry-web','ready'],['data-mindustry-campaign-phase','sector-save']]);
const listeners={},body={children:[],appendChild(x){this.children.push(x);}},copied=[];
const element=()=>({style:{},children:[],appendChild(x){this.children.push(x);},setAttribute(){},focus(){},select(){}});
const c={document:{documentElement:{getAttribute:k=>attrs.get(k),setAttribute:(k,v)=>attrs.set(k,v)},body,createElement:element,execCommand:()=>true},
 navigator:{language:'ru',clipboard:{writeText:async s=>copied.push(s)}},location:{search:'?lang=ru',reload:()=>{c.reloaded=true;}},
 innerWidth:2400,innerHeight:610,URLSearchParams,console:{error(){}},addEventListener:(name,fn)=>listeners[name]=fn};
c.globalThis=c;vm.createContext(c);vm.runInContext(source,c);
function RendererFault(){this.$message={$nativeString:'framebuffer detail'};this.$jsException={stack:'inner-renderer-stack'};this.$cause=null;}
function Wrapper(){this.$message={$nativeString:'c:97'};this.$jsException={stack:'wrapper-stack'};this.$cause=new RendererFault();}
const error=new Error('c:97');error[Symbol('javaException')]=new Wrapper();
(async()=>{
 listeners.error({error:new Error('unrelated host error')});assert.equal(body.children.length,0);
 listeners.error({error});assert.equal(body.children.length,1);
 const report=c.__mindustryErrorReport;
 assert.equal(report.phase,'sector-save');assert.equal(report.causes.length,2);
 assert.equal(report.causes[1].message,'framebuffer detail');assert.equal(report.causes[1].stack,'inner-renderer-stack');
 attrs.set('data-mindustry-web','error');listeners.error({error:new Error('c:97 relayed by SDK')});
 assert.equal(c.__mindustryErrorReport.causes[1].message,'framebuffer detail');
 const panel=body.children[0],copy=panel.children[1],reload=panel.children[2];
 await copy.onclick();assert.equal(JSON.parse(copied[0]).causes[1].message,'framebuffer detail');assert.equal(copy.textContent,'Скопировано');
 c.navigator.clipboard.writeText=async()=>{throw Error('iframe clipboard denied');};await copy.onclick();
 assert.equal(panel.children.at(-1).value,JSON.stringify(report,null,2));
 error[Object.getOwnPropertySymbols(error)[0]].$cause=error[Object.getOwnPropertySymbols(error)[0]];
 listeners.unhandledrejection({reason:error});assert.equal(c.__mindustryErrorReport.causes.length,1);assert.equal(body.children.length,1);
 reload.onclick();assert(c.reloaded);
 console.log('Browser error report: nested Java causes, inner stack, cycle bound, host filtering, copy/fallback and reload PASS');
})().catch(e=>{console.error(e);process.exitCode=1;});
