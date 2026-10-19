#!/bin/bash

set -euo pipefail
umask 077

UPDATE_INDEX_URL="https://gitee.com/chuqicloud/soft-ware/raw/master/Cloud/update.txt"
SOFTWARE_DIR="/home/software"
QAGENT_DIR="${SOFTWARE_DIR}/QAgent"
PACKAGE_PATH="${SOFTWARE_DIR}/QAgent.tar.gz"
BACKUP_DIR=""
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
STAGING_DIR=""

function fail(){
    echo -e "\033[31m--->QAgent update failed: $1\033[0m" >&2
    exit 1
}

function get_download_url(){
    local content
    local url
    content=$(curl -fsSL "${UPDATE_INDEX_URL}" | tr -d '\r')
    url=$(echo "${content}" | sed -n '/^https\?:\/\//p' | head -n 1)
    if [ -z "${url}" ]; then
        url=$(echo "${content}" | sed -n 's/.*href="\([^"]*https\?:\/\/[^"]*\)".*/\1/p' | head -n 1)
        url=$(echo "${url}" | sed 's/&amp;/\&/g')
    fi
    if [ -z "${url}" ]; then
        fail "cannot get package url from ${UPDATE_INDEX_URL}"
    fi
    echo "${url}"
}

function download_package(){
    local url="$1"
    rm -f "${PACKAGE_PATH}"
    case "${url}" in https://*) ;; *) fail "update package URL must use HTTPS" ;; esac
    wget -O "${PACKAGE_PATH}" "${url}" || fail "download package error"
    if [ ! -s "${PACKAGE_PATH}" ]; then
        fail "downloaded package is empty"
    fi
}

function backup_old_config(){
    BACKUP_DIR=$(mktemp -d "${SOFTWARE_DIR}/QAgent_update_backup.XXXXXX")

    if [ -f "${QAGENT_DIR}/token.key" ]; then
        cp -f "${QAGENT_DIR}/token.key" "${BACKUP_DIR}/token.key"
        chmod 600 "${BACKUP_DIR}/token.key"
    fi
    if [ -f "${QAGENT_DIR}/port" ]; then
        cp -f "${QAGENT_DIR}/port" "${BACKUP_DIR}/port"
    fi
    if [ -f "${QAGENT_DIR}/forward_rules.db" ]; then
        cp -f "${QAGENT_DIR}/forward_rules.db" "${BACKUP_DIR}/forward_rules.db"
    fi
}

function restore_old_config(){
    if [ -f "${BACKUP_DIR}/token.key" ]; then
        install -o root -g root -m 600 "${BACKUP_DIR}/token.key" "${QAGENT_DIR}/token.key"
    fi
    if [ -f "${BACKUP_DIR}/port" ]; then
        cp -f "${BACKUP_DIR}/port" "${QAGENT_DIR}/port"
    fi
    if [ -f "${BACKUP_DIR}/forward_rules.db" ]; then
        cp -f "${BACKUP_DIR}/forward_rules.db" "${QAGENT_DIR}/forward_rules.db"
    fi
}

function normalize_shell_scripts(){
    find "$1" -type f -name "*.sh" -exec sed -i 's/\r$//' {} \;
}

function prepare_security_config(){
    # 在下载依赖、停服和替换代码前确认旧节点具备新的安全参数。
    source "${SCRIPT_DIR}/security.sh"
    security_require_root
    security_resolve_config "${QAGENT_MANAGER_IPS:-}" "${QAGENT_CERT_HOSTS:-}" \
        "${QAGENT_VNC_PROXY_IPS:-}" "${QAGENT_BACKEND_PORT:-}" \
        "${QIMENIDC_MANAGER_ENDPOINT:-}" "${QIMENIDC_CLUSTER_ID:-}"
    security_save_config
    # 子脚本重新读已校验配置，不把加载的动态来源当作显式静态覆盖。
    unset QAGENT_MANAGER_IPS QAGENT_CERT_HOSTS QAGENT_VNC_PROXY_IPS QAGENT_BACKEND_PORT
    unset QAGENT_STATIC_MANAGER_IPS QAGENT_MANAGER_PROBES QIMENIDC_MANAGER_ENDPOINT QIMENIDC_CLUSTER_ID
}

function stage_package(){
    STAGING_DIR=$(mktemp -d "${SOFTWARE_DIR}/.QAgent_update.XXXXXX")
    trap '[ -z "${STAGING_DIR}" ] || rm -rf -- "${STAGING_DIR}"' EXIT
    tar -zxf "${PACKAGE_PATH}" -C "${STAGING_DIR}" || fail "extract package error"
    local required
    for required in main.py lib.sh qa.sh harden.sh security.sh service/SecurityConfig.py; do
        [ -f "${STAGING_DIR}/${required}" ] && [ ! -L "${STAGING_DIR}/${required}" ] || fail "invalid package: missing ${required}"
    done
    normalize_shell_scripts "${STAGING_DIR}"
    # 新包也先读已有配置，失败时原目录、运行服务与证书仍然保留。
    bash "${STAGING_DIR}/harden.sh" --prepare-config || fail "security settings are incomplete; existing QAgent was not replaced"
}

function install_package(){
    backup_old_config
    # 保存完整源码，后续依赖或服务配置失败时仍可人工恢复。
    cp -a "${QAGENT_DIR}" "${BACKUP_DIR}/QAgent"
    systemctl stop qagent.service
    rm -rf "${QAGENT_DIR}"
    mv "${STAGING_DIR}" "${QAGENT_DIR}"
    STAGING_DIR=""
    restore_old_config

    if [ -f "${QAGENT_DIR}/token.key" ]; then
        chown root:root "${QAGENT_DIR}/token.key"
        chmod 600 "${QAGENT_DIR}/token.key"
    fi

    chmod +x "${QAGENT_DIR}/lib.sh"
    bash "${QAGENT_DIR}/lib.sh" || fail "dependency/service setup failed; old QAgent source and data remain in ${BACKUP_DIR} for manual recovery"

    mv "${QAGENT_DIR}/qa.sh" /usr/local/bin/qa
    chmod +x /usr/local/bin/qa

    security_run_service restart qagent.service || fail "qagent.service restart failed; backup kept at ${BACKUP_DIR}"
    local unit
    for unit in noVNC.service nginx.service; do
        security_wait_for_service "${unit}" || fail "${unit} health check failed; backup kept at ${BACKUP_DIR}"
    done
    rm -f "${PACKAGE_PATH}"
    rm -rf "${BACKUP_DIR}"
}

function main(){
    local download_url
    prepare_security_config
    download_url=$(get_download_url)
    download_package "${download_url}"
    stage_package
    install_package
    echo -e "\033[32m--->QAgent update success!\033[0m"
}

main
