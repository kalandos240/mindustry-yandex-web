const vm=require('node:vm'),fs=require('node:fs'),assert=require('node:assert/strict');
const src=fs.readFileSync(require('node:path').join(__dirname,'../web-runtime/src/web/loading-screen.js'),'utf8');
function boot(lang){
 const nodes={};const timers=[];const listeners={};let notify;
 const node=id=>nodes[id]||=( {hidden:id.endsWith('retry'),style:{},attrs:{},setAttribute(k,v){this.attrs[k]=v},getAttribute(k){return this.attrs[k]},addEventListener(k,v){this[k]=v}} );
 const root=node('root');root.attrs['data-mindustry-web']='booting';
 const context={document:{documentElement:root,getElementById:node},navigator:{language:lang},console,setTimeout(fn){timers.push(fn);return timers.length},clearTimeout(){},MutationObserver:class{constructor(fn){notify=fn}observe(){}disconnect(){}},window:{addEventListener(k,v){listeners[k]=v}},location:{reload(){}}};context.globalThis=context;
 vm.runInNewContext(src,context);
 return {nodes,root,api:context.__mindustryLoading,notify:()=>notify(),timers,listeners};
}
for(const lang of ['ru-RU','en-US']){
 const t=boot(lang);t.api.update(60,'assets',5,10);assert.equal(t.nodes['mindustry-loading-progress'].attrs['aria-valuenow'],'60');t.api.update(20,'runtime');assert.equal(t.nodes['mindustry-loading-progress'].attrs['aria-valuenow'],'60');
 t.root.attrs['data-mindustry-web']='initialized';t.notify();assert.equal(t.nodes['mindustry-loader'].hidden,false);
 t.root.attrs['data-mindustry-web']='ready';t.notify();assert.equal(t.nodes['mindustry-loader'].hidden,true);assert.equal(t.nodes['mindustry-loading-progress'].attrs['aria-valuenow'],'100');
 const e=boot(lang);e.root.attrs['data-mindustry-web']='error';e.notify();assert.equal(e.nodes['mindustry-loading-retry'].hidden,false);assert.equal(e.nodes['mindustry-loader'].attrs['aria-busy'],'false');assert.match(e.nodes['mindustry-loading-message'].textContent,lang.startsWith('ru')?/Не удалось/:/Unable/);
 const slow=boot(lang);slow.timers[0]();assert.equal(slow.nodes['mindustry-loading-retry'].hidden,false);slow.root.attrs['data-mindustry-web']='ready';slow.notify();assert.equal(slow.nodes['mindustry-loader'].hidden,true);
 console.log('PASS',lang,'monotonic progress, initialized stays visible, ready, error, slow recovery');
}
