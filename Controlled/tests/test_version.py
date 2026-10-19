import asyncio
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from common.Version import API_VERSION, VERSION, read_version
from controller.status import version


# @Author: 星禾
# @Description: 验证发布版本来源唯一且版本接口不依赖主程序或系统服务
# @DateTime: 2026/10/19 15:47
class VersionTest(unittest.TestCase):
    def test_version_endpoint_reads_release_file_without_importing_main(self):
        release_file = Path(__file__).resolve().parents[1] / 'version'
        with mock.patch.dict(sys.modules, {'main': None, 'service.Security': None}):
            result = asyncio.run(version())
        self.assertEqual(200, result['code'])
        self.assertEqual(release_file.read_text(encoding='utf-8').strip(), VERSION)
        self.assertEqual({'version': VERSION, 'api_version': API_VERSION}, result['data'])

    def test_reads_and_trims_release_version(self):
        with tempfile.TemporaryDirectory() as directory:
            release_file = Path(directory) / 'version'
            release_file.write_text(' 1.0.4\n', encoding='utf-8')
            self.assertEqual('1.0.4', read_version(release_file))

    def test_missing_release_file_reports_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(RuntimeError, 'unavailable or unreadable'):
                read_version(Path(directory) / 'missing-version')

    def test_empty_release_file_reports_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            release_file = Path(directory) / 'version'
            release_file.write_text(' \n\t', encoding='utf-8')
            with self.assertRaisesRegex(RuntimeError, 'version file is empty'):
                read_version(release_file)

    def test_invalid_encoding_reports_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            release_file = Path(directory) / 'version'
            release_file.write_bytes(b'\xff\xfe')
            with self.assertRaisesRegex(RuntimeError, 'unavailable or unreadable'):
                read_version(release_file)


if __name__ == '__main__':
    unittest.main()
