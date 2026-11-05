"""Runs in GitHub Actions on the release-artifacts branch. No signing keys are needed."""
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile

manifest = json.loads(Path('release.json').read_text())
version = manifest['version']
assert re.fullmatch(r'\d+(?:\.\d+){2,3}(?:-[a-zA-Z0-9.]+)?', version), 'Invalid version'
assert re.fullmatch(r'[0-9a-f]{40}', manifest['source_sha']), 'Invalid source SHA'
apk = Path(f'OrcaSlicer-arm64-v{version}.apk')
assert apk.stat().st_size < 100 * 1024 * 1024, 'APK exceeds GitHub file limit'
assert hashlib.sha256(apk.read_bytes()).hexdigest() == manifest['sha256'], 'APK hash mismatch'
with zipfile.ZipFile(apk) as z:
    assert 'lib/arm64-v8a/liborcamicro_engine.so' in z.namelist(), 'Missing ARM64 engine'
    assert 'AndroidManifest.xml' in z.namelist(), 'Invalid APK'
checksums = Path('SHA256SUMS')
checksums.write_text(f'{manifest["sha256"]}  {apk.name}\n')
tag = f'v{version}'
source = subprocess.check_output(['gh', 'api', f'repos/{{owner}}/{{repo}}/commits/{manifest["source_sha"]}'], text=True)
assert json.loads(source)['sha'] == manifest['source_sha']
existing = subprocess.run(['gh', 'release', 'view', tag, '--json', 'tagName'], capture_output=True, text=True)
if existing.returncode == 0:
    raise SystemExit('Release already exists; published APKs are never overwritten')
subprocess.run(['gh', 'release', 'create', tag, str(apk), str(checksums),
                '--latest', '--target', manifest['source_sha'], '--title', f'OrcaSlicer Android {version}',
                '--notes-file', 'release-notes.md'], check=True)
print(f'Published {tag} from {manifest["source_sha"]}')
