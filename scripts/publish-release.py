#!/usr/bin/env python3
"""Publish an already tested, signed APK using SSH push and GitHub Actions.

Usage: python3 scripts/publish-release.py VERSION APK SOURCE_SHA NOTES_FILE
The dedicated artifact branch keeps binary APKs out of the source branch. The
workflow publishes this exact APK, preserving the local signing key for upgrades.
"""
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parents[1]
if len(sys.argv) != 5:
    raise SystemExit(__doc__)
version, apk_arg, source_arg, notes_arg = sys.argv[1:]
if not re.fullmatch(r'\d+(?:\.\d+){2,3}(?:-[a-zA-Z0-9.]+)?', version):
    raise SystemExit('Use a version such as 0.6.7.1')
apk, notes = Path(apk_arg).resolve(), Path(notes_arg).resolve()
assert apk.is_file() and notes.is_file()
source_sha = subprocess.check_output(['git', 'rev-parse', f'{source_arg}^{{commit}}'], cwd=root, text=True).strip()
origin = subprocess.check_output(['git', 'remote', 'get-url', 'origin'], cwd=root, text=True).strip()
with tempfile.TemporaryDirectory(prefix='orca-release-') as tmp:
    work = Path(tmp)
    def git(*args, check=True):
        return subprocess.run(['git', *args], cwd=work, check=check)
    git('init', '-q')
    git('remote', 'add', 'origin', origin)
    remote = subprocess.run(['git', 'ls-remote', '--exit-code', 'origin', 'refs/heads/release-artifacts'], cwd=work, capture_output=True, text=True)
    if remote.returncode == 0:
        git('fetch', '--depth=1', 'origin', 'release-artifacts')
        git('checkout', '-q', '-b', 'release-artifacts', 'FETCH_HEAD')
        for path in work.iterdir():
            if path.name != '.git':
                shutil.rmtree(path) if path.is_dir() else path.unlink()
    elif remote.returncode == 2:
        git('checkout', '-q', '--orphan', 'release-artifacts')
    else:
        raise SystemExit('Could not inspect release-artifacts: ' + remote.stderr)
    (work/'.github/workflows').mkdir(parents=True)
    shutil.copyfile(root/'.github/workflows/releases.yml', work/'.github/workflows/releases.yml')
    shutil.copyfile(root/'scripts/release-worker.py', work/'publish.py')
    shutil.copyfile(notes, work/'release-notes.md')
    shutil.copyfile(apk, work/f'OrcaSlicer-arm64-v{version}.apk')
    (work/'release.json').write_text(json.dumps({
        'version':version, 'source_sha':source_sha,
        'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),
    }, indent=2)+'\n')
    (work/'README.md').write_text('# Signed release artifacts\n\nSource and instructions: https://github.com/redfaced97/OrcaSlicer-Android\n\nThe workflow publishes the tested APK without rebuilding or changing its signature.\n')
    git('add', '--all')
    for key in ('user.name', 'user.email'):
        value = subprocess.check_output(['git', 'config', key], cwd=root, text=True).strip()
        git('config', key, value)
    git('commit', '-m', f'Publish tested ARM64 APK {version}')
    git('push', 'origin', 'HEAD:refs/heads/release-artifacts')
print(f'Publication queued: https://github.com/redfaced97/OrcaSlicer-Android/releases/tag/v{version}')
