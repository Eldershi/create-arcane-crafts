#!/usr/bin/env python3
"""Boot only the disposable flat-world prototype server using installed Forge libraries."""
from pathlib import Path
import argparse, json, shutil, subprocess

HERE = Path(__file__).resolve().parents[1]
ROOT = HERE.parents[1]
SERVER = HERE / 'testing/server'
SOURCE = ROOT / 'clients/cmi-expedition/evidence/gameplay-batch-20261019/test-server'
JAVA = ROOT / 'clients/cmi-expedition/runtime/temurin17/jdk-17.0.20.1+1-jre/Contents/Home/bin/java'

def prepare():
    SERVER.mkdir(parents=True, exist_ok=True)
    libraries = SERVER / 'libraries'
    if not libraries.exists(): libraries.symlink_to(SOURCE / 'libraries', target_is_directory=True)
    shutil.copy2(SOURCE / 'eula.txt', SERVER / 'eula.txt')
    mods=SERVER/'mods';mods.mkdir(exist_ok=True)
    desired={p.name:p for p in (HERE/'instance/mods').glob('*.jar')}
    for old in mods.glob('*.jar'):
        if old.name not in desired:old.unlink()
    for name,p in desired.items():shutil.copy2(p,mods/name)
    defaults=SERVER/'defaultconfigs';defaults.mkdir(exist_ok=True)
    for p in (HERE/'instance/defaultconfigs').glob('*'):shutil.copy2(p,defaults/p.name)
    settings={'layers':[{'block':'minecraft:bedrock','height':1},{'block':'minecraft:dirt','height':62},{'block':'minecraft:grass_block','height':1}], 'biome':'minecraft:plains','features':False,'lakes':False,'structure_overrides':[]}
    properties={'level-name':'Arcane-Prototype','level-type':'minecraft:flat','generator-settings':json.dumps(settings,separators=(',',':')),'generate-structures':'false','level-seed':'20261019','gamemode':'creative','difficulty':'peaceful','spawn-protection':'0','spawn-animals':'false','spawn-monsters':'false','spawn-npcs':'false','online-mode':'false','server-ip':'127.0.0.1','server-port':'25576','view-distance':'6','simulation-distance':'3','enable-command-block':'false','allow-flight':'true','max-players':'3','motd':'Arcane Industry isolated prototype','enable-rcon':'false'}
    (SERVER/'server.properties').write_text('\n'.join(f'{k}={v}' for k,v in properties.items())+'\n')
    args=SOURCE/'libraries/net/minecraftforge/forge/1.20.1-47.4.10/unix_args.txt'
    return [str(JAVA),'-Xms512M','-Xmx4G','-XX:ActiveProcessorCount=8','-Darcane.prototype=true',f'@{args}','nogui']

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--prepare-only',action='store_true');args=parser.parse_args()
    command=prepare()
    if args.prepare_only:print(SERVER);return
    process = subprocess.Popen(command,cwd=SERVER)
    (HERE/'testing/server-pid.json').write_text(json.dumps({'pid':process.pid,'directory':str(SERVER),'command':command},ensure_ascii=False,indent=2)+'\n')
    raise SystemExit(process.wait())

if __name__=='__main__':main()
