import argparse
import os
import re
import stat
import sys
import tempfile
from pathlib import Path


TOKEN_FILE = Path('/home/software/QAgent/token.key')


def validate_token(token):
    if not re.fullmatch(r'[!-~]{1,512}', token or ''):
        raise ValueError('Provide the non-empty single-line token from the manager')


def get_token():
    flags = os.O_RDONLY | getattr(os, 'O_NOFOLLOW', 0)
    descriptor = os.open(TOKEN_FILE, flags)
    with os.fdopen(descriptor, 'r', encoding='ascii') as token_file:
        info = os.fstat(token_file.fileno())
        if not stat.S_ISREG(info.st_mode):
            raise ValueError('Invalid token file')
        if os.name == 'posix' and (stat.S_IMODE(info.st_mode) != 0o600 or info.st_uid != 0):
            raise ValueError('Token file must be root owned with mode 0600')
        token = token_file.read(514).strip()
    validate_token(token)
    return token


def write_token(token, replace=True):
    if os.name == 'posix' and os.geteuid() != 0:
        raise PermissionError('Token provisioning requires root')
    validate_token(token)
    if TOKEN_FILE.is_symlink():
        raise ValueError('Refusing a symbolic link token file')
    TOKEN_FILE.parent.mkdir(parents=True, exist_ok=True)
    if not replace and TOKEN_FILE.exists():
        os.chmod(TOKEN_FILE, 0o600)
        get_token()
        return False
    descriptor, temporary_path = tempfile.mkstemp(prefix='.token-', dir=TOKEN_FILE.parent)
    try:
        with os.fdopen(descriptor, 'w', encoding='ascii') as token_file:
            os.chmod(temporary_path, 0o600)
            token_file.write(token + '\n')
            token_file.flush()
            os.fsync(token_file.fileno())
        os.replace(temporary_path, TOKEN_FILE)
        if os.name == 'posix':
            os.chown(TOKEN_FILE, 0, 0)
    finally:
        if os.path.exists(temporary_path):
            os.unlink(temporary_path)
    return True


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Save or validate the manager-supplied QAgent token')
    parser.add_argument('action', choices=('initialize', 'set'))
    args = parser.parse_args()
    if args.action == 'set':
        write_token(sys.stdin.readline().strip())
        print(f'Manager token saved to {TOKEN_FILE}.')
    else:
        get_token()
        print(f'Manager token preserved at {TOKEN_FILE}.')
