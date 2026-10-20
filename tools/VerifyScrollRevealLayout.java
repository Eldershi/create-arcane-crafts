import com.google.gson.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
public class VerifyScrollRevealLayout {
 public static void main(String[] args) throws Exception {
  var assets=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject().getAsJsonObject("items");
  var pixel=Class.forName("dev.expedition.arcane.client.ChamberScrollRevealRenderer$Pixel");
  var pc=pixel.getDeclaredConstructors()[0]; pc.setAccessible(true);
  var layout=Class.forName("dev.expedition.arcane.client.ChamberScrollRevealRenderer$Layout");
  var lc=layout.getDeclaredConstructors()[0]; lc.setAccessible(true);
  JsonArray out=new JsonArray();
  for(var role:List.of("primary","secondary")) for(int seed:new int[]{1,41877,-1,0,112358}) {
   var mask=assets.getAsJsonObject(role.equals("primary")?"blank":"focus").getAsJsonArray("mask");
   List<Object> pixels=new ArrayList<>();
   for(int y=0;y<16;y++) for(int x=0;x<16;x++) if(mask.get(y).getAsString().charAt(x)=='1') pixels.add(pc.newInstance((x+.5)/16-.5,.5-(y+.5)/16,y,x));
   var value=lc.newInstance(pixels,role.equals("primary")?.66:.32,seed,role.equals("secondary"));
   JsonObject object=new JsonObject(); object.addProperty("role",role);object.addProperty("seed",seed);
   object.add("layout",new Gson().toJsonTree(value)); out.add(object);
  }
  Files.writeString(Path.of(args[1]),out.toString());System.out.println("Built "+out.size()+" production Layout objects");
 }
}
