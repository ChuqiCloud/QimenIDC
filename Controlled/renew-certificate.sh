#!/bin/bash

set -euo pipefail
umask 077
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
source "${SCRIPT_DIR}/security.sh"

security_require_root
security_load_config
cert_hosts=$(security_validate_cert_hosts "${QAGENT_CERT_HOSTS:-}")
directory="${SECURITY_DIR}/qagent"
certificate="${directory}/qimenidc-qagent.crt"
key="${directory}/qimenidc-qagent.key"
[ ! -L "${directory}" ] || security_fail "refusing symlink: ${directory}"
security_regular_file "${certificate}"
security_regular_file "${key}"
[ -s "${certificate}" ] && [ -s "${key}" ] ||
    security_fail "certificate/key pair missing; run harden.sh first"
security_regular_file "${directory}/renew.lock"
exec 9> "${directory}/renew.lock"
flock -n 9 || security_fail "another certificate update is running"

apply_action=start
restore_action=stop
if systemctl is-active --quiet nginx.service; then
    apply_action=reload
    restore_action=reload
fi
temporary=$(mktemp -d "${directory}/.renew.XXXXXX")
restore=0
cleanup() {
    local status=$?
    trap - EXIT
    if [ "${restore}" = 1 ]; then
        if ! { mv -f -- "${temporary}/previous.key" "${key}" &&
               mv -f -- "${temporary}/previous.crt" "${certificate}" &&
               nginx -t && systemctl "${restore_action}" nginx.service; }; then
            echo "证书恢复未完成，原证书备份保留在 ${temporary}，请手动恢复。" >&2
            exit 1
        fi
        echo "证书更新失败，已恢复原证书。" >&2
    fi
    rm -rf -- "${temporary}"
    exit "${status}"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM HUP

# 新证书先在临时目录生成并校验，失败不影响正在使用的证书。
security_ensure_certificate "${temporary}" qimenidc-qagent root "${cert_hosts}"
cp -p -- "${key}" "${temporary}/previous.key"
cp -p -- "${certificate}" "${temporary}/previous.crt"
restore=1
mv -f -- "${temporary}/qimenidc-qagent.key" "${key}"
mv -f -- "${temporary}/qimenidc-qagent.crt" "${certificate}"
nginx -t
systemctl "${apply_action}" nginx.service
restore=0

echo "QAgent 证书已更新，请将以下完整 PEM 证书重新保存到主控的节点公钥中："
openssl x509 -in "${certificate}" -outform PEM
