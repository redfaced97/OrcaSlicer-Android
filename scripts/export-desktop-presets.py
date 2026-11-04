#!/usr/bin/env python3
"""Flatten local Orca user presets for transfer to the phone. Output stays gitignored."""
import argparse
import json
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--config', type=Path, default=Path.home() / '.config/OrcaSlicer')
parser.add_argument('--resources', type=Path, required=True, help='Pinned OrcaSlicer resources directory')
parser.add_argument('--output', type=Path, default=Path(__file__).resolve().parents[1] / 'dist/private-presets')
args = parser.parse_args()
index = {}
user = args.config / 'user/default'
for folder in [args.resources / 'profiles', user]:
    for path in sorted(folder.rglob('*.json')):
        kind = next((part for part in path.relative_to(folder).parts[:-1] if part in ('machine', 'filament', 'process')), None)
        if kind is None:
            continue
        data = json.loads(path.read_text(encoding='utf-8-sig'))
        index[kind, data.get('name', path.stem)] = data

# Network credentials are unnecessary for offline slicing and never leave the desktop.
private_keys = {'printhost_apikey', 'printhost_password', 'printhost_user', 'printhost_cafile',
                'print_host', 'print_host_webui', 'printhost_port', 'flashforge_serial_number'}
bookkeeping = {'inherits', 'instantiation', 'setting_id', 'base_id', 'is_custom_defined'}

def resolve(kind, name, seen=()):
    if (kind, name) in seen:
        raise ValueError('Preset inheritance cycle')
    data = index.get((kind, name))
    if data is None:
        raise ValueError(f'Unresolved {kind} parent: {name}')
    parent = data.get('inherits')
    base = resolve(kind, parent, seen + ((kind, name),)) if parent else {}
    return {**base, **data}

args.output.mkdir(parents=True, exist_ok=True)
for kind in ('machine', 'process', 'filament'):
    for number, path in enumerate(sorted((user / kind).glob('*.json')), 1):
        raw = json.loads(path.read_text())
        data = resolve(kind, raw.get('name', path.stem))
        for key in private_keys | bookkeeping:
            data.pop(key, None)
        data.update(type=kind, name=raw.get('name', path.stem), **{'from': 'User'})
        destination = args.output / f'{kind}-{number}.json'
        destination.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')
        print(f'{kind}: {data["name"]} -> {destination.name}')
print('Private output:', args.output)
