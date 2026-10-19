#!/usr/bin/env python3
"""Offline Java 17 build against the exact installed mods; leaves originals untouched."""
from pathlib import Path
import argparse, hashlib, json, os, shutil, sqlite3, subprocess, zipfile

HERE = Path(__file__).resolve().parent
ROOT = Path(os.environ.get('ARCANE_WORKSPACE_ROOT', str(HERE.parents[1]))).expanduser().resolve()
CACHE = Path(os.environ.get('ARCANE_GRADLE_CACHE', str(ROOT / 'design/newsteam-worldgen-test/rtf-build/.gradle-user/caches'))).expanduser()
LIBS = Path(os.environ.get('ARCANE_LAUNCHER_LIBS', str(Path.home() / 'Library/Application Support/Swift Craft Launcher/meta/libraries'))).expanduser()
MODS = Path(os.environ.get('ARCANE_MODS_DIR', str(ROOT / 'clients/cmi-expedition/instance/mods'))).expanduser()
BUILD = HERE / 'build'
MAPPING = CACHE / 'fabric-loom/1.20.1/loom.mappings.1_20_1.layered+hash.40359-v2-forge-1.20.1-47.2.19/mappings-srg-named.srg'
LAUNCHER_DB = Path(os.environ.get('ARCANE_LAUNCHER_DB', str(LIBS.parents[1] / 'data/data.db'))).expanduser()
LAUNCH_PROFILE = os.environ.get('ARCANE_LAUNCH_PROFILE', '机械远征-CMI-迁移测试')
ABI_PINS = {
    'com/mojang/datafixerupper': '6.0.8',
    'com/mojang/authlib': '4.0.43',
    'com/mojang/brigadier': '1.1.8',
    'com/google/guava/guava': '31.1-jre',
    'com/google/code/gson/gson': '2.10',
}

def valid_jar(p):
    try:
        return p.open('rb').read(2) == b'PK'
    except OSError:
        return False

def run(args, log):
    result = subprocess.run(list(map(str, args)), cwd=HERE, capture_output=True, text=True)
    log.write_text(result.stdout + result.stderr)
    if result.returncode:
        print((result.stdout + result.stderr)[-18000:])
        raise SystemExit(result.returncode)

def runtime_support():
    """Use this installed 1.20.1 launch's actual closure, never a multi-version directory scan.

    Only game_versions.data_json is queried; account tables and tokens are not read or stored.
    No command in the metadata is executed by this build.
    """
    with sqlite3.connect(LAUNCHER_DB.as_uri() + '?mode=ro', uri=True) as connection:
        connection.execute('PRAGMA query_only = ON')
        row = connection.execute('SELECT data_json FROM game_versions WHERE game_name = ?',
                                 (LAUNCH_PROFILE,)).fetchone()
    if row is None:
        raise RuntimeError('Missing installed 1.20.1 launch metadata')
    metadata = json.loads(row[0])
    if metadata.get('gameVersion') != '1.20.1' or metadata.get('modVersion') != '47.4.10':
        raise RuntimeError('Compile dependencies require Minecraft 1.20.1 / Forge 47.4.10')
    paths = []
    args = metadata['launchCommand']
    for index, arg in enumerate(args):
        if arg in ('-cp', '--class-path', '-classpath', '-p', '--module-path'):
            paths.extend(Path(path) for path in args[index + 1].split(os.pathsep))
        elif arg.startswith('-DlegacyClassPath='):
            paths.extend(Path(path) for path in arg.partition('=')[2].split(os.pathsep))
    # These are loaded by Forge's game layer rather than the JVM's initial classpath.
    for artifact in ['fmlcore', 'javafmllanguage', 'lowcodelanguage', 'mclanguage']:
        paths.append(LIBS / f'net/minecraftforge/{artifact}/1.20.1-47.4.10/{artifact}-1.20.1-47.4.10.jar')
    # Compile-only annotations; this has no runtime implementation and an exact stable version.
    paths.append(LIBS / 'com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar')
    support = []
    artifacts = {}
    for path in dict.fromkeys(paths):
        if not path.is_relative_to(LIBS) or 'natives-' in path.name:
            continue
        relative = path.relative_to(LIBS)
        if relative.parts[:2] == ('net', 'minecraft') or relative.parts[:3] == ('net', 'minecraftforge', 'forge'):
            continue
        if not valid_jar(path):
            raise RuntimeError(f'Pinned installed compile support JAR missing: {path}')
        artifact, version = '/'.join(relative.parts[:-2]), relative.parts[-2]
        if artifact in artifacts and artifacts[artifact] != version:
            raise RuntimeError(f'Conflicting compile support versions for {artifact}: {artifacts[artifact]} / {version}')
        artifacts[artifact] = version
        support.append(path)
    for artifact, expected in ABI_PINS.items():
        if artifacts.get(artifact) != expected:
            raise RuntimeError(f'Compile ABI pin mismatch: {artifact} must be {expected}, got {artifacts.get(artifact)}')
    (BUILD / 'compile-support-lock.json').write_text(json.dumps({
        'source': 'read-only game_versions.data_json actual launch classpath',
        'minecraft': '1.20.1', 'forge': '47.4.10', 'abiPins': ABI_PINS,
        'libraries': [{'path': str(path), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
                      for path in support],
    }, ensure_ascii=False, indent=2) + '\n')
    return support

def assert_compile_abi(cp):
    checks = {
        'com/mojang/serialization/DataResult.class': '/com/mojang/datafixerupper/6.0.8/',
        'com/mojang/authlib/GameProfile.class': '/com/mojang/authlib/4.0.43/',
        'com/mojang/brigadier/CommandDispatcher.class': '/com/mojang/brigadier/1.1.8/',
    }
    origins = {name: [] for name in checks}
    for path in cp:
        with zipfile.ZipFile(path) as archive:
            for name in checks:
                if name in archive.namelist():
                    origins[name].append(str(path))
    for name, expected in checks.items():
        if len(origins[name]) != 1 or expected not in origins[name][0]:
            raise RuntimeError(f'Compile ABI has an unexpected class provider for {name}: {origins[name]}')
    (BUILD / 'compile-abi-origins.json').write_text(json.dumps(origins, indent=2) + '\n')

def environment():
    m = CACHE / 'modules-2/files-2.1'
    special = next(m.glob('net.md-5/SpecialSource/*/*/*.jar'))
    tool = [special]
    for art in ['asm', 'asm-tree', 'asm-commons', 'asm-util', 'asm-analysis']:
        tool.append(next(m.glob(f'org.ow2.asm/{art}/9.6/*/*.jar')))
    for pat in ['com.google.guava/guava/*/*/*.jar', 'net.sf.jopt-simple/jopt-simple/*/*/*.jar', 'com.opencsv/opencsv/*/*/*.jar']:
        tool.extend([p for p in m.glob(pat) if valid_jar(p)][:1])
    named_mc = next((CACHE / 'fabric-loom/minecraftMaven/net/minecraft/forge-1.20.1-47.2.19-minecraft-merged').glob('*/*.jar'))
    # Minecraft/Forge classes come only from the patched named compile jar. All third-party
    # support must come from this version's closure: a recursive library scan mixes in 1.21 APIs.
    support = runtime_support()
    raw_mc = [LIBS / 'net/minecraft/client/1.20.1-20230612.114412/client-1.20.1-20230612.114412-srg.jar',
              LIBS / 'net/minecraftforge/forge/1.20.1-47.4.10/forge-1.20.1-47.4.10-client.jar',
              LIBS / 'net/minecraftforge/forge/1.20.1-47.4.10/forge-1.20.1-47.4.10-universal.jar']
    prefixes = ['create-1.20.1-', 'create-enchantment-industry-', 'CreateDragonsPlus-', 'irons_spellbooks-',
                'irons_lib-', 'sophisticatedbackpacks-', 'sophisticatedcore-', 'curios-forge-', 'geckolib-forge-',
                'player-animation-lib-forge-', 'ponder-', 'flywheel-forge-', 'jei-']
    deps = [p for p in MODS.glob('*.jar') if any(p.name.startswith(s) for s in prefixes)]
    if len(deps) < 7:
        raise RuntimeError('Missing installed development dependencies')
    nested = BUILD / 'raw-deps'
    nested.mkdir(parents=True, exist_ok=True)
    for dep in list(deps):
        with zipfile.ZipFile(dep) as z:
            for entry in z.namelist():
                if entry.startswith('META-INF/jarjar/') and entry.endswith('.jar'):
                    data = z.read(entry)
                    target = nested / Path(entry).name
                    if not target.exists() or target.read_bytes() != data: target.write_bytes(data)
                    if target not in deps: deps.append(target)
    return tool, named_mc, support, raw_mc, deps

def prepare():
    BUILD.mkdir(parents=True, exist_ok=True)
    (BUILD / 'deps').mkdir(exist_ok=True)
    tool, named_mc, support, raw_mc, deps = environment()
    named = []
    for dep in deps:
        out = BUILD / 'deps' / dep.name
        checksum = hashlib.sha256(dep.read_bytes()).hexdigest()
        stamp = out.with_suffix('.sha256')
        if not out.exists() or not stamp.exists() or stamp.read_text() != checksum:
            cp = tool + raw_mc + deps + support
            run(['java', '-Xmx768M', '-cp', os.pathsep.join(map(str, cp)),
                 'net.md_5.specialsource.SpecialSource', '--quiet', '--live',
                 '--in-jar', dep, '--out-jar', out, '--srg-in', MAPPING], BUILD / f'remap-{dep.stem}.log')
            stamp.write_text(checksum)
        named.append(out)
    cp = [named_mc] + named + support
    assert_compile_abi(cp)
    (BUILD / 'compile-classpath.txt').write_text(os.pathsep.join(map(str, cp)))
    return tool, cp

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--prepare-only', action='store_true')
    args = parser.parse_args()
    tool, cp = prepare()
    if args.prepare_only:
        print(BUILD / 'compile-classpath.txt')
        return
    classes = BUILD / 'classes'
    if classes.exists(): shutil.rmtree(classes)
    classes.mkdir()
    sources = sorted((HERE / 'src/main/java').rglob('*.java'))
    run(['javac', '--release', '17', '-proc:none', '-encoding', 'UTF-8', '-cp', os.pathsep.join(map(str, cp)),
         '-d', classes, *sources], BUILD / 'compile.log')
    abi = subprocess.run(['javap', '-verbose', '-classpath', str(classes),
                          'dev.expedition.arcane.compat.PrinterCompat'], capture_output=True, text=True, check=True)
    (BUILD / 'printer-bytecode-abi.txt').write_text(abi.stdout)
    if any('InterfaceMethodref' in line and 'com/mojang/serialization/DataResult.' in line
           for line in abi.stdout.splitlines()):
        raise RuntimeError('PrinterCompat was compiled against interface DataResult; 1.20.1 requires class Methodref')
    dev = BUILD / 'arcane-industry-0.1.0-dev.jar'
    with zipfile.ZipFile(dev, 'w', zipfile.ZIP_DEFLATED) as z:
        z.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\nMixinConfigs: arcane_industry.mixins.json\n\n')
        for base in [classes, HERE / 'src/main/resources']:
            for p in sorted(base.rglob('*')):
                if p.is_file(): z.write(p, p.relative_to(base).as_posix())
    output = BUILD / 'arcane-industry-0.1.0.jar'
    run(['java', '-Xmx768M', '-cp', os.pathsep.join(map(str, tool + [dev] + cp)),
         'net.md_5.specialsource.SpecialSource', '--quiet', '--live', '--reverse',
         '--in-jar', dev, '--out-jar', output, '--srg-in', MAPPING], BUILD / 'reobfuscate.log')
    print(output)

if __name__ == '__main__': main()
