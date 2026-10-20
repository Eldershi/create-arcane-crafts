#!/usr/bin/env python3
"""Copy our stopped, validated disposable world into our isolated single-player instance."""
from pathlib import Path
from datetime import datetime
import hashlib
import json
import shutil

HERE = Path(__file__).resolve().parents[1]
SOURCE = HERE / 'testing/server/Arcane-Prototype'
TARGET = HERE / 'instance/saves/Arcane-Prototype'

def main():
    factory = json.loads((HERE / 'testing/server/arcane-factory-test-results.json').read_text())
    mana = json.loads((HERE / 'testing/server/arcane-test-results.json').read_text())
    if factory.get('status') != 'PASS' or not mana.get('all_passed'):
        raise RuntimeError('Both actual runtime suites must pass before exporting this test world')
    # Caller stops our server before this tool runs. Preserve any prior export for recovery.
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    if TARGET.exists():
        backup = HERE / 'testing' / ('previous-export-' + datetime.now().strftime('%Y%m%d-%H%M%S'))
        shutil.move(TARGET, backup)
    shutil.copytree(SOURCE, TARGET, ignore=shutil.ignore_patterns('session.lock'))
    receipt = {
        'source': str(SOURCE), 'destination': str(TARGET),
        'exportedAt': datetime.now().astimezone().isoformat(),
        'factoryStatus': factory['status'], 'manaPassed': mana['passed'], 'manaTotal': mana['total'],
        'levelDatSha256': hashlib.sha256((TARGET / 'level.dat').read_bytes()).hexdigest(),
        'regionFiles': len(list(TARGET.rglob('*.mca'))),
    }
    (HERE / 'run/world-export.json').write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(receipt, ensure_ascii=False, indent=2))

if __name__ == '__main__': main()
