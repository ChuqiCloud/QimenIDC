"""
@Author: 星禾
@Description: 生成 Python 3.10/Linux 双架构离线依赖包及固定校验清单，节点无需连接 PyPI。
@DateTime: 2026/10/19 19:32
"""

import argparse
import gzip
import hashlib
import io
import json
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import urllib.request
import zipfile
from email.parser import BytesParser

ROOT = Path(__file__).resolve().parents[1]
AGENT = ROOT / 'Controlled'
PLATFORMS = {
    'x86_64': ['manylinux2014_x86_64', 'manylinux_2_17_x86_64'],
    'aarch64': ['manylinux2014_aarch64', 'manylinux_2_17_aarch64'],
}


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def pip(*arguments):
    subprocess.run([sys.executable, '-m', 'pip', '--disable-pip-version-check', *arguments], check=True)


def build_websockify(output):
    # 使用镜像站 noVNC 自带的同一份源码（包含 jwcrypto 等依赖），避免换成不同发行包。
    url = 'https://mirror.chuqiyun.com/software/noVNC/noVNC.tar.gz'
    data = urllib.request.urlopen(url, timeout=60).read()
    with tempfile.TemporaryDirectory() as directory:
        source = Path(directory)
        with tarfile.open(fileobj=io.BytesIO(data)) as archive:
            prefix = 'utils/websockify/'
            for member in archive.getmembers():
                if not member.name.startswith(prefix) or not member.isfile():
                    continue
                name = Path(member.name[len(prefix):])
                if name.is_absolute() or '..' in name.parts:
                    raise ValueError('Unsafe noVNC archive path')
                target = source / name
                target.parent.mkdir(parents=True, exist_ok=True)
                content = archive.extractfile(member).read()
                if target.suffix in ('.py', '.txt', '.md'):
                    content = content.replace(b'\r\r\n', b'\n').replace(b'\r\n', b'\n')
                target.write_bytes(content)
        pip('wheel', '--no-deps', '--no-build-isolation', '--wheel-dir', str(output), str(source))
    return {'url': url, 'sha256': sha256(data)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    arguments = parser.parse_args()
    output = arguments.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    universal = output / 'universal'
    universal.mkdir(exist_ok=True)
    source = build_websockify(universal)
    manifest = {'python': '3.10', 'websockify_source': source, 'bundles': {}}
    for architecture, platforms in PLATFORMS.items():
        wheels = output / architecture
        wheels.mkdir(exist_ok=False)
        platform_args = [arg for platform in platforms for arg in ('--platform', platform)]
        pip('download', '--index-url', 'https://pypi.org/simple', '--find-links', str(universal),
            '--only-binary=:all:', '--python-version', '310', '--implementation', 'cp', '--abi', 'cp310',
            *platform_args, '--dest', str(wheels), str(next(universal.glob('websockify-*.whl'))),
            '-r', str(AGENT / 'requirements-offline.txt'))
        files = {}
        requirements = []
        for path in sorted(wheels.glob('*.whl')):
            content = path.read_bytes()
            with zipfile.ZipFile(io.BytesIO(content)) as wheel:
                names = [name for name in wheel.namelist()
                         if name.endswith('.dist-info/METADATA') and name.count('/') == 1]
                assert len(names) == 1
                metadata = BytesParser().parsebytes(wheel.read(names[0]))
                requirements.append(f'{metadata["Name"]}=={metadata["Version"]} --hash=sha256:{sha256(content)}')
            files[path.name] = content
        files['requirements.lock'] = ('\n'.join(sorted(requirements)) + '\n').encode()
        target = output / f'{architecture}.tar.gz'
        with target.open('wb') as stream, gzip.GzipFile(fileobj=stream, mode='wb', mtime=0) as compressed:
            with tarfile.open(fileobj=compressed, mode='w') as archive:
                for name, content in sorted(files.items()):
                    member = tarfile.TarInfo(name)
                    member.size = len(content)
                    member.mode = 0o644
                    archive.addfile(member, io.BytesIO(content))
        digest = sha256(target.read_bytes())
        filename = f'qagent-cp310-manylinux2014-{architecture}-{digest[:16]}.tar.gz'
        target.rename(output / filename)
        manifest['bundles'][architecture] = {
            'file': filename, 'sha256': digest, 'wheels': len(requirements),
        }
        print(json.dumps(manifest['bundles'][architecture]), flush=True)
    (AGENT / 'dependencies.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    (output / 'dependencies.json').write_bytes((AGENT / 'dependencies.json').read_bytes())


if __name__ == '__main__':
    main()
