const fs=require('fs'),vm=require('vm'); const context={window:{}};vm.createContext(context);vm.runInContext(fs.readFileSync(process.argv[2]+'/luminous-crystals.js','utf8'),context);
const assets=JSON.parse(fs.readFileSync(process.argv[2]+'/native-assets.json','utf8')).items;
const java=JSON.parse(fs.readFileSync(process.argv[3],'utf8'));let max=0,n=0,counts=[];
function same(a,b,name){let d=Math.abs(a-b);if(!Number.isFinite(d)||d>1e-10)throw new Error(name+': '+a+' != '+b);max=Math.max(max,d);n++}
for(const row of java){const primary=row.role==='primary', js=context.window.LuminousCrystals.create(assets[primary?'blank':'focus'],primary?.66:.32,row.seed,row.role),j=row.layout;
 const cubes=js.layout.filter(c=>c.kind!=='dust'),dust=js.layout.filter(c=>c.kind==='dust');
 same(j.crystals.length,cubes.length,'cubeCount');same(j.dust.length,dust.length,'dustCount');same(j.minY,js.stats.bounds.minY,'minY');same(j.maxY,js.stats.bounds.maxY,'maxY');
 j.crystals.forEach((v,i)=>['x','y','z','size','birth'].forEach(k=>same(v[k],cubes[i][k],row.role+' '+i+' '+k)));
 j.dust.forEach((v,i)=>{for(const k of ['x','y','z','size','birth','distance','angle','drag','swirl','bendX','bendY','lift','depth','alpha','wave','index'])same(v[k],dust[i][k],'dust '+i+' '+k);same(v.life,dust[i].lifetime,'lifetime');if(v.absorb!==dust[i].absorb)throw new Error('absorb');});
 const absorbed=j.dust.filter(d=>d.absorb).length;
 if(primary&&j.dust.length!==28||!primary&&(j.dust.length!==40||absorbed!==20))throw new Error('counts');
 for(const c of j.crystals)if(c.size<.045||c.size>.082)throw new Error('cube-size');
 counts.push({role:row.role,seed:row.seed,cubes:j.crystals.length,dust:j.dust.length,absorbed});
}
console.log(JSON.stringify({pass:true,numericValuesCompared:n,maxAbsoluteError:max,counts},null,2));
