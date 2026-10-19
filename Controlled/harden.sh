#!/bin/bash

set -euo pipefail
umask 077
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
source "${SCRIPT_DIR}/security.sh"

# 配置只保留主控下发的 token；需要更换时通过 --token-stdin 输入。
clear_vnc=0
token_stdin=0
prepare_config=0
manager_ips="${QAGENT_MANAGER_IPS:-}"
cert_hosts="${QAGENT_CERT_HOSTS:-}"
vnc_proxy_ips="${QAGENT_VNC_PROXY_IPS:-}"
backend_port="${QAGENT_BACKEND_PORT:-}"
manager_endpoint="${QIMENIDC_MANAGER_ENDPOINT:-}"
cluster_id="${QIMENIDC_CLUSTER_ID:-}"
while [ "$#" -gt 0 ]; do
    case "$1" in
        --configure) shift ;;
        --prepare-config) prepare_config=1; shift ;;
        --clear-vnc) clear_vnc=1; shift ;;
        --token-stdin) token_stdin=1; shift ;;
        --manager-ips) manager_ips="${2:?provide manager source IP/CIDRs}"; shift 2 ;;
        --cert-hosts) cert_hosts="${2:?provide node IP/DNS identities used by the manager}"; shift 2 ;;
        --vnc-proxy-ips) vnc_proxy_ips="${2:?provide noVNC controller source IP/CIDRs}"; shift 2 ;;
        --backend-port) backend_port="${2:?provide internal backend port}"; shift 2 ;;
        --manager-endpoint) manager_endpoint="${2:?provide manager host:port used for source probing}"; shift 2 ;;
        --cluster-id) cluster_id="${2:?provide cluster id shared by every manager in this cluster}"; shift 2 ;;
        --help)
            echo "Usage: bash harden.sh --manager-ips 'IP/CIDR ...' --cert-hosts 'node-ip-or-dns ...' --vnc-proxy-ips 'IP/CIDR ...' [--backend-port 17600] [--manager-endpoint host:port] [--cluster-id id] [--configure|--token-stdin|--prepare-config] [--clear-vnc]"
            echo "Default: preserve the manager token and noVNC sessions. --token-stdin installs the manager-supplied token."
            echo "--prepare-config only validates and saves settings; it does not stop services or change certificates."
            exit 0 ;;
        *) security_fail "unknown option: $1"; exit 1 ;;
    esac
done

security_require_root
command -v /usr/bin/python3.10 >/dev/null || security_fail "install Python 3.10 first"
# --token-stdin 的标准输入包含密钥，不可作为缺失配置的交互输入。
if [ "${token_stdin}" = 1 ]; then
    security_resolve_config "${manager_ips}" "${cert_hosts}" "${vnc_proxy_ips}" "${backend_port}" "${manager_endpoint}" "${cluster_id}" < /dev/null
else
    security_resolve_config "${manager_ips}" "${cert_hosts}" "${vnc_proxy_ips}" "${backend_port}" "${manager_endpoint}" "${cluster_id}"
fi
if [ "${prepare_config}" = 1 ]; then
    [ "${token_stdin}" = 0 ] && [ "${clear_vnc}" = 0 ] || security_fail "--prepare-config cannot change tokens or VNC sessions"
    security_save_config
    echo "QAgent security settings are ready; services and certificates have not been changed."
    exit 0
fi
[ -f "${QAGENT_HOME}/service/VncTokenPlugin.py" ] || security_fail "deploy the updated QAgent code before hardening"
[ -f "${NOVNC_HOME}/utils/websockify/run" ] || security_fail "install noVNC/websockify before hardening"
security_install_dependencies
security_init_directory
security_ensure_novnc_user
[ ! -L /home/software ] || security_fail "refusing symlink: /home/software"
install -d -o root -g root -m 755 /home/software
chown root:root /home/software
chmod 755 /home/software

for unit in qagent.service noVNC.service; do
    if systemctl cat "${unit}" >/dev/null 2>&1; then
        systemctl stop "${unit}"
    fi
done
trap 'systemctl stop qagent.service noVNC.service >/dev/null 2>&1 || true; echo "Security remediation failed; QAgent and noVNC stay stopped. Fix the error and rerun harden.sh." >&2' ERR

security_prepare_vnc_files
security_prepare_novnc_certificate
security_ensure_certificate "${SECURITY_DIR}/qagent" qimenidc-qagent root "${QAGENT_CERT_HOSTS}"
security_write_nginx_config
security_save_config

if [ "${token_stdin}" = 1 ]; then
    /usr/bin/python3.10 "${QAGENT_HOME}/service/Authentication.py" set
else
    /usr/bin/python3.10 "${QAGENT_HOME}/service/Authentication.py" initialize
fi
if [ "${clear_vnc}" = 1 ]; then
    security_clear_vnc_tokens
fi
# 低权 noVNC 只能读取应用代码，不能修改 root 执行的脚本。
chown -R root:root "${QAGENT_HOME}" "${NOVNC_HOME}"
chmod -R go-w "${QAGENT_HOME}" "${NOVNC_HOME}"
find "${QAGENT_HOME}" -type d -exec chmod 755 {} +
# noVNC 需要导入 QAgent 的过期 token 插件，源文件只读开放，密钥单独保持 600。
find "${QAGENT_HOME}" -type f -name '*.py' -exec chmod 644 {} +
chmod -R a+rX "${NOVNC_HOME}"
chown root:root "${QAGENT_HOME}/token.key"
chmod 600 "${QAGENT_HOME}/token.key"
security_write_qagent_unit
security_write_novnc_unit
systemctl daemon-reload
systemctl enable qagent.service noVNC.service nginx.service
systemctl restart nginx.service
systemctl start qagent.service noVNC.service
for unit in nginx.service qagent.service noVNC.service; do
    systemctl is-active --quiet "${unit}"
done
trap - ERR
echo "QAgent TLS whitelist enabled on port ${QAGENT_TLS_PORT}; backend is 127.0.0.1:${QAGENT_BACKEND_PORT}."
echo "noVNC runs as ${VNC_TOKEN_USER}; tokens and metadata are mode 600."
echo "Register ${SECURITY_DIR}/qagent/qimenidc-qagent.crt as this node's certificate in the manager."
if [ "${token_stdin}" = 1 ]; then
    echo "The manager-supplied QAgent token was installed."
fi
