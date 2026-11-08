// Exercise the real renderer against a real canvas implementation without an Android device.
const assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path'), vm = require('node:vm'), crypto = require('node:crypto');
const { createCanvas } = require(process.env.CHUMS_CANVAS_MODULE || '@napi-rs/canvas');
const root = path.resolve(__dirname, '..'), generated = path.join(root,'app/ChumsAndroid/app/build/generated/liveCharacters');
const output = process.env.CHUMS_RENDER_OUTPUT || path.join(root,'app/ChumsAndroid/app/build/reports/characters');fs.mkdirSync(output,{recursive:true});
const stage=createCanvas(300,366);stage.style={};const clip={style:{},src:''};let frame,clock=1000;const handlers={};
const sandbox={console,Math,JSON,Promise,Set,Map,innerWidth:300,innerHeight:366,devicePixelRatio:1,
  document:{createElement:()=>createCanvas(198,242),hidden:false,body:{dataset:{}},getElementById:id=>id==='stage'?stage:clip,addEventListener:(name,fn)=>handlers[name]=fn},
  requestAnimationFrame:fn=>{frame=fn},fetch:async name=>({ok:true,json:async()=>JSON.parse(fs.readFileSync(path.join(generated,name),'utf8'))}),
  OffscreenCanvas:class {constructor(w,h){return createCanvas(w,h)}}};sandbox.window=sandbox;
vm.createContext(sandbox);vm.runInContext(fs.readFileSync(path.join(root,'design/pix/pix.js'),'utf8'),sandbox);
vm.runInContext(fs.readFileSync(path.join(root,'app/ChumsAndroid/app/src/main/assets/renderer/characters.js'),'utf8'),sandbox);
const advance=(n=30)=>{for(let i=0;i<n;i++){clock+=1000/30;assert.equal(typeof frame,'function','animation started');frame(clock)}};
(async()=>{
  await new Promise(r=>setImmediate(r));await new Promise(r=>setImmediate(r));assert(!sandbox.document.body.dataset.error);
  const ids=['noodle','stretch','peeps','squint','ray','tangle','crunch','nana'];
  const states=['idle','listening','thinking','working','subagents','speaking','done','needs_you','approval','blocked','dizzy','sleep','compacting','limited'];
  let checks=0;
  for(const id of ids){
    const hashes={};
    for(const state of states){
      sandbox.ChumsCharacter.set({id,state,caption:'Here is the result.',tools:['Read hub.mjs','Bash npm test'],subagents:3,eventId:id+'-'+state,kind:state==='done'?'done':'',toolId:id+'-'+state});
      advance(30);
      const actual=sandbox.ChumsCharacter.snapshot();assert.equal(actual.id,id);
      if(id==='stretch')assert.notEqual(actual.source,'live','state clip selected');else assert.equal(actual.source,'live','canvas active');
      if(id==='stretch'){
        assert(clip.src.startsWith('stretch/'));assert(fs.existsSync(path.join(generated,clip.src)),clip.src);
        hashes[state]=clip.src;
      }else{
        const pixels=stage.getContext('2d').getImageData(0,0,300,366).data;
        assert(pixels.some((v,i)=>i%4!==3&&v>25),id+' '+state+' is not blank');
        const png=stage.toBuffer('image/png');hashes[state]=crypto.createHash('sha256').update(png).digest('hex');
        if(['idle','thinking','working','blocked','sleep'].includes(state))fs.writeFileSync(path.join(output,id+'-'+state+'.png'),png);
      }
      checks++;
    }
    assert.notEqual(hashes.idle,hashes.working,id+' reacts to working');assert.notEqual(hashes.working,hashes.sleep,id+' reacts to sleep');
    sandbox.ChumsCharacter.set({id,state:'idle'});advance();
    handlers.pointerdown({preventDefault(){},pointerId:1,clientX:140,clientY:130});handlers.pointermove({pointerId:1,clientX:190,clientY:150});handlers.pointerup({pointerId:1});advance();
    assert.equal(sandbox.ChumsCharacter.snapshot().id,id,'touch does not switch identity');
    console.log('PASS',id,'all states render; working/sleep differ; touch handled');
  }
  sandbox.ChumsCharacter.set({id:'ray',state:'speaking',eventId:'reply'});advance();
  sandbox.ChumsCharacter.set({state:'idle'});advance(1);assert.equal(sandbox.ChumsCharacter.snapshot().state,'done','reply holds done before idle');
  advance(100);assert.equal(sandbox.ChumsCharacter.snapshot().state,'idle','done settles back to idle');
  sandbox.ChumsCharacter.set({id:'noodle',state:'working',toolId:'live-tool'});advance(1);assert.equal(sandbox.ChumsCharacter.snapshot().tool,'live-tool');
  sandbox.ChumsCharacter.set({id:'nana',state:'idle',toolId:''});advance(1);assert.equal(sandbox.ChumsCharacter.snapshot().tool,'','identity switch clears tool event');
  console.log('PASS',checks,'real-canvas state cases; snapshots in',output);
})().catch(e=>{console.error(e);process.exitCode=1});
