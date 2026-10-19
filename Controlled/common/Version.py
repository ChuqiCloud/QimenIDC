from pathlib import Path


# @Author: 星禾
# @Description: 统一读取发布文件中的程序版本，接口协议版本独立维护
# @DateTime: 2026/10/19 15:47
API_VERSION = '1.0.1'


def read_version(version_file):
    """发布版本文件不可读或为空时明确失败，避免报告虚假的版本。"""
    version_file = Path(version_file)
    try:
        version = version_file.read_text(encoding='utf-8').strip()
    except (OSError, UnicodeError) as error:
        raise RuntimeError(f'QAgent version file is unavailable or unreadable: {version_file}') from error
    if not version:
        raise RuntimeError(f'QAgent version file is empty: {version_file}')
    return version


VERSION = read_version(Path(__file__).resolve().parents[1] / 'version')
