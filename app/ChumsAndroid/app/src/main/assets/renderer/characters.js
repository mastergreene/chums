/* Android canvas port of the upstream character state/shape contracts.
 * Ray uses SolArt's original outlines; Noodle uses its baked 3-D shape journeys;
 * Tangle uses the original face glyphs; Crunch/Nana run the upstream PIX renderer;
 * Stretch plays the original state-specific Blender clips, without a watch shell.
 * This page has no credentials, Android JS interface, remote resources or navigation.
 */
(() => {
  'use strict';
  const canvas = document.getElementById('stage'), ctx = canvas.getContext('2d'), clipImage = document.getElementById('stretch');
  const TAU = Math.PI * 2, clamp = (v,a,b) => v<a?a:v>b?b:v, ease = x => {x=clamp(x,0,1);return x*x*(3-2*x)}, mix=(a,b,k)=>a+(b-a)*k;
  const states = new Set(['idle','listening','thinking','working','subagents','speaking','done','needs','approval','blocked','dizzy','sleep','compacting','waiting']);
  const aliases = {needs_you:'needs',limited:'waiting',error:'blocked',unknown:'blocked',reconnecting:'sleep'};
  let paintAt=-1;
  let input={id:'noodle',state:'idle',caption:'',tools:[],subagents:0}, state='idle', stateAt=0, t=0, last=0, disposed=false;
  let pokeAt=-100,petAt=-100,crown=0,look=[0,0],drag=null,distance=0,pressedAt=0,doneUntil=0,lastEvent='',lastTool='',failedUntil=0;
  let ray,tangle,noodle,meta,fromShape=null,toShape=0,morphAt=0,route=[],routeAt=0,stretchName='',stretchTimer=0,stretchLast='';
  const normalized = s => states.has(s) ? s : aliases[s] || 'blocked';
  const get = name => fetch(name).then(r=>{if(!r.ok)throw Error(name);return r.json()});
  const ready = Promise.all([get('ray.json'),get('tangle.json'),get('noodle.json'),get('stretch/clips.json')]).then(x=>{[ray,tangle,noodle,meta]=x;fromShape=noodle.shapes[0]});
  function transition(s) {
    if(s===state)return;
    const before=state;state=s;stateAt=t;
    if(s==='done')doneUntil=t+2.6;
    if(before==='speaking'&&s==='idle'){state='done';doneUntil=t+2.6}
    stretchName='';stretchTimer=0;
  }
  window.ChumsCharacter={
    set(value){
      if(!value||typeof value!=='object')return;
      const old=input;input={...input,...value};
      if(old.id!==input.id){state='';stretchName='';fromShape=null;toShape=0;route=[];crown=0;doneUntil=0;lastEvent='';lastTool=''}
      if(input.eventId&&input.eventId!==lastEvent){lastEvent=input.eventId;if(input.kind==='done'&&normalized(input.state)==='idle')doneUntil=t+2.6}
      if(input.toolId&&input.toolId!==lastTool){lastTool=input.toolId;if(input.toolFailed)failedUntil=t+0.7;stretchTimer=Math.min(stretchTimer,0.1)}
      const wanted=normalized(input.state);transition(wanted==='idle'&&t<doneUntil?'done':wanted);
    },
    snapshot(){return {id:input.id,state,source:input.id==='stretch'?stretchName:'live',time:t,tool:lastTool}},
    dispose(){disposed=true;clipImage.src='';}
  };
  function path(points,closed=false){ctx.beginPath();for(let i=0;i<points.length;i+=2){if(!i)ctx.moveTo(points[i],points[i+1]);else ctx.lineTo(points[i],points[i+1])}if(closed)ctx.closePath()}
  function line(points,width=3,color='#fff'){ctx.strokeStyle=color;ctx.lineWidth=width;ctx.lineJoin='round';ctx.lineCap='round';path(points);ctx.stroke()}
  function oval(x,y,rx,ry,color){ctx.fillStyle=color;ctx.beginPath();ctx.ellipse(x,y,Math.max(.1,rx),Math.max(.1,ry),0,0,TAU);ctx.fill()}
  function curve(x,y,r,a,b,width=3,color='#fff'){ctx.strokeStyle=color;ctx.lineWidth=width;ctx.lineCap='round';ctx.beginPath();ctx.arc(x,y,r,a,b);ctx.stroke()}
  function label(s,x,y,size=14,color='#fff'){ctx.fillStyle=color;ctx.font=`600 ${size}px sans-serif`;ctx.textAlign='center';ctx.fillText(s,x,y)}
  function spiral(x,y,r){const p=[];for(let i=0;i<65;i++){const u=i/64,a=u*TAU*1.7+t*2;p.push(x+Math.cos(a)*r*u,y+Math.sin(a)*r*u)}line(p,2.5)}
  const blink=()=> state==='sleep'||Math.sin(t*.86)>0.995;
  function face(kind='normal',color='#fff') {
    const happy=state==='done'||t-petAt<1, closed=blink(), angry=state==='blocked'||t<failedUntil;
    for(const side of [-1,1]){
      const x=side*25+look[0]*3,y=-9+look[1]*2;
      if(state==='dizzy')spiral(x,y,12);
      else if(happy)curve(x,y+6,12,Math.PI*1.1,Math.PI*1.9,3,color);
      else if(closed)line([x-10,y,x+10,y],3,color);
      else if(kind==='peeps'){
        const h=state==='working'?11:state==='listening'?35:29;
        ctx.fillStyle=color;ctx.beginPath();ctx.roundRect(x-13,y-h/2,26,h,10);ctx.fill();
      } else if(kind==='squint') {
        if(state==='listening')curve(x,y,13,0,TAU,3,color);
        else if(state==='thinking'&&side===1)curve(x,y,11,0,TAU,3,color);
        else line([x-3,y-12,x+2,y+12],3,color);
      } else {oval(x,y,12,15,'#fcebd4');oval(x+look[0]*3,y+look[1]*3,5,7,'#090807')}
      if(angry)line([x-12,y-20-side*4,x+12,y-20+side*4],3,color);
      if(state==='needs')line([x-12,y-22+side*4,x+12,y-22-side*4],3,color);
    }
    if(kind==='peeps')return;
    if(state==='speaking'||state==='listening')oval(0,25,8,5+Math.abs(Math.sin(t*10))*8,color);
    else if(state==='blocked')line([-17,24,-10,19,-3,26,4,20,11,26,18,21],3,color);
    else if(state==='thinking')oval(0,25,4,4,color);
    else if(state==='needs')curve(0,48,24,Math.PI*1.22,Math.PI*1.78,3,color);
    else curve(0,6,25,Math.PI*.28,Math.PI*.72,3,color);
  }
  function simpleFace(kind){
    ctx.save();ctx.translate(99+(drag?drag[0]*.1:0),120+Math.sin(t*1.7)*2-(t-pokeAt<.6?Math.sin((t-pokeAt)/.6*Math.PI)*13:0));
    if(state==='thinking')ctx.rotate(-.12);if(state==='dizzy')ctx.rotate(Math.sin(t*3)*.2);
    if(state==='sleep')ctx.scale(1,.6);
    face(kind,state==='approval'?'#ffbd66':'#fff');
    if(state==='thinking')label('?',46,-40);
    if(state==='needs')label('!',0,-51,18,'#ffbd66');
    ctx.restore();
    if(state==='subagents')for(let i=0;i<3;i++){ctx.save();ctx.translate(99+Math.cos(t*.7+i*TAU/3)*65,120+Math.sin(t*.7+i*TAU/3)*58);ctx.scale(.28,.28);face(kind);ctx.restore()}
  }
  // Original SolArt arm sampling: 10 arms, K samples on each side of each arm.
  function drawRay(){
    if(!ray)return;
    const mood=state==='blocked'?'angry':state==='sleep'?'melt':state==='dizzy'?'dizzy':'base';const shape=ray[mood],K=shape.length/40;
    let speed=state==='working'?4.2:state==='thinking'?.9:state==='sleep'?.6:1.5;
    let amp=state==='working'?.42:state==='thinking'?.14:state==='blocked'?.08:.24;
    const turn=(state==='working'?t*.45:state==='dizzy'?t:0)+crown, RC=37;
    ctx.save();ctx.translate(99,124-(state==='done'?Math.abs(Math.sin((t-stateAt)*4))*7:0));ctx.rotate(state==='thinking'?-.157:turn);
    const outline=[];
    for(let a=0;a<10;a++){
      const o=a*K*4,R=[],L=[],C=[],nr=[],nl=[],lengths=[0];let total=0;
      for(let k=0;k<K;k++){R.push([shape[o+2*k],shape[o+2*k+1]]);L.push([shape[o+2*K+2*k],shape[o+2*K+2*k+1]]);C.push([(R[k][0]+L[k][0])/2,(R[k][1]+L[k][1])/2]);if(k){total+=Math.hypot(C[k][0]-C[k-1][0],C[k][1]-C[k-1][1]);lengths.push(total)}}
      let phi=0,nc=C[0].slice();nr.push(R[0]);nl.push(L[0]);
      const phase=a*2.399+.7*Math.sin(a*1.7),gain=.82+.18*(1+Math.sin(a*2.9+1));
      for(let k=1;k<K;k++){
        const u=lengths[k]/total,du=(lengths[k]-lengths[k-1])/total,w=ease((u-.1)/.35);
        let bias=state==='thinking'?.5:state==='needs'?.2*Math.sin(t*3):state==='approval'?.3:0;
        if(drag)bias+=clamp(drag[0]*Math.sin(ray.armAngle[a])*.004,-.6,.6);
        phi+=du*(amp*gain*Math.sin(TAU*.85*u-t*speed+phase)*2.2+bias*1.6)*w;
        const c=Math.cos(phi),s=Math.sin(phi),dx=C[k][0]-C[k-1][0],dy=C[k][1]-C[k-1][1];
        nc=[nc[0]+dx*c-dy*s,nc[1]+dx*s+dy*c];
        for(const [input,output] of [[R,nr],[L,nl]]){const x=input[k][0]-C[k][0],y=input[k][1]-C[k][1];output.push([nc[0]+x*c-y*s,nc[1]+x*s+y*c])}
      }
      [...nr,...nl.slice(1,-1).reverse()].forEach(p=>outline.push(p[0]*RC,p[1]*RC));
    }
    ctx.fillStyle='#f2ae08';path(outline,true);ctx.fill();ctx.rotate(-turn);
    const f={idle:['wSurL','wSurR','smile','pNeuL','pNeuR'],listening:['wSurL','wSurR','O','pSurL','pSurR'],thinking:['wThkL','wThkR','dot','pThkL','pThkR'],working:['wSurL','wSurR','flat','pNeuL','pNeuR'],needs:['wSadL','wSadR','frown','pSadL','pSadR'],approval:['wSurL','wSurR','dotC','pNeuL','pNeuR'],blocked:['wAngL','wAngR','zig','pAngL','pAngR']}[state]||['wSurL','wSurR','smile','pNeuL','pNeuR'];
    const glyph=(name,color)=>{const p=ray.glyphs[name];if(!p)return;ctx.fillStyle=color;path(p.map(x=>x*RC),true);ctx.fill()};
    if(state==='sleep'||state==='done'){glyph(state==='done'?'happyL':'dashL','#040201');glyph(state==='done'?'happyR':'dashR','#040201')}
    else if(state==='dizzy'){spiral(-15,-7,10);spiral(13,-7,10)}
    else for(let i=0;i<2;i++){glyph(f[i],'#f9eccd');const p=ray.pupils[f[3+i]];oval(p[0]*RC+look[0]*2,p[1]*RC+look[1]*2,p[2]*RC,p[3]*RC,'#040201')}
    glyph(state==='done'?'D':state==='sleep'?'yawn':f[2],'#040201');
    if(state==='blocked'){glyph('browL','#040201');glyph('browR','#040201')}
    if(state==='thinking')label('?',35,-40,18,'#f9eccd');ctx.restore();
  }
  function drawTangle(){
    ctx.save();ctx.translate(99+(drag?drag[0]*.12:0),125+(state==='needs'?5:0));ctx.scale(.49,.49);
    const flat=state==='sleep',working=state==='working'||state==='done',speed=working?2.6:state==='thinking'?.8:1.75,p=t*speed;
    ctx.strokeStyle='#9bc2e5';ctx.lineWidth=15.5;ctx.lineCap='round';ctx.lineJoin='round';ctx.beginPath();
    for(let i=0;i<440;i++){
      const u=i/379,sc=u-.5,ring=.36,turn=p*.1+crown,cang=TAU*1.2*sc+.6+turn*.5;
      const th=TAU*14*sc+turn+.2*Math.sin(u*7-p*.2),ow=working?.45*ease((Math.sin(TAU*14*sc/3+.4)+.1)):0;
      const r=122*(.5*(1+.2*Math.sin(u*38+p*.1))*(1-ow)+ow*.95),psi=cang+.2;
      const a=th-psi,spike=state==='blocked'?1+.15*Math.pow(Math.max(0,Math.cos(a)),2)*Math.sin(i*.8):1;
      let x=Math.cos(cang)*ring*122+Math.cos(a)*r*Math.cos(psi)-Math.sin(a)*r*.7*Math.sin(psi);
      let y=Math.sin(cang)*ring*122+Math.cos(a)*r*Math.sin(psi)+Math.sin(a)*r*.7*Math.cos(psi);
      x*=spike;y*=flat?.25:1;
      if(!i)ctx.moveTo(x,y);else ctx.lineTo(x,y);
    }
    ctx.stroke();ctx.restore();
    ctx.save();ctx.translate(99,flat?132:120);ctx.scale(.55,.55);
    const pose=state==='blocked'?'10':state==='needs'?'02':state==='done'?'01':state==='listening'?'11':state==='sleep'?'20':'00';
    if(tangle){
      const anchor=key=>{const p=tangle[key];if(!p)return [0,0];const xs=p.filter((_,i)=>i%2===0),ys=p.filter((_,i)=>i%2===1);return [(Math.min(...xs)+Math.max(...xs))/2,(Math.min(...ys)+Math.max(...ys))/2]};
      const left=anchor(pose+(pose==='10'?'.eLa':'.eL')),right=anchor(pose+(pose==='10'?'.eRa':'.eR')),origin=[(left[0]+right[0])/2,(left[1]+right[1])/2];
      const marks=pose==='10'?['eLa','eRa','eLb','eRb','m']:['eL','eR','m',...(pose==='02'?['bL','bR']:[])];
      for(const part of marks){
        const dots=pose==='00'&&part!=='m',key=(dots?'21':pose)+'.'+part,p=tangle[key];if(!p)continue;
        const from=anchor(key),to=anchor(pose+'.'+part),k=dots?1.2:1;
        const points=p.map((v,i)=>(v-from[i%2])*k+to[i%2]-origin[i%2]+(i%2===0?look[0]*3:look[1]*2));
        line(points,15,'#9bc2e5');line(points,5,'#fff');
      }
      if(state==='dizzy'){spiral(-24,0,13);spiral(24,0,13)}
    }else face();ctx.restore();
  }

  function noodleTarget(){return {listening:6,thinking:5,working:1,subagents:2,speaking:0,done:2,needs:6,blocked:8,dizzy:4,sleep:3,approval:0,compacting:3,waiting:6}[state]??0}
  function beginJourney(target){
    if(!noodle||target===toShape)return;
    const start=toShape,visited=new Set([start]),q=[[start,[]]];let legs=[];
    while(q.length){const [at,path]=q.shift();if(at===target){legs=path;break}for(const e of noodle.edges){const next=e.a===at?e.b:e.b===at?e.a:null;if(next===null||visited.has(next))continue;visited.add(next);q.push([next,path.concat([{edge:e,reverse:e.b===at}])])}}
    route=legs;routeAt=t;fromShape=sampleNoodle();toShape=target;morphAt=t;
  }
  function sampleNoodle(){
    if(!noodle)return null;
    if(!route.length)return noodle.shapes[toShape];
    let elapsed=(t-routeAt)*1.2;
    for(const leg of route){const frames=leg.reverse?leg.edge.frames.slice().reverse():leg.edge.frames,duration=Math.max(.4,leg.edge.seconds);if(elapsed>duration){elapsed-=duration;continue}
      const u=ease(elapsed/duration)*(frames.length-1),i=Math.floor(u),k=u-i,a=frames[i],b=frames[Math.min(i+1,frames.length-1)];return a.map((p,j)=>p.map((v,d)=>mix(v,b[j][d],k)))}
    route=[];return noodle.shapes[toShape];
  }
  function drawNoodle(){
    if(!noodle)return;beginJourney(noodleTarget());const points=sampleNoodle();if(!points)return;
    let spin=([1,2,4].includes(toShape)?t*(state==='working'?1.5:.6):0)+crown;
    const transformed=points.map((p,i)=>{const x=p[0]*Math.cos(spin)+p[2]*Math.sin(spin),z=-p[0]*Math.sin(spin)+p[2]*Math.cos(spin);return [x+Math.sin(i*.35-t*1.4)*1.6,p[1],z,i]});
    const minX=Math.min(...transformed.map(p=>p[0])),maxX=Math.max(...transformed.map(p=>p[0])),minY=Math.min(...transformed.map(p=>p[1])),maxY=Math.max(...transformed.map(p=>p[1]));
    const scale=Math.min(155/(maxX-minX+29),185/(maxY-minY+29));
    const ox=99-(maxX+minX)/2*scale+(drag?drag[0]*.12:0),oy=130-(maxY+minY)/2*scale-(t-pokeAt<.6?Math.sin((t-pokeAt)/.6*Math.PI)*12:0);
    const dense=[];for(let i=0;i<transformed.length-1;i++)for(let j=0;j<3;j++){const k=j/3,a=transformed[i],b=transformed[i+1];dense.push([mix(a[0],b[0],k),mix(a[1],b[1],k),mix(a[2],b[2],k),mix(a[3],b[3],k)])}
    // Rasterize the frontmost sphere surface as a union, not a stack of shaded
    // circles. A shared z-buffer removes the seams between neighbouring samples.
    const surface=drawNoodle.surface||(drawNoodle.surface=(()=>{const c=document.createElement('canvas');c.width=198;c.height=242;return c})()),g=surface.getContext('2d');
    const image=g.createImageData(198,242),pixels=image.data,zbuffer=new Float32Array(198*242);zbuffer.fill(-1e10);
    const radius=14.5*scale,rr=radius*radius;
    for(const p of dense){
      const u=p[3]/63,v=u*(noodle.albedo.length-1),i=Math.floor(v),a=noodle.albedo[i],b=noodle.albedo[Math.min(i+1,noodle.albedo.length-1)],col=a.map((x,d)=>mix(x,b[d],v-i));
      const cx=p[0]*scale+ox,cy=p[1]*scale+oy,cz=p[2]*scale;
      for(let y=Math.max(0,Math.floor(cy-radius));y<Math.min(242,Math.ceil(cy+radius));y++)for(let x=Math.max(0,Math.floor(cx-radius));x<Math.min(198,Math.ceil(cx+radius));x++){
        const dx=x+.5-cx,dy=y+.5-cy,d2=dx*dx+dy*dy;if(d2>=rr)continue;
        const dz=(rr-d2)**.5,depth=cz+dz,index=y*198+x;if(depth<=zbuffer[index])continue;zbuffer[index]=depth;
        const nx=dx/radius,ny=-dy/radius,nz=dz/radius;
        const light=.84488-.10194*nx+.21310*ny+.55242*nz-.03921*nx*ny+.14323*nx*nz-.16665*ny*nz+.06919*(nx*nx-ny*ny)-.13055*(3*nz*nz-1),o=index*4;
        pixels[o]=clamp(col[0]*light*255,0,255);pixels[o+1]=clamp(col[1]*light*255,0,255);pixels[o+2]=clamp(col[2]*light*255,0,255);pixels[o+3]=255;
      }
    }
    g.putImageData(image,0,0);ctx.drawImage(surface,0,0);
    const head=transformed[63],hx=head[0]*scale+ox,hy=head[1]*scale+oy,r=14.5*scale;
    if(state==='sleep'){line([hx-r*.6,hy-r*.1,hx-r*.1,hy-r*.1],2);line([hx+r*.1,hy-r*.1,hx+r*.6,hy-r*.1],2)}
    else {oval(hx-r*.4,hy-r*.1,r*.27,r*.38,'#fcebd4');oval(hx+r*.4,hy-r*.1,r*.27,r*.38,'#fcebd4');oval(hx-r*.4+look[0],hy,r*.12,r*.16,'#080808');oval(hx+r*.4+look[0],hy,r*.12,r*.16,'#080808')}
  }
  function stretch(){
    if(!meta)return;
    const candidates=Object.entries(meta.clips).filter(([,v])=>v.state===state),enter=candidates.find(([,v])=>v.role==='enter');
    if(!stretchName||t>stretchTimer){let pick;
      if(!stretchName&&enter)pick=enter;
      else {const rests=candidates.filter(([,v])=>v.role==='rest'),beats=candidates.filter(([k,v])=>v.role==='beat'&&k!==stretchLast);pick=(beats.length&&(!rests.length||Math.random()<.35)?beats[Math.floor(Math.random()*beats.length)]:rests[0])||candidates.find(([,v])=>v.role!=='enter')||candidates[0]||Object.entries(meta.clips).find(([k])=>k==='idle_rest')}
      if(pick){stretchName=pick[0];stretchLast=stretchName;stretchTimer=t+pick[1].n/meta.fps;clipImage.src='stretch/'+stretchName+'.webp'}
    }
  }
  function poke(){pokeAt=t;const specials=['poke','bump','pet'];if(input.id==='stretch'&&meta){const k= t-petAt<.5?'pet':specials.find(k=>meta.clips[k]);if(k){stretchName=k;stretchTimer=t+meta.clips[k].n/meta.fps;clipImage.src='stretch/'+k+'.webp'}}}
  const fingers=new Map();let lastAngle=null;
  document.addEventListener('pointerdown',e=>{e.preventDefault();fingers.set(e.pointerId,[e.clientX,e.clientY]);pressedAt=t;distance=0;drag=[0,0];look=[(e.clientX/innerWidth-.5)*2,(e.clientY/innerHeight-.5)*2]});
  document.addEventListener('pointermove',e=>{if(!fingers.has(e.pointerId))return;const prev=fingers.get(e.pointerId);distance+=Math.hypot(e.clientX-prev[0],e.clientY-prev[1]);fingers.set(e.pointerId,[e.clientX,e.clientY]);drag=[(e.clientX/innerWidth-.5)*100,(e.clientY/innerHeight-.5)*100];look=drag.map(v=>clamp(v/50,-1,1));if(distance>30)petAt=t;if(fingers.size===2){const [a,b]=[...fingers.values()],angle=Math.atan2(b[1]-a[1],b[0]-a[0]);if(lastAngle!==null)crown+=angle-lastAngle;lastAngle=angle}});
  const release=e=>{if(!fingers.has(e.pointerId))return;fingers.delete(e.pointerId);if(distance<20)poke();drag=null;lastAngle=null;};
  document.addEventListener('pointerup',release);document.addEventListener('pointercancel',e=>{fingers.delete(e.pointerId);drag=null;lastAngle=null});
  document.addEventListener('wheel',e=>{e.preventDefault();crown+=e.deltaY*.003},{passive:false});
  document.addEventListener('visibilitychange',()=>{last=0});
  function render(now){
    if(disposed)return;if(!last)last=now;const dt=Math.min(.05,(now-last)/1000);last=now;
    if(!document.hidden){t+=dt;if(t-paintAt<1/24){requestAnimationFrame(render);return}paintAt=t;const wanted=normalized(input.state);if(state==='done'&&t>doneUntil&&wanted==='idle')transition('idle');
      const dpr=Math.min(devicePixelRatio||1,2),w=Math.round(innerWidth*dpr),h=Math.round(innerHeight*dpr);if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h}
      ctx.setTransform(w/198,0,0,h/242,0,0);ctx.clearRect(0,0,198,242);ctx.fillStyle='#000';ctx.fillRect(0,0,198,242);
      const isStretch=input.id==='stretch';clipImage.style.display=isStretch?'block':'none';canvas.style.display=isStretch?'none':'block';
      if(isStretch)stretch();else if(input.id==='crunch'||input.id==='nana')PIX.draw(ctx,{species:input.id==='crunch'?'apple':'banana',cx:99,cy:(state==='working'?134:state==='needs'?146:state==='approval'?80:118)-(t-pokeAt<.6?Math.sin((t-pokeAt)/.6*Math.PI)*14:0),px:state==='approval'?3:7,t,state:state==='approval'?'needs':state,stateT:t-stateAt,look:look[0],eyeMode:t-petAt<1?'happy':undefined,text:input.caption||'',tools:input.tools||[],subagents:input.subagents||1,screen:{x:0,y:15,w:198,h:220},room:210,bang:state!=='approval'});
      else if(input.id==='peeps'||input.id==='squint')simpleFace(input.id);else if(input.id==='ray')drawRay();else if(input.id==='tangle')drawTangle();else drawNoodle();
    }requestAnimationFrame(render)
  }
  ready.then(()=>requestAnimationFrame(render)).catch(e=>{document.body.dataset.error=e.message;console.error(e)});
})();
