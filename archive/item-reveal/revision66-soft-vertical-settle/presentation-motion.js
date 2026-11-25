/* Shared timing variants with a continuously hovering, jointly fading scroll. */
(()=>{
 const absorptionArrival=.65,duration=112,durationOptions=[5.6,6.2,6.8],defaultSeconds=6.8;
 const timeScale=defaultSeconds*20/duration;
 const clamp=x=>Math.max(0,Math.min(1,x)),ease=x=>{x=clamp(x);return x*x*(3-2*x)};
 function sample(tick){
  tick=Math.max(0,Number(tick)||0);
  const seconds=tick/20,lift=ease(tick/16),returnStart=16;
  const mainSettle=ease((tick-returnStart)/62),secondarySettle=ease((tick-returnStart)/56);
  const orientationSettle=ease((tick-12)/22);
  const orientationCurveT=clamp((tick-12)/22);
  // Continue the existing float phase without restarting it. Its clock slows
  // continuously after 58t and reaches the former 78t resting pose at 96t.
  const floatU=clamp((tick-58)/38);
  const floatClock=tick<=58?tick:58+38*floatU-16*floatU*floatU-2*floatU*floatU*floatU;
  const floatSettle=ease((floatClock-returnStart)/62);
  // Keep the original turn arc while position and orientation settle earlier.
  const spinBrakeStart=24,brake=clamp((tick-spinBrakeStart)/18);
  const spinTicks=tick<=spinBrakeStart?tick:spinBrakeStart+18*(brake-brake*brake+brake*brake*brake/3);
  const mainWorkY=1.12+.36*lift+.04*Math.sin(floatClock/20)*lift;
  const secondaryWorkY=1.12+.36*lift+.04*Math.sin(seconds+Math.PI)*lift;
  // Retain a little crossing velocity instead of stopping dead at the centre.
  // Hermite arrival and the small return share the same velocity at 42t.
  const approach=clamp((tick-10)/32),crossingSpeed=.005625;
  const recoilU=clamp((tick-42)/18);
  const overshoot=18*crossingSpeed*recoilU*(1-recoilU)*(1-recoilU);
  const mainX=tick<42?-.40*(1-ease(approach))+32*crossingSpeed*(approach*approach*approach-approach*approach):overshoot;
  const mergeProgress=clamp(1+mainX/.40);
  const inertiaRoll=-Math.PI/180*16*recoilU*recoilU*(1-recoilU)*(1-recoilU);
  const legacyY=mainWorkY*(1-floatSettle)+1.28*floatSettle;
  // Cross into a continuous small hover while crystallisation is still active.
  // The same envelope then removes both its amplitude and the luminous imprint.
  const hoverBlend=ease((tick-42)/16),glowEnvelope=1-ease((tick-74)/22);
  // Integrate a smooth speed brake: 1 -> 0 with zero endpoint acceleration.
  // Stretch the last bob before the light fades, avoiding a quick final dip.
  const hoverBrakeStart=68,hoverBrakeDuration=28;
  const hoverU=clamp((tick-hoverBrakeStart)/hoverBrakeDuration);
  const hoverClock=tick<=hoverBrakeStart?tick:hoverBrakeStart+hoverBrakeDuration*(hoverU-hoverU*hoverU*hoverU+.5*hoverU*hoverU*hoverU*hoverU);
  const hoverSpeed=1-ease(hoverU);
  const hoverPhase=(hoverClock-42)*Math.PI*2/30;
  const hoverY=1.28+glowEnvelope*(.050+.028*Math.sin(hoverPhase));
  const mainY=legacyY*(1-hoverBlend)+hoverY*hoverBlend;
  // Angular momentum overlaps the end of the Bezier turn: pass the upright
  // pose, counter-swing, and let the smaller waves die away without a pause.
  const turnAge=Math.max(0,tick-26);
  const turnGate=ease(turnAge/6)*(1-ease((tick-64)/8));
  const angularRecoil=turnGate*Math.exp(-turnAge/25)*Math.sin(turnAge*Math.PI/12);
  // A slower, phase-offset rock remains during the luminous hover. Its
  // amplitude shares the same final envelope as the light and vertical bob.
  const swayEnvelope=ease((tick-54)/12)*glowEnvelope;
  const swayPhase=(tick-42)*Math.PI*2/40+.5,rad=Math.PI/180;
  // Broaden only the later side-to-side rock; retain the pitch and bob clocks.
  const sideSwayPeriod=52,sideSwayPhase=(tick-42)*Math.PI*2/sideSwayPeriod+.5;
  const angularPitch=rad*(10*angularRecoil+4.2*swayEnvelope*Math.sin(swayPhase+.9));
  const angularYaw=rad*(1.8*angularRecoil+1.4*swayEnvelope*Math.sin(sideSwayPhase-.35));
  const angularRoll=inertiaRoll+rad*(6.5*angularRecoil+3.1*swayEnvelope*Math.sin(sideSwayPhase));
  const secondaryHomeY=secondaryWorkY*(1-secondarySettle)+1.12*secondarySettle;
  return {
   mainX,mainY,
   secondaryX:-mainX,
   secondaryY:secondaryHomeY*(1-mergeProgress)+mainY*mergeProgress,
   mergeProgress,mergeStarts:10,mergeEnds:42,inertiaEnds:60,inertiaOffset:overshoot,inertiaRoll,
   mainAngle:spinTicks*Math.PI/36,
   secondaryAngle:spinTicks*Math.PI/36,
   orientationSettle,orientationCurveT,orientationStarts:12,orientationEnds:34,
   settle:mainSettle,mainSettle,secondarySettle,returnStart,
   floatClock,floatSettle,floatSpeed:tick<=58?1:(38-32*floatU-6*floatU*floatU)/38,
   hoverPhase,hoverClock,hoverSpeed,hoverAmplitude:.028*glowEnvelope,hoverEnvelope:glowEnvelope,
   angularPitch,angularYaw,angularRoll,angularRecoil,swayEnvelope,swayPhase,sideSwayPhase,sideSwayPeriod,
   imprintAlpha:glowEnvelope,
   settled:tick>=96,secondarySettled:tick>=96,
   revealStarts:74,revealEnds:96,mainReturnEnds:96,secondaryReturnEnds:96,
   nativeAngularSpeedDegreesPerSecond:100
  };
 }
 // Each mote first drifts freely, then curls toward a different point on the
 // main item's real silhouette. Arrival precedes its existing fade-out.
 function absorbDust(d,source,target,time){
  const age=Number(time)-source.birth,p=clamp(age/source.lifetime);
  const pull=ease((p-.08)/(absorptionArrival-.08)),arc=Math.sin(Math.PI*pull)*(1-pull);
  return {...d,
   x:d.x+(target.x-d.x)*pull+Math.cos(source.angle)*.024*arc,
   y:d.y+(target.y-d.y)*pull+source.bendY*.034*arc,
   z:d.z+(target.z-d.z)*pull+source.swirl*.020*arc,
   absorption:pull};
 }
 window.LuminousMotion={sample,absorbDust,absorptionArrival,duration,timeScale,durationOptions,defaultSeconds,revision:'6.8s reveal with a smooth vertical phase brake and slower side hover rock'};
})();
