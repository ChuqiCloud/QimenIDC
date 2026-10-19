"""
@Author: 星禾
@Description: 验证离线依赖的架构选择、完整性与缓存边界，禁止回退到在线安装。
@DateTime: 2026/10/19 19:32
"""

import hashlib
import io
import json
from pathlib import Path
import sys
import tarfile
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from service import DependencyBundle as Bundle


class DependencyBundleTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.cache = self.root / 'cache'
        self.cache.mkdir()
        self.lock = 'demo==1.0 --hash=sha256:' + 'a' * 64 + '\n'

    def archive(self, entries):
        path = self.cache / 'test.tar.gz'
        with tarfile.open(path, 'w:gz') as archive:
            for name, value in entries.items():
                member = tarfile.TarInfo(name)
                member.size = len(value)
                archive.addfile(member, io.BytesIO(value))
        return path

    def test_selects_both_supported_architectures_and_rejects_32bit(self):
        manifest = {'python': '3.10', 'bundles': {
            key: {'file': key + '.tar.gz', 'sha256': 'a' * 64}
            for key in ('x86_64', 'aarch64')}}
        with mock.patch.object(Bundle.sys, 'version_info', (3, 10)), \
                mock.patch.object(Bundle.platform, 'system', return_value='Linux'):
            for architecture in ('x86_64', 'aarch64'):
                with mock.patch.object(Bundle.platform, 'machine', return_value=architecture):
                    self.assertEqual(manifest['bundles'][architecture], Bundle.select_bundle(manifest))
            with mock.patch.object(Bundle.platform, 'machine', return_value='i386'):
                with self.assertRaisesRegex(RuntimeError, 'Unsupported'):
                    Bundle.select_bundle(manifest)

    def test_archive_cannot_write_outside_cache(self):
        path = self.archive({'../escape.whl': b'bad'})
        with self.assertRaisesRegex(RuntimeError, 'Invalid dependency archive member'):
            Bundle.unpack(path, self.cache / 'wheels')
        self.assertFalse((self.cache / 'escape.whl').exists())

    def test_cache_tampering_is_rejected(self):
        path = self.archive({'demo.whl': b'wheel', 'requirements.lock': self.lock.encode()})
        wheels = Bundle.unpack(path, self.cache / 'wheels')
        (wheels / 'demo.whl').write_bytes(b'tampered')
        with self.assertRaisesRegex(RuntimeError, 'differs from verified archive'):
            Bundle.unpack(path, wheels)

    def test_lock_cannot_reference_an_external_index_or_url(self):
        for content in ('--extra-index-url https://pypi.org/simple\n', 'https://example.com/demo.whl\n'):
            with self.subTest(lock=content):
                path = self.archive({'requirements.lock': content.encode()})
                with self.assertRaisesRegex(RuntimeError, 'only pinned packages'):
                    Bundle.unpack(path, self.cache / hashlib.sha256(content.encode()).hexdigest())

    def test_bad_download_never_runs_pip(self):
        manifest = {'file': 'bundle.tar.gz', 'sha256': 'a' * 64}
        (self.root / 'dependencies.json').write_text('{}')
        with mock.patch.object(Bundle, 'AGENT_ROOT', self.root), \
                mock.patch.object(Bundle, 'select_bundle', return_value=manifest), \
                mock.patch.object(Bundle, 'download', side_effect=lambda url, path: path.write_bytes(b'bad')), \
                mock.patch.object(Bundle, 'pip_install') as pip:
            with self.assertRaisesRegex(RuntimeError, 'SHA-256 mismatch'):
                Bundle.prepare(self.cache)
            pip.assert_not_called()
        self.assertFalse((self.cache / 'bundle.tar.gz').exists())

    def test_prepare_reuses_verified_cache_without_network_or_install(self):
        path = self.archive({'demo.whl': b'wheel', 'requirements.lock': self.lock.encode()})
        manifest = {'file': path.name, 'sha256': Bundle.sha256(path)}
        (self.root / 'dependencies.json').write_text(json.dumps({}))
        with mock.patch.object(Bundle, 'AGENT_ROOT', self.root), \
                mock.patch.object(Bundle, 'select_bundle', return_value=manifest), \
                mock.patch.object(Bundle, 'download') as download, \
                mock.patch.object(Bundle, 'pip_install') as pip:
            result = Bundle.prepare(self.cache)
            download.assert_not_called()
            pip.assert_called_once_with(result, dry_run=True)

    def test_pip_ignores_indexes_and_uses_only_hashed_wheels(self):
        (self.cache / 'pip-25.2-py3-none-any.whl').touch()
        with mock.patch.object(Bundle.subprocess, 'run') as run:
            Bundle.pip_install(self.cache, dry_run=True)
            arguments = run.call_args.args[0]
            for option in ('-I', '--isolated', '--no-index', '--only-binary=:all:',
                           '--require-hashes', '--dry-run', '--ignore-installed'):
                self.assertIn(option, arguments)


if __name__ == '__main__':
    unittest.main()
