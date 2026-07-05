#!/bin/bash

set -e

UPDATE_INDEX_URL="https://gitee.com/chuqicloud/soft-ware/raw/master/Cloud/update.txt"
SOFTWARE_DIR="/home/software"
QAGENT_DIR="${SOFTWARE_DIR}/QAgent"
PACKAGE_PATH="${SOFTWARE_DIR}/QAgent.tar.gz"
BACKUP_DIR="${SOFTWARE_DIR}/QAgent_update_backup"

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
    wget -O "${PACKAGE_PATH}" "${url}" --no-check-certificate || fail "download package error"
    if [ ! -s "${PACKAGE_PATH}" ]; then
        fail "downloaded package is empty"
    fi
}

function backup_old_config(){
    rm -rf "${BACKUP_DIR}"
    mkdir -p "${BACKUP_DIR}"

    if [ -f "${QAGENT_DIR}/token.key" ]; then
        cp -f "${QAGENT_DIR}/token.key" "${BACKUP_DIR}/token.key"
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
        cp -f "${BACKUP_DIR}/token.key" "${QAGENT_DIR}/token.key"
    fi
    if [ -f "${BACKUP_DIR}/port" ]; then
        cp -f "${BACKUP_DIR}/port" "${QAGENT_DIR}/port"
    fi
    if [ -f "${BACKUP_DIR}/forward_rules.db" ]; then
        cp -f "${BACKUP_DIR}/forward_rules.db" "${QAGENT_DIR}/forward_rules.db"
    fi
}

function normalize_shell_scripts(){
    find "${QAGENT_DIR}" -type f -name "*.sh" -exec sed -i 's/\r$//' {} \;
}

function install_package(){
    backup_old_config
    rm -rf "${QAGENT_DIR}"
    mkdir -p "${QAGENT_DIR}"
    tar -zxvf "${PACKAGE_PATH}" -C "${QAGENT_DIR}/" || fail "extract package error"
    restore_old_config
    normalize_shell_scripts

    chmod +x "${QAGENT_DIR}/lib.sh"
    bash "${QAGENT_DIR}/lib.sh"

    mv "${QAGENT_DIR}/qa.sh" /usr/local/bin/qa
    chmod +x /usr/local/bin/qa

    rm -f "${PACKAGE_PATH}"
    rm -rf "${BACKUP_DIR}"
    systemctl restart qagent.service
}

function main(){
    local download_url
    download_url=$(get_download_url)
    download_package "${download_url}"
    install_package
    echo -e "\033[32m--->QAgent update success!\033[0m"
}

main
