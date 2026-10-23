import fs from 'node:fs';
import path from 'node:path';
import { createRequire, stripTypeScriptTypes } from 'node:module';
import { pathToFileURL, fileURLToPath } from 'node:url';
const require=createRequire(import.meta.url);
const {createCanvas,DOMMatrix}=require('@napi-rs/canvas');
const root=path.dirname(fileURLToPath(import.meta.url));
const source=path.join(root,'robot-heads-main','src','tv');
const built=path.join(root,'render-js');fs.mkdirSync(built,{recursive:true});
for(const name of ['geometry','light','faces','sim','render']){
  let js=stripTypeScriptTypes(fs.readFileSync(path.join(source,name+'.ts'),'utf8'),{mode:'transform'});
  js=js.replace(/(from\s+['"])(\.\.?\/[^'"]+)(['"])/g,(_m,a,b,c)=>a+b+'.mjs'+c);
  fs.writeFileSync(path.join(built,name+'.mjs'),js);
}
globalThis.document={createElement:()=>createCanvas(1,1)};globalThis.DOMMatrix=DOMMatrix;
const {RobotRenderer,frameFor}=await import(pathToFileURL(path.join(built,'render.mjs')));
const {getShape}=await import(pathToFileURL(path.join(built,'geometry.mjs')));
const {RobotSim,restPose}=await import(pathToFileURL(path.join(built,'sim.mjs')));
const {linear}=await import(pathToFileURL(path.join(built,'light.mjs')));
const {LIGHTS}=await import(pathToFileURL(path.join(built,'faces.mjs')));
// Keep notification lights steady; state changes already communicate success/error.
LIGHTS.error.pulse=()=>.8;LIGHTS.searching.pulse=t=>.75+.15*Math.sin(t*1.6);
const palette={shell:{albedo:linear([244,77,0]),f0:.05,metal:.15,spec:.55,shininess:70,rim:.9},trim:{albedo:linear([39,42,45]),f0:.2,metal:.65,spec:.8,shininess:45,rim:.7},led:[147,255,172]};
const shape=getShape('square'),frame=frameFor(shape,true,160),W=Math.round(frame.box+2*frame.padSide),H=Math.round(frame.box+frame.padTop+frame.padBottom);
const out=path.join(root,'generated');fs.mkdirSync(out,{recursive:true});
const states=['idle','thinking','searching','working','happy','error'];const count=64,columns=8;
const manifest={source:'https://github.com/fayazara/robot-heads',shape:'square',shell:'#f44d00',trim:'#272a2d',led:'#93ffac',box:frame.box,frameWidth:W,frameHeight:H,padSide:frame.padSide,padTop:frame.padTop,frames:count,columns,states:{}};
for(const state of states){
 const renderer=new RobotRenderer(),canvas=createCanvas(W,H),ctx=canvas.getContext('2d'),atlas=createCanvas(W*columns,H*8),a=atlas.getContext('2d'),sim=new RobotSim(.3,state);sim.age=10;sim.switched=10;
 for(let i=0;i<count;i++){
  const p=i/count,t=p*Math.PI*2,pose={...restPose(state)};pose.yaw+=.06*Math.sin(t);pose.roll+=.018*Math.sin(t);pose.lift=.018*(1-Math.cos(t));pose.antennaX=.022*Math.sin(t);
  if(state==='thinking'){pose.yaw+=.06*Math.sin(t);pose.pitch+=.025*Math.sin(t);}
  if(state==='searching'){pose.yaw=.22*Math.sin(t);pose.x=.015*Math.sin(t);}
  if(state==='working'){pose.lift=.025*(1-Math.cos(t*2));pose.pitch+=.025*Math.sin(t*2);}
  if(state==='happy'){pose.lift=.10*(1-Math.cos(t*2))/2;pose.roll=.05*Math.sin(t*2);pose.antennaX=.055*Math.sin(t*2);}
  if(state==='error'){pose.yaw=.07*Math.sin(t*2);}
  sim.pose=pose;sim.face={lookX:state==='idle'?.7*Math.sin(t):0,lookY:0,blink:state==='idle'?Math.max(0,1-Math.abs(p-.6)/.035):0,time:state==='thinking'?2.5+p*2.5:state==='searching'?p*Math.PI*2/1.6:state==='working'?p*2.6:state==='happy'?p*Math.PI*2/5.6:state==='error'?p*Math.PI*2/7:p*Math.PI*2/1.4,side:1,seed:.3};
  renderer.draw(ctx,frame,1,pose,sim,state,shape,palette,{floorShadow:true,antenna:true});a.drawImage(canvas,(i%columns)*W,Math.floor(i/columns)*H);
 }
 fs.writeFileSync(path.join(out,state+'.png'),atlas.toBuffer('image/png'));
 renderer.draw(ctx,frame,1,restPose(state),null,state,shape,palette,{floorShadow:true,antenna:true});fs.writeFileSync(path.join(out,state+'-still.png'),canvas.toBuffer('image/png'));
 manifest.states[state]={fps:state==='idle'?16:30};console.log(state+': '+W+'x'+H+' x '+count+' frames');
}
fs.writeFileSync(path.join(out,'manifest.json'),JSON.stringify(manifest,null,2));
console.log('Robot assets exported');