'use strict';
// Self-contained stable IDs; checksum detects copying errors, not tampering.
window.ICDeckCode = (() => {
 const checksum = text => {let h=2166136261;for(const byte of new TextEncoder().encode(text))h=Math.imul(h^byte,16777619);return (h>>>0).toString(16).padStart(8,'0');};
 function encode(deck) {
  const payload=JSON.stringify([deck.primary,deck.ally,deck.capital,Object.entries(deck.counts).sort(([a],[b])=>a.localeCompare(b))]);
  const body=btoa(String.fromCharCode(...new TextEncoder().encode(payload))).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');
  return 'ICD1.'+body+'.'+checksum(body);
 }
 function decode(input,validate) {
  if(typeof input!=='string'||input.length>64000)throw Error('Deck code is too long.');
  const code=input.replace(/\s/g,'');
  if(!code.startsWith('ICD1.'))throw Error('Unsupported deck code. Use an ICD1 code from this prototype.');
  const match=/^ICD1\.([A-Za-z0-9_-]+)\.([a-f0-9]{8})$/.exec(code);
  if(!match||checksum(match[1])!==match[2])throw Error('Deck code is incomplete or damaged. Copy the whole code again.');
  let value;
  try {value=JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(Uint8Array.from(atob(match[1].replace(/-/g,'+').replace(/_/g,'/')),c=>c.charCodeAt(0))));}catch {throw Error('Deck code data could not be read.');}
  if(!Array.isArray(value)||value.length!==4||typeof value[0]!=='string'||!(value[1]===null||typeof value[1]==='string')||typeof value[2]!=='string'||!Array.isArray(value[3])||value[3].length>500)throw Error('Invalid deck format.');
  const counts=Object.create(null);
  for(const entry of value[3]) {
   if(!Array.isArray(entry)||entry.length!==2||typeof entry[0]!=='string'||!Number.isInteger(entry[1])||entry[1]<1||entry[1]>4||Object.hasOwn(counts,entry[0]))throw Error('Invalid or duplicate card entry.');
   counts[entry[0]]=entry[1];
  }
  const deck={primary:value[0],ally:value[1],capital:value[2],counts};
  const errors=validate(deck);if(errors.length)throw Error(errors.join(' '));
  return deck;
 }
 return {encode,decode};
})();
