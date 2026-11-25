/* Shared whole-item quaternion pose, including detached particles' birth poses.
 * orientationCurveT is the RAW 0..1 progress through the 12..34 tick turn.
 * angularPitch/Yaw/Roll are whole-item angles in RADIANS after the base curve.
 * The base curve settles smoothly while the angular recoil keeps moving.
 */
(()=>{
 'use strict';
 function create(THREE,Motion){
  if(!THREE?.Quaternion||!THREE?.Euler||typeof Motion?.sample!=='function')
   throw new TypeError('LuminousItemPose.create needs THREE and Motion.sample.');
  const initial=Motion.sample(12);
  const start=Number.isFinite(initial.orientationStarts)?initial.orientationStarts:12;
  const end=Number.isFinite(initial.orientationEnds)?initial.orientationEnds:34;
  const duration=end-start;
  if(!(duration>0))throw new RangeError('The orientation window must have positive duration.');
  const yTurn=new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0,1,0),Math.PI);
  const euler=new THREE.Euler(0,0,0,'XYZ'),angularEuler=new THREE.Euler(0,0,0,'XYZ');
  const q0=new THREE.Quaternion(),q1=new THREE.Quaternion(),q2=new THREE.Quaternion(),q3=new THREE.Quaternion();
  const before=new THREE.Quaternion(),after=new THREE.Quaternion(),derivative=new THREE.Quaternion();
  const inverse=new THREE.Quaternion(),delta=new THREE.Quaternion(),angularPose=new THREE.Quaternion();
  const a=new THREE.Quaternion(),b=new THREE.Quaternion(),c=new THREE.Quaternion();
  const d=new THREE.Quaternion(),e=new THREE.Quaternion();
  function native(out,angle){
   euler.set(angle,0,angle,'XYZ');
   return out.setFromEuler(euler).multiply(yTurn).normalize();
  }
  function sameHemisphere(q,reference){
   if(q.dot(reference)<0)q.set(-q.x,-q.y,-q.z,-q.w);
   return q;
  }
  native(q0,Motion.sample(start).mainAngle);
  // Differentiate the normalized native quaternion centrally. Converting its
  // tangent into q0's body frame gives an exponential control point whose
  // cubic endpoint derivative is the original angular velocity.
  const h=.01;
  native(before,Motion.sample(start-h).mainAngle);sameHemisphere(before,q0);
  native(after,Motion.sample(start+h).mainAngle);sameHemisphere(after,q0);
  derivative.set((after.x-before.x)/(2*h),(after.y-before.y)/(2*h),
   (after.z-before.z)/(2*h),(after.w-before.w)/(2*h));
  inverse.copy(q0).invert();delta.copy(inverse).multiply(derivative);
  const tangentLength=Math.hypot(delta.x,delta.y,delta.z);
  if(tangentLength>1e-12){
   const logScale=duration/3,angle=tangentLength*logScale;
   const sine=Math.sin(angle)/tangentLength;
   delta.set(delta.x*sine,delta.y*sine,delta.z*sine,Math.cos(angle));
   q1.copy(q0).multiply(delta).normalize();
  }else q1.copy(q0);
  sameHemisphere(q1,q0);
  function apply(out,motionSample,restQuaternion){
   const progress=Number(motionSample.orientationCurveT);
   if(!Number.isFinite(progress))throw new TypeError('motionSample.orientationCurveT must be finite.');
   const u=Math.max(0,Math.min(1,progress));
   if(u<=0)native(out,motionSample.mainAngle);
   else if(u>=1)out.copy(restQuaternion).normalize();
   else{
    q2.copy(restQuaternion).normalize();sameHemisphere(q2,q1);q3.copy(q2);
    // Spherical de Casteljau: three first-level, two second-level, one result.
    a.copy(q0).slerp(q1,u);b.copy(q1).slerp(q2,u);c.copy(q2).slerp(q3,u);
    d.copy(a).slerp(b,u);e.copy(b).slerp(c,u);
    out.copy(d).slerp(e,u).normalize();
   }
   const pitch=Number(motionSample.angularPitch)||0,yaw=Number(motionSample.angularYaw)||0;
   const localRoll=Number.isFinite(motionSample.angularRoll)?motionSample.angularRoll:(Number(motionSample.inertiaRoll)||0);
   if(pitch!==0||yaw!==0||localRoll!==0){
    angularEuler.set(pitch,yaw,localRoll,'XYZ');
    out.multiply(angularPose.setFromEuler(angularEuler)).normalize();
   }
   return out;
  }
  return {apply};
 }
 window.LuminousItemPose={create,revision:'spherical Bezier with whole-item angular momentum and a continuously fading hover rock'};
})();
