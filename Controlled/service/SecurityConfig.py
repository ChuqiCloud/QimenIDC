import os
import shlex
import sys
import tempfile


KEYS = ('QAGENT_MANAGER_IPS', 'QAGENT_MANAGER_PROBES', 'QAGENT_STATIC_MANAGER_IPS',
        'QAGENT_CERT_HOSTS', 'QAGENT_VNC_PROXY_IPS', 'QAGENT_VNC_PROXY_MANAGED', 'QAGENT_BACKEND_PORT',
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


def show_trust(path):
    values = read_config(path)
    print('--->主控 API 来源白名单（nginx）:')
    print(values.get('QAGENT_MANAGER_IPS') or '未配置')
    print('--->实测主控来源:')
    print(values.get('QAGENT_MANAGER_PROBES') or '暂无实测记录')
    print('--->VNC 中转来源白名单（TCP 代理，新连接生效）:')
    managed = values.get('QAGENT_VNC_PROXY_MANAGED') == '1'
    print('管理方式: ' + ('主控自动同步' if managed else '安装配置（尚未由主控托管）'))
    networks = values.get('QAGENT_VNC_PROXY_IPS', '')
    print(networks or ('空名单：拒绝所有 VNC 中转来源' if managed else '未配置 VNC 来源，请检查安全配置及主控同步状态'))


if __name__ == '__main__':
    if sys.argv[1] == '--show-trust':
        show_trust(sys.argv[2])
    elif sys.argv[1] == '--write':
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
