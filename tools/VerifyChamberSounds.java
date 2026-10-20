package dev.expedition.arcane.client;
import java.util.*;
/** Headless audio timeline regression checks; no Minecraft client or sound device is created.
 * javac --release 17 -d build/sound-check src/main/java/dev/expedition/arcane/client/ChamberResearchSoundPlan.java tools/VerifyChamberSounds.java
 * java -cp build/sound-check dev.expedition.arcane.client.VerifyChamberSounds
 */
public final class VerifyChamberSounds {
  static void check(boolean valid, String what) { if (!valid) throw new AssertionError(what); }
  public static void main(String[] args) {
    int assertions=0;
    for(boolean crimson:new boolean[]{false,true}) for(int rarity=0;rarity<5;rarity++) {
      var cursor=new ChamberResearchSoundPlan.Cursor(crimson);
      List<ChamberResearchSoundPlan.Cue> played=new ArrayList<>();
      for(int server=0;server<=161;server++) played.addAll(cursor.update(server*132.0/161,rarity,true));
      long beacon=played.stream().filter(c->c.event().equals("minecraft:block.beacon.activate")).count();
      long ordinary=played.stream().filter(c->c.event().equals("minecraft:block.enchantment_table.use")).count();
      check(beacon==(rarity>=3?1:0),"beacon rarity "+rarity); assertions++;
      check(ordinary==(rarity<3?1:0),"ordinary rarity "+rarity); assertions++;
      check(played.size()==(rarity==4?21:rarity==3?18:14),"cue count "+rarity+":"+played.size()); assertions++;
      check(cursor.update(132,rarity,true).isEmpty(),"no replay at rest"); assertions++;
      for(var cue:played) {
        check(cue.duration()>0 && cue.gain()>0 && cue.gain()<=1 && cue.pitch()>0 && cue.pitch()<2,"finite positive cue"); assertions++;
        if(cue.event().equals("minecraft:block.beacon.activate")) { check(cue.tick()==76 && cue.pitch()==1,"09 at vortex, no pitch shift"); assertions++; }
      }
      for(int first:new int[]{10,76,90,132}) {
        var late=new ChamberResearchSoundPlan.Cursor(crimson);
        check(late.update(first,rarity,true).isEmpty(),"no first-observation backlog at "+first); assertions++;
      }
      var paused=new ChamberResearchSoundPlan.Cursor(crimson);
      paused.update(0,rarity,true);
      check(paused.update(80,rarity,false).isEmpty(),"pause silent"); assertions++;
      check(paused.update(80,rarity,true).isEmpty(),"resume no stale beacon"); assertions++;
      check(paused.update(20,rarity,true).isEmpty(),"rewind no backlog"); assertions++;
      check(paused.update(Double.NaN,rarity,true).isEmpty(),"invalid no cue"); assertions++;
      var jump=new ChamberResearchSoundPlan.Cursor(crimson);jump.update(0,rarity,true);
      for(var cue:jump.update(100,rarity,true)) {check(cue.tick()>=100-.2/(6.8/112),"skip network backlog"); assertions++;}
    }
    System.out.println("PASS "+assertions+" cue, rarity, replay, late-load, pause, rewind and network-jump checks");
  }
}
