/* Compare production Java directly with the accepted WebGL functions and Three.js.
 * No Minecraft client, renderer stub or reimplemented reference curve is involved.
 */
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),cp=require('node:child_process'),os=require('node:os'),assert=require('node:assert/strict');
const project=path.resolve(__dirname,'..'),art=path.join(project,'art');
const source=f=>fs.readFileSync(path.join(art,f),'utf8');
const THREE=require(path.join(art,'revision84-school-preview/vendor/three.min.js'));
const context={window:{},THREE};vm.createContext(context);
for(const f of ['revision84-school-preview/presentation-motion.js','revision84-school-preview/item-pose.js',
 'revision89-repeat-upgrade-idle/reveal-return.js','revision89-repeat-upgrade-idle/idle-float.js'])vm.runInContext(source(f),context,{filename:f});
// Runtime feedback changes only clearance and idle tuning; retain the approved curve functions.
const OLD=context.window.ChamberIdleFloat;
const tuned={window:{}};vm.createContext(tuned);
vm.runInContext(source('revision89-repeat-upgrade-idle/idle-float.js').replace('amplitude=.008,period=4.8','amplitude=.035,period=5.6'),tuned);
const M=context.window.LuminousMotion,F=tuned.window.ChamberIdleFloat,displayLift=.40;
const returned=context.window.RevealRepeatIdle.create(M,1.12,90,132),P=context.window.LuminousItemPose.create(THREE,M);
// These functions are copied byte-for-byte out of the approved UI runtime.
const runtime=source('revision89-repeat-upgrade-idle/operations-runtime.js');
const start=runtime.indexOf('  function completionRotation(dt)'),end=runtime.indexOf('  function draw(t)',start);
assert(start>=0&&end>start);
const operations={THREE,ChamberIdleFloat:F,config:{bookHeight:1.52},WORK_HEIGHT:1.68,WORK_SECONDS:3.5,
 COMPLETION_SETTLE_SECONDS:1.2,clamp:x=>Math.max(0,Math.min(1,x)),camera:{quaternion:new THREE.Quaternion()}};
vm.createContext(operations);vm.runInContext('const ease=x=>{x=clamp(x);return x*x*(3-2*x)};'+runtime.slice(start,end)+';globalThis.originalPose=pose;globalThis.originalItemRotation=itemRotation;',operations);
const rows=[],expected=[],labels=[];
function add(label,row,values){labels.push(label);rows.push(row.join(','));expected.push(values);}
const keys=['mainX','mainY','secondaryX','secondaryY','mergeProgress','mainAngle','orientationCurveT','angularPitch','angularYaw','angularRoll',
 'imprintAlpha','inertiaOffset','inertiaRoll','angularRecoil','swayEnvelope','mainSettle','secondarySettle','floatClock','hoverClock'];
for(let tick=0;tick<=132;tick+=.125){
 const s=M.sample(tick),r=returned.sample(tick);s.mainY+=r.main+displayLift;s.secondaryY+=r.secondary+displayLift;
 add('reveal '+tick,['scroll',tick],[...keys.map(k=>s[k]),r.progress]);
 const seconds=19.3+tick*M.secondsPerTick,gate=1-Math.max(0,Math.min(1,tick/12))**2*(3-2*Math.max(0,Math.min(1,tick/12)))
  +Math.max(0,Math.min(1,(tick-88)/22))**2*(3-2*Math.max(0,Math.min(1,(tick-88)/22)));
 const h={...s,mainY:s.mainY+F.amplitude*Math.sin(seconds*2*Math.PI/F.period)*gate,
  secondaryY:s.secondaryY+F.amplitude*Math.sin(seconds*2*Math.PI/F.period+F.secondaryPhase)*gate};
 add('reveal idle '+tick,['scroll',tick,seconds],[...keys.map(k=>h[k]),r.progress]);
}
const rests=[new THREE.Quaternion(),new THREE.Quaternion().setFromEuler(new THREE.Euler(.24,Math.PI+.46,0,'XYZ')),
 new THREE.Quaternion().setFromEuler(new THREE.Euler(-.3,-1.2,.12,'XYZ')),
 new THREE.Quaternion().setFromEuler(new THREE.Euler(.6,2.9,-.2,'XYZ'))];
for(const rest of rests)for(let tick=0;tick<=132;tick+=.25){
 const q=P.apply(new THREE.Quaternion(),M.sample(tick),rest);
 add('reveal quaternion '+tick,['quaternion',tick,...rest.toArray()],q.toArray());
}
for(const operation of [false,true])for(const secondary of [false,true]){
 for(let i=0;i<=350;i++){
  const time=i/100,p=F.processing(time,{operation,secondary,baseHeight:1.52,workHeight:1.68});
  add('processing '+operation+' '+secondary+' '+time,['processing',time,operation,secondary],[p.y,p.vy,p.ay]);
 }
 for(let i=0;i<=300;i++){
  const time=i/100,clock=18.72+time,p=F.completed(time,clock,{operation,secondary,baseHeight:1.52,workHeight:1.68});
  add('completed '+operation+' '+secondary+' '+time,['completed',time,clock,operation,secondary],[p.y,p.vy,p.ay]);
 }
}
for(let i=0;i<=960;i++){
 const time=i/100,p=F.idle(time,1.1);
 add('idle '+time,['idle',time,1.1],[p.y,p.vy,p.ay]);
}
for(let i=0;i<=300;i++){
 const time=i/100,p=operations.originalPose(time,true,true,18.72+time);
 const angular=vm.runInContext('completionRotation('+time+')',operations);
 add('completion rotation '+time,['rotation',time],[angular.y,angular.vy,angular.ay]);
 for(const camera of rests)for(const slot of [0,1]){
  operations.camera.quaternion.copy(camera);
  const mesh=new THREE.Object3D();operations.originalItemRotation(mesh,p,false,slot);
  add('item quaternion '+time+' '+slot,['itemQuaternion',time,18.72+time,slot,...camera.toArray()],mesh.quaternion.toArray());
 }
}
const output=fs.mkdtempSync(path.join(os.tmpdir(),'arcane-chamber-motion-'));
try{
 const files=['src/main/java/dev/expedition/arcane/client/ScrollRevealMotion.java','src/main/java/dev/expedition/arcane/client/ChamberItemMotion.java','tools/VerifyChamberMotion.java'];
 const compile=cp.spawnSync('javac',['-d',output,...files],{cwd:project,encoding:'utf8'});
 assert.equal(compile.status,0,compile.stderr);
 const run=cp.spawnSync('java',['-cp',output,'VerifyChamberMotion'],{input:rows.join('\n')+'\n',encoding:'utf8',maxBuffer:16*1024*1024});
 assert.equal(run.status,0,run.stderr);
 const actual=run.stdout.trim().split('\n').map(row=>row.split(',').map(Number));
 assert.equal(actual.length,expected.length);
 let maxError=0,scalars=0;
 for(let i=0;i<expected.length;i++){
  assert.equal(actual[i].length,expected[i].length,labels[i]);
  // Quaternions q and -q represent the same pose.
  const sign=actual[i].length===4&&actual[i].reduce((sum,v,k)=>sum+v*expected[i][k],0)<0?-1:1;
  for(let k=0;k<expected[i].length;k++){
   const error=Math.abs(actual[i][k]*sign-expected[i][k]);
   assert(Number.isFinite(error)&&error<2e-12,labels[i]+' field '+k+': '+actual[i][k]+' != '+expected[i][k]+' (error '+error+')');
   maxError=Math.max(maxError,error);scalars++;
  }
 }
 const report={status:'PASS',javaVersion:'17',reference:'WebGL revision84 motion/item pose and revision89 return/operations, translated +.40 blocks; idle retuned to .035 blocks / 5.6 seconds',
  sampleRows:rows.length,scalarComparisons:scalars,maxAbsoluteError:maxError,durationLogicalTicks:M.duration,durationSeconds:M.defaultSeconds,
  serverCompletionTicks:Math.ceil(M.defaultSeconds*20),idleAmplitude:F.amplitude,idlePeriodSeconds:F.period,completionSettleSeconds:F.completionSettleSeconds,
  scope:['full reveal at 1/8 logical tick resolution','four camera quaternion targets','primary and secondary idle phases',
   'normal book and operation work paths','C2 completion heights and angular stop','both returned item orientations']};
 console.log(JSON.stringify(report,null,2));
}finally{fs.rmSync(output,{recursive:true,force:true});}
