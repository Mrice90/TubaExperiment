'use strict';
(() => {
 const $ = id => document.getElementById(id);
 const artBase = '../../../game-gui/src/main/resources/art/';
 const factions = ['ZEUS','POSEIDON','HADES','ARES','ATHENA','HEPHAESTUS'];
 const identities = {ZEUS:'Storm and precision',POSEIDON:'Tides and resilience',HADES:'Death and concealment',ARES:'War and momentum',ATHENA:'Sight and strategy',HEPHAESTUS:'Fire and craft'};
 const cards = window.ICData.cards;
 const capitals = window.ICData.capitals;
 const state = {theme:'storm',device:'desktop',scene:'opening',view:'battle',step:0,primary:'ZEUS',ally:'POSEIDON',capital:'zeus_capital_olympus_citadel',counts:{},selected:null,filter:''};
 const title = s => s.charAt(0) + s.slice(1).toLowerCase();
 const esc = s => String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 const capOf = faction => capitals.find(c=>c.faction===faction);
 const art = card => artBase+(card.art || 'capitals/'+card.id+'.jpg');
 const deckSize = () => Object.values(state.counts).reduce((a,b)=>a+b,0);
 const eligible = card => card.faction===state.primary || card.faction===state.ally || card.faction==='NEUTRAL';
 const distance = (a,b) => {
  const qa=a.c-(a.r-(a.r&1))/2, qb=b.c-(b.r-(b.r&1))/2;
  return Math.max(Math.abs(qa-qb),Math.abs(a.r-b.r),Math.abs(qa+a.r-qb-b.r));
 };
 function deckErrors(candidate=state) {
  const errors=[];
  if(!factions.includes(candidate.primary)) errors.push('Choose a primary faction.');
  if(candidate.ally && (!factions.includes(candidate.ally)||candidate.ally===candidate.primary)) errors.push('Choose a different faction as your only ally, or choose no ally.');
  if(!capitals.some(c=>c.id===candidate.capital&&c.faction===candidate.primary)) errors.push('Your Capital must belong to your primary faction.');
  if(Object.values(candidate.counts).reduce((a,b)=>a+b,0)<40) errors.push('Add '+(40-Object.values(candidate.counts).reduce((a,b)=>a+b,0))+' more cards to reach 40.');
  if(Object.keys(candidate.counts).filter(id=>candidate.counts[id]>0).length<10) errors.push('Use at least 10 different cards.');
  for(const [id,count] of Object.entries(candidate.counts)) {
   const card=cards.find(c=>c.id===id);
   if(!card||!(card.faction===candidate.primary||card.faction===candidate.ally||card.faction==='NEUTRAL')||count<1||count>4||!Number.isInteger(count)) errors.push('Invalid card selection: '+id);
  }
  return errors;
 }
 function sheet(name,html) {$('sheetTitle').textContent=name;$('sheetBody').innerHTML=html;$('sheet').showModal();}
 $('closeSheet').onclick=()=>$('sheet').close();
 $('sheet').addEventListener('click',e=>{if(e.target===$('sheet'))$('sheet').close();});
 $('theme').onchange=e=>{state.theme=e.target.value;renderShell();};
 $('device').onchange=e=>{state.device=e.target.value;renderShell();};
 $('scene').onchange=e=>{state.scene=e.target.value;state.selected=null;renderBattle();};
 $('battleTab').onclick=()=>{state.view='battle';renderShell();};
 $('deckTab').onclick=()=>{state.view='deck';renderShell();renderDeck();};
 function renderShell(){
  $('product').className='product '+state.theme;$('product').dataset.device=state.device;
  $('battleView').hidden=state.view!=='battle';$('deckView').hidden=state.view!=='deck';
  $('battleTab').setAttribute('aria-pressed',state.view==='battle');$('deckTab').setAttribute('aria-pressed',state.view==='deck');
  $('sceneLabel').hidden=state.view!=='battle';
  $('landscapeTitle').innerHTML=state.theme==='storm'?'Where storms<br>meet empires.':'The war table.<br>Every piece matters.';
  $('landscapeCopy').innerHTML=state.theme==='storm'?'One field. Two strongholds.<br>Every hex is a decision.':'Quiet terrain. Clear intent.<br>A tactile field of carved stone.';
  renderBattle();
 }
 function sampleUnits(){
  const units=new Map();
  units.set('2,0',{card:capOf('ARES'),owner:1,stack:1,capital:true});
  units.set('1,5',{card:capitals.find(c=>c.id===state.capital)||capOf(state.primary),owner:0,stack:1,capital:true});
  if(state.scene==='crowded') {
   const friendly=cards.filter(c=>c.type==='CHARACTER'&&(c.faction===state.primary||c.faction===state.ally));
   const enemy=cards.filter(c=>c.type==='CHARACTER'&&c.faction==='ARES');
   for(let r=0;r<6;r++)for(let c=0;c<4;c++){
    const key=c+','+r;if(units.has(key)||(r+c)%4===0)continue;
    const owner=r<3?1:0,pool=owner?enemy:friendly.filter(card=>card.faction===((r+c)%2&&state.ally?state.ally:state.primary));
    units.set(key,{card:pool[(r*4+c)%pool.length],owner,stack:(r+c)%3===0?2:1,capital:false});
   }
  }
  return units;
 }
 function renderBattle(){
  const capital=capitals.find(c=>c.id===state.capital)||capOf(state.primary);
  $('homePortrait').src=art(capital);$('enemyPortrait').src=art(capOf('ARES'));
  $('homeName').textContent='YOU · '+state.primary;$('homeAlly').textContent=state.ally?'Ally · '+title(state.ally):'No ally · '+capital.name;
  const units=sampleUnits(),board=$('hexboard');board.replaceChildren();
  const landscape=state.device==='desktop'&&window.innerWidth>700;board.classList.toggle('landscape',landscape);
  for(let r=0;r<6;r++)for(let c=0;c<4;c++){
   const key=c+','+r,unit=units.get(key),button=document.createElement('button');
   const isNeighbor=state.selected&&distance(state.selected,{c,r})===1;
   button.className='hex'+(r<3?' enemy':'')+(unit?' occupied':'')+(state.selected?.c===c&&state.selected?.r===r?' selected':'')+(isNeighbor?' neighbor':'');
   button.style.left=(landscape?(5-r)*15.77:1.25+(c+(r%2)/2)*21.65)+'%';
   button.style.top=(landscape?1.25+(c+(r%2)/2)*21.65:r*15.77)+'%';
   button.dataset.hex=key;button.setAttribute('aria-pressed',state.selected?.c===c&&state.selected?.r===r?'true':'false');
   button.setAttribute('aria-label',unit?`${unit.card.name}, player ${unit.owner+1}, ${unit.capital?'Capital':'unit'}, stack ${unit.stack}, hex ${key}`:`Empty hex ${key}, ${r<3?'opponent':'your'} territory`);
   if(unit)button.innerHTML=`<img src="${art(unit.card)}" alt=""><span class="shade"></span><span class="owner">${unit.owner?'◇ P2':'○ P1'}</span>${unit.stack>1?'<span class="stack">×'+unit.stack+'</span>':''}${unit.capital?'<span class="capmark">✦</span>':''}<span class="stats">${unit.capital?'20 HP':(unit.card.attack||0)+' / '+(unit.card.defense||0)}</span>`;
   else button.innerHTML='<span class="empty">'+(isNeighbor?'•':(r<3?'◇':'○'))+'</span>';
   button.onclick=()=>selectHex({c,r},unit);
   board.append(button);
  }
  renderHand();
 }
 function selectHex(position,unit){
  state.selected=position;renderBattle();
  const name=unit?unit.card.name:'Empty hex';
  const detail=unit?`Player ${unit.owner+1} · ${unit.card.faction} · ${unit.capital?'Capital · 20 HP':'Attack '+unit.card.attack+' · Defense '+unit.card.defense} · Stack ${unit.stack}`:'Six-direction adjacency preview. Glowing neighbors show geometry, not legal moves.';
  $('inspectName').textContent=name;$('inspectText').textContent=detail;
  $('inspectArt').hidden=!unit;if(unit)$('inspectArt').src=art(unit.card);
  $('inspectButton').disabled=!unit;
  const open=()=>sheet(name,(unit?`<img class="sheet-art" src="${art(unit.card)}" alt="${esc(name)}">`:'')+`<p>${esc(detail)}</p><p>Prototype selection only. This does not resolve movement or combat.</p>`+(unit?.stack>1?'<div class="inspector-row">Top: '+esc(name)+'</div><div class="inspector-row">Supporting unit · illustrative stack</div>':''));
  $('inspectButton').onclick=open;
  $('event').textContent='Selected '+name+' · neighboring hexes illuminated';
  if(state.device==='phone'||window.innerWidth<=700)open();
 }
 function renderHand(){
  const pool=cards.filter(c=>c.faction===state.primary&&c.type==='CHARACTER').slice(0,3);
  if(state.ally)pool.push(cards.find(c=>c.faction===state.ally&&c.type==='CHARACTER'));
  $('hand').innerHTML='';
  for(const card of pool){const b=document.createElement('button');b.className='handcard';b.innerHTML=`<img src="${art(card)}" alt=""><i>${card.cost}</i><span>${esc(card.name)}</span>`;b.onclick=()=>inspectCard(card);$('hand').append(b);}
 }
 $('history').onclick=()=>sheet('Recent actions','<div class="history-entry">↻ Turn start · You</div><div class="history-entry">+ Income · 3 GP from developments</div><div class="history-entry">○ Selection · '+esc($('inspectName').textContent)+'</div><p>Illustrative history. No match is running.</p>');
 $('endTurn').onclick=()=>sheet('Prototype only','<p>This concept tests layout and inspection. It does not run a match or end a real turn.</p>');
 function choice(label,description,image,selected,onClick){
  const b=document.createElement('button');b.className='choice'+(selected?' selected':'')+(!image?' none':'');b.setAttribute('aria-pressed',selected);
  b.innerHTML=(image?`<img src="${image}" alt="">`:'')+`<b>${esc(label)}</b><small>${esc(description)}</small>`;b.onclick=onClick;return b;
 }
 function discardIneligible(){for(const id of Object.keys(state.counts)){const card=cards.find(c=>c.id===id);if(!card||!eligible(card))delete state.counts[id];}}
 function renderDeck(){
  const stage=$('deckStage');stage.replaceChildren();
  document.querySelectorAll('.steps li').forEach((el,i)=>{el.className=i===state.step?'active':i<state.step?'done':'';});
  $('deckCount').textContent=deckSize()+' / 40 cards';$('back').disabled=state.step===0;$('next').textContent=state.step===3?'Save draft':'Continue';
  const descriptions=[['Choose your faction.','Your primary faction determines your Capital choices.'],['One ally. Or stand alone.','An ally adds its cards to your collection. You still have one Capital.'],['Choose your Capital.','Only Capitals from your primary faction appear here.'],['Build your deck.','Primary + optional ally + Neutral. At least 40 cards, 10 distinct cards, at most 4 copies each.']];
  stage.innerHTML='<h3>'+descriptions[state.step][0]+'</h3><p class="intro">'+descriptions[state.step][1]+'</p>';
  if(state.step<3){
   const choices=document.createElement('div');choices.className='choices';stage.append(choices);
   if(state.step===0)for(const f of factions)choices.append(choice(title(f),identities[f],art(capOf(f)),state.primary===f,()=>{if(state.primary!==f&&deckSize()>0&&!confirm('Changing faction removes cards that no longer belong to the primary faction, ally, or Neutral. Continue?'))return;state.primary=f;if(state.ally===f)state.ally=null;state.capital=capOf(f).id;discardIneligible();renderDeck();}));
   if(state.step===1){choices.append(choice('No ally','Use your primary faction and Neutral cards.',null,!state.ally,()=>changeAlly(null)));for(const f of factions.filter(f=>f!==state.primary))choices.append(choice(title(f),identities[f],art(capOf(f)),state.ally===f,()=>changeAlly(f)));}
   if(state.step===2){
    choices.classList.add('capital-choices');
    for(const cap of capitals.filter(c=>c.faction===state.primary)){
     const button=choice(cap.name,'',art(cap),state.capital===cap.id,()=>{state.capital=cap.id;renderDeck();});
     button.classList.add('capital-choice');
     button.innerHTML=`<img src="${art(cap)}" alt=""><span class="capital-copy"><b>${esc(cap.name)}</b><span class="capital-stats">${cap.hitPoints} HP · +1 GP/turn</span><span class="capital-passive-name">${esc(cap.passiveName)}</span><span class="capital-passive-text">${esc(cap.passiveText)}</span><span class="capital-selection">${state.capital===cap.id?'✓ Selected Capital':'Choose this Capital'}</span></span>`;
     choices.append(button);
    }
   }
  } else renderCatalog(stage);
  $('deckStatus').textContent=state.step===0?'Primary · '+title(state.primary):state.step===1?(state.ally?'Ally · '+title(state.ally):'No ally selected'):state.step===2?capitals.find(c=>c.id===state.capital).name:deckErrors().length?'Draft needs more cards.':'Deck composition valid · prototype draft';
  $('next').disabled=state.step===3&&deckErrors().length>0;
 }
 function changeAlly(f){if(f!==state.ally&&deckSize()>0&&!confirm('Changing your ally removes cards that become ineligible. Continue?'))return;state.ally=f;discardIneligible();renderDeck();}
 function renderCatalog(stage){
  const summary=document.createElement('div');summary.className='decksummary';summary.textContent=title(state.primary)+(state.ally?' + '+title(state.ally):' · no ally')+' · '+capitals.find(c=>c.id===state.capital).name;stage.append(summary);
  const errors=document.createElement('div');errors.className='errors';errors.textContent=deckErrors().join(' ');stage.append(errors);
  const toolbar=document.createElement('div');toolbar.className='decktools';toolbar.innerHTML='<input id="search" aria-label="Search eligible cards" placeholder="Search eligible cards" value="'+esc(state.filter)+'"><button id="sampleDeck">Fill sample deck</button><button id="clearDeck">Clear</button>';stage.append(toolbar);
  const list=document.createElement('div');list.className='catalog';stage.append(list);
  function fill(){list.replaceChildren();for(const card of cards.filter(eligible).filter(c=>(c.name+' '+c.faction+' '+c.type+' '+cardRules(c).join(' ')).toLowerCase().includes(state.filter.toLowerCase()))){
   const count=state.counts[card.id]||0,row=document.createElement('div');row.className='catalog-card';row.dataset.faction=card.faction;
   row.innerHTML=`<img loading="lazy" src="${art(card)}" alt=""><div class="cardinfo"><b>${esc(card.name)}</b><small>${esc(card.faction)} · ${esc(card.type)}<br>${card.cost} ${card.type==='LAND'||card.type==='STRUCTURE'?'development turn':'GP'}</small></div><div class="countcontrols"><button aria-label="Add ${esc(card.name)}" ${count>=4?'disabled':''}>+</button><span>${count}/4</span><button aria-label="Remove ${esc(card.name)}" ${!count?'disabled':''}>−</button></div>`;
   const info=row.querySelector('.cardinfo');info.insertAdjacentHTML('beforeend','<p class="card-summary">'+esc(cardStats(card))+'</p><p class="card-summary">'+esc(cardRules(card).join(' '))+'</p>');const details=document.createElement('button');details.className='card-details';details.textContent='View card';details.setAttribute('aria-label','View '+card.name);details.onclick=()=>inspectCard(card);info.append(details);const buttons=row.querySelectorAll('.countcontrols button');buttons[0].onclick=()=>{state.counts[card.id]=count+1;renderDeck();};buttons[1].onclick=()=>{if(count===1)delete state.counts[card.id];else state.counts[card.id]=count-1;renderDeck();};list.append(row);
  }}fill();
  $('search').oninput=e=>{state.filter=e.target.value;fill();};
  $('sampleDeck').onclick=()=>{state.counts={};const primary=cards.filter(c=>c.faction===state.primary).slice(0,state.ally?7:10);const ally=state.ally?cards.filter(c=>c.faction===state.ally).slice(0,3):[];for(const c of [...primary,...ally])state.counts[c.id]=4;renderDeck();};
  $('clearDeck').onclick=()=>{state.counts={};renderDeck();};
 }
 $('back').onclick=()=>{state.step=Math.max(0,state.step-1);renderDeck();};
 $('next').onclick=()=>{if(state.step<3){state.step++;renderDeck();return;}if(deckErrors().length)return;const draft={kind:'infinite-conquest-design-draft',schemaVersion:2,name:title(state.primary)+' alliance',primaryFaction:state.primary,allyFaction:state.ally,capitalId:state.capital,cards:Object.entries(state.counts).map(([id,copies])=>({id,copies}))};try{localStorage.setItem('infinite-conquest-hex-deck-draft',JSON.stringify(draft));sheet('Draft saved','<p>Your '+deckSize()+'-card draft is saved in this browser.</p><p>Use Share deck to transfer this build into the desktop Hex &amp; Allies game.</p>');}catch(e){sheet('Storage unavailable','<p>Your browser blocked local storage. The draft remains available in this open page.</p>');}};

 const words = text => String(text).toLowerCase().replaceAll('_',' ').replace(/\b\w/g,c=>c.toUpperCase());
 function cardStats(card) {
  if(card.type==='CHARACTER')return `Attack ${card.attack} · Defense ${card.defense} · Move ${card.movement} · Range ${card.range}`;
  if(card.type==='LAND'||card.type==='STRUCTURE'||card.type==='CAPITAL')return `${card.hitPoints} HP · +${card.gpGeneration} GP/turn`;
  return 'Spell · resolves when cast';
 }
 function cardRules(card) {
  const rules=[];
  if(card.passiveText)rules.push(card.passiveName+': '+card.passiveText);
  if(card.keywords?.length)rules.push('Keywords: '+card.keywords.map(words).join(', ')+'.');
  for(const effect of card.effects||[])rules.push(`${words(effect.type)} ${effect.amount} — ${words(effect.target)}.`);
  for(const a of card.abilities||[]) {
   const timing={ENTERS_PLAY:'When this enters play',DESTROYED:'When destroyed',PASSIVE:'Start of your turn',ACTIVATED:`Activate once per turn (${a.gpCost} GP)`}[a.trigger];
   const effect={DRAW_CARD:`draw ${a.amount} card(s)`,DRAW_CHARACTER:'draw a random Character from your deck',DRAW_STRUCTURE:'draw a random Structure from your deck',GAIN_GP:`gain ${a.amount} GP`,HEAL_SELF:`heal this card for ${a.amount}`,HEAL_CAPITAL:`heal your Capital for ${a.amount}`,BUFF_SELF_ATTACK:`gain +${a.amount} Attack this turn`,BUFF_SELF_DEFENSE:`gain +${a.amount} Defense this turn`,DAMAGE_ENEMY_CAPITAL:`deal ${a.amount} damage to the enemy Capital`}[a.effect];
   rules.push(timing+': '+effect+'.');
  }
  const development={DRAW_ON_DEPLOY:'Deploy: draw 1 card.',HEAL_CAPITAL_ON_DEPLOY:'Deploy: heal your Capital for 3.',SELF_REPAIR:'Start of your turn: heal 2 damage from this card.'}[card.developmentPassive];
  if(development)rules.push(development);
  return rules;
 }
 function inspectCard(card) {
  const cost=card.type==='LAND'||card.type==='STRUCTURE'?'Free · available from turn '+Math.max(1,card.cost):card.cost+' GP';
  sheet(card.name,`<img class="sheet-art" src="${art(card)}" alt=""><p class="card-meta">${esc(words(card.faction))} · ${esc(words(card.type))} · ${cost}</p><p class="stat-panel">${esc(cardStats(card))}</p><h4>Abilities & effects</h4>${cardRules(card).map(r=>'<p class="rule-panel">'+esc(r)+'</p>').join('')||'<p>No additional abilities.</p>'}${card.rulesText?'<p>'+esc(card.rulesText)+'</p>':''}${card.description&&card.description!==card.rulesText?'<p class="card-lore">'+esc(card.description)+'</p>':''}`);
 }
 $('shareDeck').onclick=()=>{
  const errors=deckErrors();if(errors.length){sheet('Finish your deck first','<p>'+esc(errors.join(' '))+'</p>');return;}
  const code=ICDeckCode.encode(state);
  sheet('Share your deck','<p>This code recreates your faction, ally, Capital and every card. Share the whole code.</p><label for="deckCode">Deck code</label><textarea id="deckCode" readonly spellcheck="false"></textarea><button id="copyCode">Copy deck code</button><p id="copyStatus" role="status"></p><p>Import this code in the desktop Hex &amp; Allies 0.2 Deck Builder.</p>');
  $('deckCode').value=code;
  $('copyCode').onclick=async()=>{try{await navigator.clipboard.writeText(code);$('copyStatus').textContent='Deck code copied.';}catch{$('deckCode').focus();$('deckCode').select();$('copyStatus').textContent='Code selected. Use Copy on your device.';}};
 };
 $('importDeck').onclick=()=>{
  sheet('Import a shared deck','<label for="importCode">Paste the complete deck code</label><textarea id="importCode" spellcheck="false" maxlength="64000"></textarea><button id="previewCode">Preview deck</button><p id="importError" role="alert"></p><div id="importPreview"></div>');
  $('importCode').oninput=()=>{$('importPreview').replaceChildren();$('importError').textContent='';};
  $('previewCode').onclick=()=>{
   $('importPreview').replaceChildren();$('importError').textContent='';
   try {
    const candidate=ICDeckCode.decode($('importCode').value,deckErrors),cap=capitals.find(c=>c.id===candidate.capital);
    $('importPreview').innerHTML='<h4>'+esc(title(candidate.primary)+(candidate.ally?' + '+title(candidate.ally):' · No ally'))+'</h4><p>'+esc(cap.name)+'</p><p>'+esc(cap.passiveText)+'</p><ul>'+Object.entries(candidate.counts).map(([id,n])=>'<li>'+n+' × '+esc(cards.find(c=>c.id===id).name)+'</li>').join('')+'</ul><p>Import replaces the draft currently on screen. Save draft afterward to keep it in this browser.</p><button id="applyImport">Replace draft with this deck</button>';
    $('applyImport').onclick=()=>{Object.assign(state,candidate,{step:3,view:'deck',filter:''});$('sheet').close();renderShell();renderDeck();};
   }catch(error){$('importError').textContent=error.message;}
  };
 };

 window.ICPrototype={state,cardRules,cardStats,inspectCard,distance,deckErrors,eligible,cards,renderDeck,renderShell,sampleUnits};
 window.addEventListener('resize',()=>renderBattle());
 renderShell();renderDeck();
})();
