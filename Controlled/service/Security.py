import base64
import hashlib
import inspect
import json
import os
import re
import threading
import time

from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

from common.CodeEnum import CodeEnum
from common.UnicornException import UnicornException
from service.Authentication import get_token

'''
负载加密协议：
magic(10字节固定前缀+'QIMENIDC-') + iv(12字节) + ciphertext + tag(16字节)，整体Base64
magic 与程序版本无关，避免升级版本后老主控无法解密新被控的响应
'''
MAGIC = b'QIMENIDC-1'
MAGIC_LENGTH = len(MAGIC)
IV_LENGTH = 12
TAG_LENGTH = 16
# 通过配置关闭加密校验时使用的私有magic，与业务请求的magic区分开
PLAIN_MAGIC = b'QIMEN-PLAIN-1.0.'

# 加密负载签名时间戳允许的偏差（秒），超时视为重放
TIMESTAMP_TOLERANCE = 300
# 已使用过的nonce保留时间（秒）
NONCE_TTL = 600


class LoadCodec:
    def __init__(self, token):
        self.token = str(token).strip()
        self.key = hashlib.sha256(self.token.encode('utf-8')).digest()
        self.magic = MAGIC

    def build_aad(self, method, path, timestamp, nonce):
        # 响应可能没有对应的请求参数（如版本协商），此时用空值，两侧保持一致
        return (method.upper() + '|' + path + '|' + ('' if timestamp is None else str(timestamp))
                + '|' + ('' if nonce is None else nonce)).encode('utf-8')

    def encrypt(self, payload, method=None, path=None, timestamp=None, nonce=None):
        iv = os.urandom(IV_LENGTH)
        encryptor = Cipher(algorithms.AES(self.key), modes.GCM(iv)).encryptor()
        if method and path:
            encryptor.authenticate_additional_data(self.build_aad(method, path, timestamp, nonce))
        ciphertext = encryptor.update(payload) + encryptor.finalize()
        return base64.b64encode(self.magic + iv + ciphertext + encryptor.tag).decode('utf-8')

    '''
    解密校验，任何失败均抛出401，避免向调用方泄露失败原因
    '''
    def decrypt(self, encoded, method=None, path=None, timestamp=None, nonce=None):
        try:
            blob = base64.b64decode(encoded, validate=True)
        except Exception:
            return None
        if len(blob) < MAGIC_LENGTH + IV_LENGTH + TAG_LENGTH or blob[:MAGIC_LENGTH] != self.magic:
            return None
        iv = blob[MAGIC_LENGTH:MAGIC_LENGTH + IV_LENGTH]
        ciphertext = blob[MAGIC_LENGTH + IV_LENGTH:len(blob) - TAG_LENGTH]
        tag = blob[len(blob) - TAG_LENGTH:]
        try:
            decryptor = Cipher(algorithms.AES(self.key), modes.GCM(iv, tag)).decryptor()
            if method and path:
                decryptor.authenticate_additional_data(self.build_aad(method, path, timestamp, nonce))
            return decryptor.update(ciphertext) + decryptor.finalize()
        except Exception:
            return None


class NonceCache:
    def __init__(self, ttl=NONCE_TTL):
        self.ttl = ttl
        self.lock = threading.Lock()
        self.items = {}

    def check_and_store(self, nonce, now):
        with self.lock:
            expired = [item for item, expire in self.items.items() if expire <= now]
            for item in expired:
                self.items.pop(item, None)
            if nonce in self.items:
                return False
            self.items[nonce] = now + self.ttl
            return True


_nonce_cache = NonceCache()
_version = None


def get_version():
    global _version
    if _version is None:
        version_file = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'version')
        try:
            with open(version_file, 'r') as f:
                _version = f.read().strip()
        except Exception:
            _version = ''
    return _version


'''
请求负载解密：
开启加密校验时请求体必须为密文，且必须带 timestamp、nonce 头（放在头里避免进访问日志）；
关闭加密校验时（仅演示环境）请求体必须带私有magic，防止直接暴露明文接口。

timestamp 只用于限定密文有效期，nonce 由次数上限约束，
即使抓包重放同一份密文，nonce 已入库也会被拒。
'''
def decode_request(request, body):
    path = request.url.path
    method = request.method
    raw = body.decode('utf-8') if isinstance(body, (bytes, bytearray)) else str(body or '')
    if is_encrypt_enabled():
        timestamp = request.headers.get('X-QimenIDC-Timestamp')
        nonce = request.headers.get('X-QimenIDC-Nonce')
        if not timestamp or not nonce:
            # 缺少防重放参数时不放行加密负载，仅接受私有magic明文（仅演示环境开启）
            if get_config('QIMENIDC_COMPAT_PLAIN', '0') == '1':
                return decode_plain(raw, path)
            unauthorized('authentication failed')
        check_timestamp(timestamp)
        if not _nonce_cache.check_and_store(nonce, int(time.time())):
            unauthorized('duplicated request')
        plain = LoadCodec(get_token()).decrypt(raw, method, path, timestamp, nonce)
        if plain is None:
            unauthorized('authentication failed')
        return json.loads(plain.decode('utf-8'))
    return decode_plain(raw, path)


def decode_plain(raw, path):
    try:
        obj = json.loads(raw)
    except Exception:
        unauthorized('payload not encrypted')
    if not isinstance(obj, dict) or obj.get('_magic') != PLAIN_MAGIC.decode('utf-8'):
        unauthorized('payload not encrypted')
    obj.pop('_magic', None)
    return obj.get('_data') or {}


'''
响应体编码：密文里带上请求的 timestamp、nonce 参与AEAD校验，
主控解不开被篡改的响应，也不会误用别的请求的响应。
'''
def encode_response(payload, request=None):
    body = json.dumps(payload, ensure_ascii=False, default=str).encode('utf-8')
    if not is_encrypt_enabled() or request is None:
        return payload
    timestamp = request.headers.get('X-QimenIDC-Timestamp')
    nonce = request.headers.get('X-QimenIDC-Nonce')
    method = request.method
    path = request.url.path
    if not timestamp or not nonce:
        # 免鉴权接口没有防重放参数，用固定值参与校验，密文仍然无法被替换成别的路径的响应
        timestamp = None
        nonce = None
    return {'_encrypted': LoadCodec(get_token()).encrypt(body, method, path, timestamp, nonce)}


def get_version_of_master(request):
    return request.headers.get('X-QimenIDC-Version', '').strip()


def check_timestamp(timestamp):
    try:
        value = int(timestamp)
    except Exception:
        unauthorized('invalid timestamp')
    if abs(int(time.time()) - value) > TIMESTAMP_TOLERANCE:
        unauthorized('request expired')


def unauthorized(message):
    raise UnicornException(code=CodeEnum.UNAUTHORIZED, msg=message)


def is_encrypt_enabled():
    return get_config('QIMENIDC_ENCRYPT', '1') != '0'


'''
负载字段白名单：
未被请求方法签名声明为参数的字段一律丢弃，并记录日志，
防止客户端伪造额外字段触碰未声明的行为。
'''
def filter_payload(request, payload):
    allowed = get_declared_fields(request)
    if allowed is None:
        return payload
    unknown = [key for key in payload.keys() if key not in allowed]
    if unknown:
        print('[SECURITY] 请求 %s %s 携带未声明字段，已忽略: %s' % (request.method, request.url.path, ','.join(unknown)))
        payload = {key: value for key, value in payload.items() if key in allowed}
    return payload


def get_declared_fields(request):
    endpoint = request.scope.get('endpoint')
    if endpoint is None:
        return None
    fields = set()
    for name, param in get_signature_params(endpoint).items():
        if name == 'self':
            continue
        if param.kind in (param.VAR_KEYWORD, param.VAR_POSITIONAL):
            return None
        fields.add(name)
        annotation = param.annotation
        if isinstance(annotation, type) and hasattr(annotation, 'model_fields'):
            fields.update(annotation.model_fields.keys())
    return fields


def get_signature_params(endpoint):
    if not hasattr(endpoint, '__qimen_signature__'):
        endpoint.__qimen_signature__ = inspect.signature(endpoint).parameters
    return endpoint.__qimen_signature__


def get_config(name, default=''):
    return os.environ.get(name, default)


'''
文件操作范围白名单，按接口能力细分，默认只放行 snippets、网络配置与镜像目录
'''
FILE_POLICIES = {
    'read': ['/var/lib/vz/snippets', '/etc/network'],
    'write': ['/var/lib/vz/snippets'],
    'list': ['/home/images', '/var/lib/vz/snippets', '/etc/network'],
    'delete': ['/home/images'],
    'download': ['/home/images'],
}


def check_file_path(policy, path, filename=''):
    allowed_paths = FILE_POLICIES.get(policy, [])
    if not allowed_paths:
        unauthorized('path not allowed')
    real_path = resolve_path(path)
    if not in_scope(real_path, [resolve_path(item) for item in allowed_paths]):
        print('[SECURITY] 文件操作越权被拒绝: policy=%s path=%s' % (policy, path))
        unauthorized('path not allowed')
    if filename and not is_valid_filename(filename):
        unauthorized('invalid file name')
    return real_path


def resolve_path(path):
    return os.path.realpath(str(path or ''))


def in_scope(real_path, allowed_paths):
    for allowed in allowed_paths:
        if real_path == allowed or real_path.startswith(allowed.rstrip('/') + '/'):
            return True
    return False


def is_valid_filename(filename):
    if filename in ('', '.', '..'):
        return False
    if os.path.basename(filename) != filename:
        return False
    return not re.search(r'[\x00-\x1f]', filename)
