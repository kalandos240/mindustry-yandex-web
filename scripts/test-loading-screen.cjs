const {chromium}=require(process.env.CODEX_PRIMARY_RUNTIME_NODE_MODULES ? process.env.CODEX_PRIMARY_RUNTIME_NODE_MODULES+'/playwright' : 'playwright');
const fs=require('fs');
const path=require('path');
const assert=require('node:assert/strict');
const base=path.join(__dirname,'../web-runtime/src/web');
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
 for(const platform of ['yandex','playgama']) for(const viewport of [{width:1280,height:720},{width:390,height:844},{width:844,height:390}]){
  const page=await browser.newPage({viewport,locale:'ru-RU'});
  await page.route('http://game.test/**',async route=>{
   const name=new URL(route.request().url()).pathname.slice(1);
   let body=''; let contentType='text/javascript';
   if(!name){body=fs.readFileSync(base+'/index.html','utf8');contentType='text/html';if(platform==='playgama')body=body.replace('<script src="yandex-platform.js"></script>','<script src="yandex-platform.js"></script><script>globalThis.__mindustryPlaygamaAds={active:true,initialize:async()=>{}};</script>');}
   else if(name==='loading-screen.js')body=fs.readFileSync(base+'/'+name,'utf8');
   else if(name==='browser-storage.js')body='globalThis.__mindustryStorage={init:async()=>{await new Promise(r=>setTimeout(r,100))}}';
   else if(name==='yandex-platform.js')body='globalThis.__mindustryYandex={available:true,init:async()=>{},restoreCloudCheckpoint:async()=>{}}';
   else if(name==='assets-manifest.js')body='globalThis.__mindustryAssetManifest=["test.bin"]';
   else if(name==='mindustry.js')body='globalThis.main=()=>{document.documentElement.setAttribute("data-test-main","called")}';
   else if(name.startsWith('assets/')){body='resource';await new Promise(r=>setTimeout(r,200));}
   await route.fulfill({status:200,contentType,body});
  });
  await page.goto('http://game.test/');
  await page.waitForSelector('[data-test-main="called"]',{state:'attached'});
  assert.equal(await page.locator('#mindustry-loader').isVisible(),true);
  assert.match(await page.locator('#mindustry-loading-message').textContent(),/Запуск/);
  const box=await page.locator('.loading-card').boundingBox();assert.ok(box.x>=0&&box.y>=0&&box.x+box.width<=viewport.width&&box.y+box.height<=viewport.height);
  await page.evaluate(()=>document.documentElement.setAttribute('data-mindustry-web','initialized'));
  assert.equal(await page.locator('#mindustry-loader').isVisible(),true);
  await page.evaluate(()=>document.documentElement.setAttribute('data-mindustry-web','ready'));
  await page.waitForSelector('#mindustry-loader',{state:'hidden'});
  assert.equal(await page.locator('#mindustry-loading-progress').getAttribute('aria-valuenow'),'100');
  await page.reload();await page.waitForSelector('[data-test-main="called"]',{state:'attached'});
  await page.evaluate(()=>document.documentElement.setAttribute('data-mindustry-web','error'));
  await page.waitForSelector('#mindustry-loading-retry',{state:'visible'});
  assert.match(await page.locator('#mindustry-loading-message').textContent(),/Не удалось/);
  console.log('PASS',platform,viewport.width+'x'+viewport.height,'boot visibility, localized progress, ready, error');
  await page.close();
 }
 await browser.close();
})().catch(e=>{console.error(e);process.exitCode=1});
