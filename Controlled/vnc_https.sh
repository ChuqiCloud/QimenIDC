#!/bin/bash

set -e

NOVNC_HOME="/home/software/noVNC"
VNC_TOKEN_FILE="/home/software/vnc"
CERT_DIR="${NOVNC_HOME}/certs"
CERT_FILE="${CERT_DIR}/qimenidc-vnc.crt"
KEY_FILE="${CERT_DIR}/qimenidc-vnc.key"
OPENSSL_CONF="${CERT_DIR}/qimenidc-vnc-openssl.cnf"
SERVICE_FILE="/etc/systemd/system/noVNC.service"
NOVNC_PORT="6080"

function ensure_root(){
    if [ "$(id -u)" != "0" ]; then
        echo "Error: You must be root to run this script!"
        exit 1
    fi
}

function ensure_openssl(){
    if command -v openssl >/dev/null 2>&1; then
        return
    fi
    if command -v apt-get >/dev/null 2>&1; then
        apt-get update
        apt-get install -y openssl
        return
    fi
    echo "Error: openssl is required."
    exit 1
}

function collect_ips(){
    {
        hostname -I 2>/dev/null | tr ' ' '\n'
        ip -o -4 addr show scope global 2>/dev/null | awk '{split($4,a,"/"); print a[1]}'
        ip -o -6 addr show scope global 2>/dev/null | awk '{split($4,a,"/"); print a[1]}'
        echo "127.0.0.1"
        echo "::1"
    } | sed 's/%.*//' | sed '/^$/d' | sort -u
}

function write_openssl_conf(){
    local primary_ip="$1"
    local index=1

    cat > "${OPENSSL_CONF}" <<EOF
[req]
default_bits = 2048
prompt = no
default_md = sha256
distinguished_name = dn
x509_extensions = v3_req

[dn]
CN = ${primary_ip}

[v3_req]
subjectAltName = @alt_names

[alt_names]
DNS.1 = localhost
EOF

    while read -r ip_addr; do
        if [ -n "${ip_addr}" ]; then
            echo "IP.${index} = ${ip_addr}" >> "${OPENSSL_CONF}"
            index=$((index + 1))
        fi
    done < <(collect_ips)
}

function generate_ip_certificate(){
    mkdir -p "${CERT_DIR}"
    chmod 700 "${CERT_DIR}"

    local primary_ip
    primary_ip=$(collect_ips | head -n 1)
    if [ -z "${primary_ip}" ]; then
        primary_ip="127.0.0.1"
    fi

    write_openssl_conf "${primary_ip}"
    openssl req -x509 -nodes -days 3650 -newkey rsa:2048 \
        -keyout "${KEY_FILE}" \
        -out "${CERT_FILE}" \
        -config "${OPENSSL_CONF}"

    chmod 600 "${KEY_FILE}"
    chmod 644 "${CERT_FILE}"
}

function configure_novnc_service(){
    mkdir -p "${NOVNC_HOME}"
    touch "${VNC_TOKEN_FILE}"

    cat > "${SERVICE_FILE}" <<EOF
[Unit]
Description=noVNC HTTPS Service
After=network.target

[Service]
Type=simple
ExecStart=${NOVNC_HOME}/utils/websockify/run --web ${NOVNC_HOME} --target-config ${VNC_TOKEN_FILE} --cert ${CERT_FILE} --key ${KEY_FILE} --ssl-only ${NOVNC_PORT}
Restart=on-failure
User=root

[Install]
WantedBy=multi-user.target
EOF

    systemctl daemon-reload
    systemctl enable noVNC.service
    systemctl restart noVNC.service
}

function main(){
    ensure_root
    ensure_openssl
    generate_ip_certificate
    configure_novnc_service
    echo "noVNC HTTPS service has been configured on port ${NOVNC_PORT}."
}

main
