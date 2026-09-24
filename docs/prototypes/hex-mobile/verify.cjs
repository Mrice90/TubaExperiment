const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs');
const {pathToFileURL} = require('node:url');
const {chromium} = require('playwright');

(async()=>{
 const out=path.resolve(__dirname,'../../../build/hex-prototype');fs.mkdirSync(out,{recursive:true});
 const browser=await chromium.launch({headless:true,...(process.env.IC_BROWSER_EXECUTABLE?{executablePath:process.env.IC_BROWSER_EXECUTABLE}:{})});
 const page=await browser.newPage({viewport:{width:1280,height:960}});
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 page.on('dialog',d=>d.accept());
 await page.goto(pathToFileURL(path.join(__dirname,'index.html')).href);
 await page.waitForFunction(()=>window.ICPrototype);
 assert.equal(await page.locator('.hex').count(),24);
 const geometry=await page.evaluate(()=>{
  const cells=Array.from({length:24},(_,i)=>({c:i%4,r:Math.floor(i/4)}));const d=ICPrototype.distance;
  return {symmetric:cells.every(a=>cells.every(b=>d(a,b)===d(b,a)&&d(a,b)===d({c:3-a.c,r:5-a.r},{c:3-b.c,r:5-b.r}))),neighbors:cells.filter(b=>d({c:1,r:2},b)===1).length};
 });assert.equal(geometry.symmetric,true);assert.equal(geometry.neighbors,6);
 for(const theme of ['storm','obsidian'])for(const scene of ['opening','crowded']){
  await page.selectOption('#theme',theme);await page.selectOption('#scene',scene);await page.locator('#product').screenshot({path:path.join(out,theme+'-'+scene+'-desktop.png')});
  assert.ok((await page.locator('#product').boundingBox()).height<=650,'Desktop product fits 650px usable height');
 }
 await page.selectOption('#device','phone');
 for(const theme of ['storm','obsidian']){
  await page.selectOption('#theme',theme);await page.selectOption('#scene','crowded');
  await page.locator('#product').screenshot({path:path.join(out,theme+'-crowded-phone.png')});
 }
 await page.locator('[data-hex="2,0"]').click();await page.locator('#sheet').waitFor({state:'visible'});assert.match(await page.locator('#sheetTitle').textContent(),/Red Citadel/);await page.click('#closeSheet');
 await page.click('#deckTab');await page.click('#next');
 assert.equal(await page.locator('.choice').count(),6,'Five other factions plus no ally');
 await page.getByRole('button',{name:'No ally',exact:false}).click();await page.click('#next');
 assert.equal(await page.locator('.choice').count(),3,'Only three primary Capitals');await page.click('#next');
 assert.equal(await page.locator('#next').isDisabled(),true);
 await page.click('#sampleDeck');assert.equal(await page.locator('#next').isEnabled(),true);
 await page.click('#next');await page.click('#closeSheet');
 let draft=await page.evaluate(()=>JSON.parse(localStorage.getItem('infinite-conquest-hex-deck-draft')));
 assert.equal(draft.allyFaction,null);assert.equal(draft.cards.reduce((a,b)=>a+b.copies,0),40);
 await page.click('#back');await page.click('#back');await page.getByRole('button',{name:'Poseidon',exact:false}).click();await page.click('#next');await page.click('#next');await page.click('#sampleDeck');
 assert.equal(await page.evaluate(()=>ICPrototype.state.ally),'POSEIDON');
 assert.equal(await page.locator('.catalog-card[data-faction="ARES"]').count(),0);
 await page.click('#next');await page.click('#closeSheet');draft=await page.evaluate(()=>JSON.parse(localStorage.getItem('infinite-conquest-hex-deck-draft')));assert.equal(draft.allyFaction,'POSEIDON');
 const invalid=await page.evaluate(()=>{const s=ICPrototype.state;const bad=ICPrototype.cards.find(c=>c.faction==='ARES');s.counts[bad.id]=1;const rejected=ICPrototype.deckErrors().some(e=>e.includes('Invalid card'));delete s.counts[bad.id];return rejected;});assert.equal(invalid,true,'Reject a third faction even if injected');
 const identityChecks=await page.evaluate(()=>{const s=ICPrototype.state,ally=s.ally,capital=s.capital;s.ally=s.primary;const sameAlly=ICPrototype.deckErrors().some(e=>e.includes('different faction'));s.ally=ally;s.capital='ares_capital_red_citadel';const wrongCapital=ICPrototype.deckErrors().some(e=>e.includes('Capital must'));s.capital=capital;return {sameAlly,wrongCapital,unique:new Set(ICPrototype.cards.map(c=>c.id)).size===ICPrototype.cards.length};});assert.deepEqual(identityChecks,{sameAlly:true,wrongCapital:true,unique:true});
 const codec=await page.evaluate(()=>{
  const {state,deckErrors}=ICPrototype,code=ICDeckCode.encode(state);
  const imported=ICDeckCode.decode(code,deckErrors);
  const reversed={...state,counts:Object.fromEntries(Object.entries(state.counts).reverse())};
  const rejected=[];
  for(const bad of [code.slice(0,-1)+'x',code.replace('ICD1','ICD9'),ICDeckCode.encode({...state,ally:state.primary}),ICDeckCode.encode({...state,capital:'ares_capital_red_citadel'}),ICDeckCode.encode({...state,counts:{...state.counts,[ICPrototype.cards.find(c=>c.faction==='ARES').id]:1}}),ICDeckCode.encode({...state,counts:{[Object.keys(state.counts)[0]]:5}})]){
   try{ICDeckCode.decode(bad,deckErrors);rejected.push(false);}catch{rejected.push(true);}
  }
  return {stable:ICDeckCode.encode(reversed)===code,roundtrip:ICDeckCode.encode(imported)===code,wrapped:ICDeckCode.encode(ICDeckCode.decode(code.replaceAll('.','.\n'),deckErrors))===code,rejected,passives:ICData.capitals.every(c=>c.passiveName&&c.passiveText),stats:ICPrototype.cards.every(c=>Number.isInteger(c.movement)&&Number.isInteger(c.range))};
 });assert.deepEqual(codec,{stable:true,roundtrip:true,wrapped:true,rejected:Array(6).fill(true),passives:true,stats:true});
 await page.click('#shareDeck');const shared=await page.inputValue('#deckCode');await page.click('#closeSheet');
 await page.click('#importDeck');await page.fill('#importCode',shared);await page.click('#previewCode');assert.equal(await page.locator('#importPreview li').count(),10);await page.click('#closeSheet');
 await page.click('#clearDeck');await page.click('#importDeck');await page.fill('#importCode','bad code');await page.click('#previewCode');assert.match(await page.textContent('#importError'),/Unsupported/);assert.equal(await page.evaluate(()=>Object.keys(ICPrototype.state.counts).length),0);
 await page.fill('#importCode',shared);await page.click('#previewCode');await page.click('#applyImport');assert.equal(await page.evaluate(()=>ICDeckCode.encode(ICPrototype.state)),shared);
 await page.locator('.card-details').first().click();assert.match(await page.textContent('#sheetBody'),/Attack.*Defense.*Move.*Range/);await page.screenshot({path:path.join(out,'card-inspection.png')});await page.click('#closeSheet');
 await page.evaluate(()=>ICPrototype.inspectCard(ICPrototype.cards.find(c=>c.id==='zeus_ability_oracle_spire')));assert.match(await page.textContent('#sheetBody'),/2 GP.*draw 1/);await page.click('#closeSheet');
 await page.click('#back');assert.match(await page.textContent('#deckStage'),/first Blink Character gains/);await page.locator('#product').screenshot({path:path.join(out,'capital-passives.png')});await page.click('#next');
 await page.locator('#product').screenshot({path:path.join(out,'allied-deck-phone.png')});
 for(const width of [320,360,390]){
  await page.setViewportSize({width,height:844});await page.click('#battleTab');
  const layout=await page.evaluate(()=>({overflow:document.documentElement.scrollWidth>innerWidth,hexes:[...document.querySelectorAll('.hex')].map(el=>{const r=el.getBoundingClientRect();return {width:r.width,height:r.height};})}));
  assert.equal(layout.overflow,false,'No horizontal page overflow at '+width);assert.ok(layout.hexes.every(r=>r.width>=44&&r.height>=44),'Touch bounds at '+width);
 }
 const broken=await page.evaluate(async()=>{const images=[...document.querySelectorAll('img')].filter(i=>i.src&&i.loading!=='lazy');const results=await Promise.all(images.map(async i=>{try{await i.decode();return null;}catch{return i.src;}}));return results.filter(Boolean);});assert.deepEqual(broken,[]);
 assert.deepEqual(errors,[]);
 console.log(JSON.stringify({result:'pass',cells:24,desktopConceptCaptures:4,phoneBoardCaptures:2,deckCaptures:1,phoneWidths:[320,360,390],geometry:'six neighbors and rotational symmetry',deck:'no ally, one ally, primary Capital, third-faction rejection, browser save',output:out},null,2));
 await browser.close();
})().catch(e=>{console.error(e);process.exit(1);});
