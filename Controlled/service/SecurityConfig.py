import os
import shlex
import sys
import tempfile


KEYS = ('QAGENT_MANAGER_IPS', 'QAGENT_MANAGER_PROBES', 'QAGENT_STATIC_MANAGER_IPS',
        'QAGENT_CERT_HOSTS', 'QAGENT_VNC_PROXY_IPS', 'QAGENT_BACKEND_PORT',
        'QIMENIDC_MANAGER_ENDPOINT', 'QIMENIDC_CLUSTER_ID')


def read_config(path):
    values = {}
    try:
        with open(path, encoding='utf-8') as stream:
            for line in stream:
                key, separator, value = line.strip().partition('=')
                if separator and key in KEYS:
                    # 兼容 Shell %q、引号和历史未引用的地址列表，不执行配置内容。
                    values[key] = ' '.join(shlex.split(value, comments=True))
    except FileNotFoundError:
        pass
    return values


def render_config(values):
    return ''.join('%s=%s\n' % (key, shlex.quote(str(value))) for key, value in values.items() if key in KEYS)


if __name__ == '__main__':
    if sys.argv[1] == '--write':
        path = sys.argv[2]
        values = dict(item.split('=', 1) for item in sys.argv[3:])
        descriptor, temporary = tempfile.mkstemp(prefix='.security-', dir=os.path.dirname(path))
        try:
            with os.fdopen(descriptor, 'w', encoding='utf-8') as stream:
                stream.write(render_config(values))
                stream.flush()
                os.fsync(stream.fileno())
            os.chmod(temporary, 0o600)
            os.replace(temporary, path)
        finally:
            if os.path.exists(temporary):
                os.unlink(temporary)
    else:
        for key, value in read_config(sys.argv[1]).items():
            if '\n' in value or '\r' in value:
                raise ValueError('invalid security configuration')
            print('%s=%s' % (key, value))
