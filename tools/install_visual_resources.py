#!/usr/bin/env python3
"""Install the selected backpack art only into the isolated prototype instance."""
from datetime import datetime, timezone, timedelta
import fcntl
import hashlib
import json
from pathlib import Path
import shutil
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
WORKSPACE = ROOT.parents[1]
INSTANCE = ROOT / 'instance'
ORIGINAL = WORKSPACE / 'clients/cmi-expedition/instance/config/paxi/resourcepacks'
COMPAT = ROOT / 'art/revision25-crystalline-cross/Arcane-Industry-Create-Backpacks-Compat-1.20.1.zip'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def install():
    world_lock = INSTANCE / 'saves/Arcane-Prototype/session.lock'
    if world_lock.exists():
        # The updater runs only after the owned prototype client has stopped.
        with world_lock.open('rb') as stream:
            fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
    selected = json.loads((ROOT / 'art/selected-designs/revision25-final-selection.json').read_text())
    assert all(sha(ROOT / 'art' / name) == value for name, value in selected['files'].items())
    files = [ORIGINAL / 'create_mcGUI_2.0.1.zip', ORIGINAL / 'CreateSophisticatedBackpacks.zip', COMPAT]
    target_dir = INSTANCE / 'resourcepacks'
    target_dir.mkdir(exist_ok=True)
    records = []
    for path in files:
        with ZipFile(path) as archive:
            if archive.testzip() is not None:
                raise RuntimeError(f'Invalid resource archive: {path.name}')
            metadata = json.loads(archive.read('pack.mcmeta').decode('utf-8-sig'))
        target = target_dir / path.name
        if target.is_symlink():
            raise RuntimeError(f'Refusing to replace a linked resource pack: {target}')
        shutil.copy2(path, target)
        assert sha(path) == sha(target)
        records.append({'file': path.name, 'source': str(path), 'sha256': sha(target),
                        'packFormat': metadata['pack']['pack_format']})
    options = INSTANCE / 'options.txt'
    lines = options.read_text().splitlines()
    old = dict(line.split(':', 1) for line in lines if ':' in line)
    packs = json.loads(old.get('resourcePacks', '[]'))
    incompatible = json.loads(old.get('incompatibleResourcePacks', '[]'))
    additions = ['file/' + path.name for path in files]
    packs = [value for value in packs if value not in additions] + additions
    # The original artwork declares format9, but its unchanged 16px assets are
    # the very same ones already used by this user's installed 1.20.1 pack.
    acknowledged = 'file/CreateSophisticatedBackpacks.zip'
    incompatible = [value for value in incompatible if value != acknowledged] + [acknowledged]
    changed = {'resourcePacks': json.dumps(packs, ensure_ascii=False, separators=(',', ':')),
               'incompatibleResourcePacks': json.dumps(incompatible, ensure_ascii=False, separators=(',', ':'))}
    remaining = [line for line in lines if line.partition(':')[0] not in changed]
    options.write_text('\n'.join(remaining + [key + ':' + value for key, value in changed.items()]) + '\n')
    receipt = {'installedAt': datetime.now(timezone(timedelta(hours=8))).isoformat(),
               'instance': str(INSTANCE), 'packs': records, 'resourcePacks': packs,
               'defaultUpgrade': 'Baked into the updated addon JAR from revision26.',
               'switching': 'Disable CreateSophisticatedBackpacks and its Arcane companion to inspect default art.',
               'productionInstanceModified': False}
    (ROOT / 'run/visual-resource-install.json').write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + '\n')
    return receipt


if __name__ == '__main__':
    print(json.dumps(install(), ensure_ascii=False, indent=2))
