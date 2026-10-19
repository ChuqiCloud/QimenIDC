"""
@Author: 星禾
@Description: 仅从自有镜像站获取固定哈希的离线 wheel 包，停服前验证全部依赖。
@DateTime: 2026/10/19 19:32
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import subprocess
import sys
import tarfile
import tempfile
import urllib.request

AGENT_ROOT = Path(__file__).resolve().parents[1]
MIRROR_URL = 'https://mirror.chuqiyun.com/software/QAgent/dependencies/'
CACHE_ROOT = Path('/home/software/.qagent-dependencies')
PIP_ENTRY = ('import runpy,sys; sys.path.insert(0,sys.argv.pop(1)); '
             'runpy.run_module("pip",run_name="__main__")')


def sha256(path):
    result = hashlib.sha256()
    with path.open('rb') as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            result.update(chunk)
    return result.hexdigest()


def select_bundle(manifest):
    if sys.version_info[:2] != (3, 10) or platform.system() != 'Linux':
        raise RuntimeError('Offline dependencies require Python 3.10 on Linux')
    architecture = platform.machine().lower()
    if architecture not in manifest['bundles']:
        raise RuntimeError(f'Unsupported offline dependency architecture: {architecture}; use x86_64 or aarch64')
    bundle = manifest['bundles'][architecture]
    if manifest['python'] != '3.10' or not re.fullmatch(r'[a-zA-Z0-9_.-]+\.tar\.gz', bundle['file']):
        raise RuntimeError('Invalid dependency manifest')
    if not re.fullmatch(r'[0-9a-f]{64}', bundle['sha256']):
        raise RuntimeError('Invalid dependency SHA-256')
    return bundle


def download(url, destination):
    with urllib.request.urlopen(url, timeout=60) as response, destination.open('xb') as output:
        if not response.geturl().startswith(MIRROR_URL):
            raise RuntimeError('Dependency download redirected outside the configured mirror')
        size = 0
        for chunk in iter(lambda: response.read(1024 * 1024), b''):
            size += len(chunk)
            if size > 256 * 1024 * 1024:
                raise RuntimeError('Dependency bundle is unexpectedly large')
            output.write(chunk)


def unpack(archive_path, destination):
    # 每次核验缓存内容；禁止链接、目录穿越及任意下载 URL 写入锁文件。
    destination.mkdir(mode=0o700, exist_ok=True)
    if destination.is_symlink():
        raise RuntimeError('Refusing symlink dependency cache')
    names = set()
    with tarfile.open(archive_path, 'r:gz') as archive:
        for member in archive.getmembers():
            name = member.name
            if (not member.isfile() or name != Path(name).name or name in names
                    or not re.fullmatch(r'[a-zA-Z0-9_.+-]+\.whl|requirements\.lock', name)):
                raise RuntimeError(f'Invalid dependency archive member: {name}')
            names.add(name)
            data = archive.extractfile(member).read()
            target = destination / name
            if target.is_symlink():
                raise RuntimeError('Refusing symlink dependency file')
            if target.exists():
                if target.read_bytes() != data:
                    raise RuntimeError(f'Dependency cache differs from verified archive: {name}')
            else:
                with target.open('xb') as output:
                    output.write(data)
    if names != {path.name for path in destination.iterdir()} or 'requirements.lock' not in names:
        raise RuntimeError('Incomplete or unexpected dependency cache contents')
    for line in (destination / 'requirements.lock').read_text(encoding='utf-8').splitlines():
        if not re.fullmatch(r'[a-zA-Z0-9_.-]+==[a-zA-Z0-9_.+!-]+ --hash=sha256:[0-9a-f]{64}', line):
            raise RuntimeError('Dependency lock must contain only pinned packages with SHA-256 hashes')
    return destination


def pip_install(wheels, dry_run=False):
    bootstrap = list(wheels.glob('pip-*-py3-none-any.whl'))
    if len(bootstrap) != 1:
        raise RuntimeError('Offline bundle must contain exactly one pip bootstrap wheel')
    command = [sys.executable, '-I', '-c', PIP_ENTRY, str(bootstrap[0]),
               '--isolated', '--disable-pip-version-check', '--no-cache-dir', 'install',
               '--no-index', '--only-binary=:all:', '--find-links', str(wheels),
               '--require-hashes', '-r', str(wheels / 'requirements.lock')]
    if dry_run:
        command.extend(['--dry-run', '--ignore-installed'])
    subprocess.run(command, check=True)


def prepare(cache_root=CACHE_ROOT):
    manifest = json.loads((AGENT_ROOT / 'dependencies.json').read_text(encoding='utf-8'))
    bundle = select_bundle(manifest)
    if cache_root.is_symlink():
        raise RuntimeError('Refusing symlink dependency cache root')
    cache_root.mkdir(parents=True, mode=0o700, exist_ok=True)
    archive = cache_root / bundle['file']
    if archive.is_symlink():
        raise RuntimeError('Refusing symlink dependency archive')
    if not archive.exists():
        with tempfile.TemporaryDirectory(prefix='.download-', dir=cache_root) as temporary:
            part = Path(temporary) / 'bundle.tar.gz'
            print('Downloading offline dependencies from ' + MIRROR_URL + bundle['file'], flush=True)
            download(MIRROR_URL + bundle['file'], part)
            if sha256(part) != bundle['sha256']:
                raise RuntimeError('Dependency bundle SHA-256 mismatch; services were not changed')
            os.replace(part, archive)
    if sha256(archive) != bundle['sha256']:
        raise RuntimeError(f'Dependency cache SHA-256 mismatch: {archive}')
    wheels = unpack(archive, cache_root / bundle['sha256'])
    pip_install(wheels, dry_run=True)
    return wheels


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('prepare', 'install'))
    parser.add_argument('--cache-dir', type=Path, default=CACHE_ROOT)
    arguments = parser.parse_args()
    wheels = prepare(arguments.cache_dir)
    if arguments.action == 'install':
        pip_install(wheels)


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, RuntimeError, tarfile.TarError, subprocess.CalledProcessError) as error:
        print('QAgent offline dependency setup failed: ' + str(error), file=sys.stderr)
        sys.exit(1)
