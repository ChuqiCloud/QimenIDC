#!/bin/bash

# 安装、更新和存量节点整改共用，避免更新恢复宽松权限。
QAGENT_HOME="/home/software/QAgent"
NOVNC_HOME="/home/software/noVNC"
SECURITY_DIR="/etc/qimenidc"
SECURITY_CONFIG="${SECURITY_DIR}/security.conf"
SECURITY_SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
VNC_TOKEN_FILE="/home/software/vnc"
VNC_TOKEN_USER="qimenvnc"
# 主控出口地址探测目标与集群标识，随 security.conf 一起落盘
MANAGER_ENDPOINT="${QIMENIDC_MANAGER_ENDPOINT:-}"
CLUSTER_ID="${QIMENIDC_CLUSTER_ID:-}"

security_fail() {
    echo "Security setup failed: $*" >&2
    return 1
}

security_require_root() {
    [ "$(id -u)" = 0 ] || security_fail "run this script as root"
}

security_regular_file() {
    [ ! -L "$1" ] && { [ ! -e "$1" ] || [ -f "$1" ]; } ||
        security_fail "refusing symlink or non-regular file: $1"
}

security_install_dependencies() {
    command -v openssl >/dev/null && command -v nginx >/dev/null && return 0
    command -v apt-get >/dev/null || security_fail "openssl and nginx are required"
    apt-get update
    apt-get install -y openssl nginx
}

security_init_directory() {
    [ ! -L "${SECURITY_DIR}" ] || security_fail "refusing symlink: ${SECURITY_DIR}"
    install -d -o root -g root -m 755 "${SECURITY_DIR}"
}

security_load_config() {
    security_regular_file "${SECURITY_CONFIG}" || return 1
    if [ -f "${SECURITY_CONFIG}" ]; then
        [ "$(stat -c '%u:%a' "${SECURITY_CONFIG}")" = '0:600' ] ||
            { security_fail "${SECURITY_CONFIG} must be root-owned mode 600"; return 1; }
        local parsed name value
        parsed=$(/usr/bin/python3.10 "${SECURITY_SCRIPT_DIR}/service/SecurityConfig.py" "${SECURITY_CONFIG}") || return 1
        while IFS='=' read -r name value; do
            case "${name}" in
                QAGENT_MANAGER_IPS|QAGENT_MANAGER_PROBES|QAGENT_STATIC_MANAGER_IPS|QAGENT_CERT_HOSTS|QAGENT_VNC_PROXY_IPS|QAGENT_BACKEND_PORT|QIMENIDC_MANAGER_ENDPOINT|QIMENIDC_CLUSTER_ID)
                    printf -v "${name}" '%s' "${value}" ;;
            esac
        done <<< "${parsed}"
        MANAGER_ENDPOINT="${QIMENIDC_MANAGER_ENDPOINT:-${MANAGER_ENDPOINT}}"
        CLUSTER_ID="${QIMENIDC_CLUSTER_ID:-${CLUSTER_ID}}"
    fi
}

security_require_setting() {
    local name="$1" description="$2"
    if [ -z "${!name:-}" ]; then
        if [ ! -t 0 ]; then
            security_fail "missing ${name}; configure manager IPs, node certificate hosts and VNC proxy IPs with harden.sh, or export QAGENT_MANAGER_IPS, QAGENT_CERT_HOSTS and QAGENT_VNC_PROXY_IPS before updating"
            return 1
        fi
        read -r -p "${description}: " "${name}" || return 1
        [ -n "${!name}" ] || { security_fail "${name} cannot be empty"; return 1; }
    fi
}

# 在替换程序或停止服务前补齐旧节点配置；不推测主控出口或自动放通来源。
security_resolve_config() {
    local manager_ips="$1" cert_hosts="$2" vnc_proxy_ips="$3" backend_port="$4"
    local manager_endpoint="$5" cluster_id="$6" explicit_manager=0
    [ -z "${manager_ips}" ] || explicit_manager=1
    security_load_config || return 1
    QAGENT_MANAGER_IPS="${manager_ips:-${QAGENT_MANAGER_IPS:-}}"
    QAGENT_CERT_HOSTS="${cert_hosts:-${QAGENT_CERT_HOSTS:-}}"
    QAGENT_VNC_PROXY_IPS="${vnc_proxy_ips:-${QAGENT_VNC_PROXY_IPS:-}}"
    [ -n "${QAGENT_MANAGER_IPS}" ] || explicit_manager=1
    security_require_setting QAGENT_MANAGER_IPS 'Manager source IP/CIDRs (space-separated)' || return 1
    security_require_setting QAGENT_CERT_HOSTS 'Node IP/DNS used by manager API calls (certificate SANs)' || return 1
    security_require_setting QAGENT_VNC_PROXY_IPS 'noVNC controller source IP/CIDRs (space-separated)' || return 1
    QAGENT_MANAGER_IPS=$(security_validate_manager_ips "${QAGENT_MANAGER_IPS}") || return 1
    if [ "${explicit_manager}" = 1 ] || [ ! -f "${SECURITY_CONFIG}" ]; then
        QAGENT_STATIC_MANAGER_IPS="${QAGENT_MANAGER_IPS}"
    fi
    QAGENT_CERT_HOSTS=$(security_validate_cert_hosts "${QAGENT_CERT_HOSTS}") || return 1
    QAGENT_VNC_PROXY_IPS=$(security_validate_manager_ips "${QAGENT_VNC_PROXY_IPS}") || return 1
    QAGENT_BACKEND_PORT="${backend_port:-${QAGENT_BACKEND_PORT:-17600}}"
    MANAGER_ENDPOINT="${manager_endpoint:-${MANAGER_ENDPOINT:-}}"
    CLUSTER_ID="${cluster_id:-${CLUSTER_ID:-}}"
    security_validate_manager_endpoint "${MANAGER_ENDPOINT}" || return 1
    security_validate_cluster_id "${CLUSTER_ID}" || return 1
    security_regular_file "${QAGENT_HOME}/port" || return 1
    QAGENT_TLS_PORT=7600
    if [ -f "${QAGENT_HOME}/port" ]; then
        QAGENT_TLS_PORT=$(cat "${QAGENT_HOME}/port")
    fi
    security_validate_port "${QAGENT_TLS_PORT}" || return 1
    security_validate_port "${QAGENT_BACKEND_PORT}" || return 1
    [ "${QAGENT_TLS_PORT}" != "${QAGENT_BACKEND_PORT}" ] || { security_fail "TLS and backend ports must differ"; return 1; }
    [ "${QAGENT_TLS_PORT}" != 6080 ] || { security_fail "QAgent TLS port cannot collide with noVNC port 6080"; return 1; }
}

security_validate_manager_ips() {
    /usr/bin/python3.10 - "$1" <<'PY'
import ipaddress
import sys
try:
    values = sys.argv[1].replace(',', ' ').split()
    if not values:
        raise ValueError('explicit manager source IP/CIDR whitelist is required')
    networks = []
    for value in values:
        network = ipaddress.ip_network(value, strict=True)
        if network.prefixlen == 0:
            raise ValueError('allow-all networks are forbidden')
        if str(network) not in networks:
            networks.append(str(network))
    print(' '.join(networks))
except ValueError as error:
    print('Invalid manager whitelist: ' + str(error), file=sys.stderr)
    sys.exit(1)
PY
}

security_validate_cert_hosts() {
    /usr/bin/python3.10 - "$1" <<'PY'
import ipaddress
import re
import sys
try:
    values = sys.argv[1].replace(',', ' ').split()
    if not values:
        raise ValueError('provide the exact node IP or DNS name used by the manager')
    names = []
    for value in values:
        try:
            name = str(ipaddress.ip_address(value))
        except ValueError:
            name = value.rstrip('.').lower()
            if len(name) > 253 or not re.fullmatch(r'(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)(?:\.(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?))*', name):
                raise ValueError('invalid DNS name: ' + value)
        if name not in names:
            names.append(name)
    print(' '.join(names))
except ValueError as error:
    print('Invalid certificate hostname: ' + str(error), file=sys.stderr)
    sys.exit(1)
PY
}

security_validate_cluster_id() {
    [ -z "$1" ] && return 0
    [[ "$1" =~ ^[a-z0-9][a-z0-9_.-]{2,63}$ ]] ||
        security_fail "invalid cluster id: $1 (3-64 chars of [a-z0-9_.-])"
}

security_validate_manager_endpoint() {
    [ -z "$1" ] && return 0
    /usr/bin/python3.10 - "$1" <<'PY'
import sys
value = sys.argv[1].strip()
host, _, port = value.rpartition(':')
if not host or not port.isdigit() or not 0 < int(port) < 65536:
    print('Invalid manager endpoint: ' + value, file=sys.stderr)
    sys.exit(1)
PY
}

security_validate_port() {
    [[ "$1" =~ ^[0-9]{1,5}$ ]] && [ "$1" -gt 0 ] && [ "$1" -le 65535 ] ||
        security_fail "invalid port: $1"
}

security_save_config() {
    security_init_directory
    # 和 Python 同步接口使用相同的引用格式，不执行历史配置。
    /usr/bin/python3.10 "${SECURITY_SCRIPT_DIR}/service/SecurityConfig.py" --write "${SECURITY_CONFIG}" \
        "QAGENT_MANAGER_IPS=${QAGENT_MANAGER_IPS}" \
        "QAGENT_MANAGER_PROBES=${QAGENT_MANAGER_PROBES:-}" \
        "QAGENT_STATIC_MANAGER_IPS=${QAGENT_STATIC_MANAGER_IPS:-}" \
        "QAGENT_CERT_HOSTS=${QAGENT_CERT_HOSTS}" \
        "QAGENT_VNC_PROXY_IPS=${QAGENT_VNC_PROXY_IPS}" \
        "QAGENT_BACKEND_PORT=${QAGENT_BACKEND_PORT}" \
        "QIMENIDC_MANAGER_ENDPOINT=${MANAGER_ENDPOINT}" \
        "QIMENIDC_CLUSTER_ID=${CLUSTER_ID}"
}

security_ensure_novnc_user() {
    if ! id "${VNC_TOKEN_USER}" >/dev/null 2>&1; then
        useradd --system --user-group --no-create-home --home-dir /nonexistent \
            --shell /usr/sbin/nologin "${VNC_TOKEN_USER}"
    fi
    [ "$(id -u "${VNC_TOKEN_USER}")" != 0 ] || security_fail "noVNC user cannot be root"
    usermod --lock --shell /usr/sbin/nologin "${VNC_TOKEN_USER}"
}

security_prepare_vnc_files() {
    local path
    for path in "${VNC_TOKEN_FILE}" "${VNC_TOKEN_FILE}.expires.json" "${VNC_TOKEN_FILE}.lock"; do
        security_regular_file "${path}"
    done
    if [ ! -f "${VNC_TOKEN_FILE}" ]; then
        install -o "${VNC_TOKEN_USER}" -g "${VNC_TOKEN_USER}" -m 600 /dev/null "${VNC_TOKEN_FILE}"
    fi
    if [ ! -f "${VNC_TOKEN_FILE}.expires.json" ]; then
        printf '{"version":1,"tokens":{}}\n' > "${VNC_TOKEN_FILE}.expires.json"
    fi
    chown "${VNC_TOKEN_USER}:${VNC_TOKEN_USER}" "${VNC_TOKEN_FILE}" "${VNC_TOKEN_FILE}.expires.json"
    chmod 600 "${VNC_TOKEN_FILE}" "${VNC_TOKEN_FILE}.expires.json"
    touch "${VNC_TOKEN_FILE}.lock"
    chown root:root "${VNC_TOKEN_FILE}.lock"
    chmod 600 "${VNC_TOKEN_FILE}.lock"
}

security_clear_vnc_tokens() {
    # 调用前必须停止 QAgent 和 noVNC，撤销全部会话，避免写入竞争。
    : > "${VNC_TOKEN_FILE}"
    printf '{"version":1,"tokens":{}}\n' > "${VNC_TOKEN_FILE}.expires.json"
    security_prepare_vnc_files
}

security_collect_ips() {
    {
        hostname -I 2>/dev/null | tr ' ' '\n'
        echo '127.0.0.1'
        echo '::1'
    } | sed 's/%.*//' | sed '/^$/d' | sort -u
}

security_ensure_certificate() {
    local directory="$1" name="$2" key_owner="$3" cert_hosts="${4:-localhost}" ip_addr ip_index=1 dns_index=2 regenerate=0
    local certificate="${directory}/${name}.crt" key="${directory}/${name}.key"
    security_regular_file "${certificate}"
    security_regular_file "${key}"
    [ ! -L "${directory}" ] || security_fail "refusing symlink: ${directory}"
    install -d -o root -g "${key_owner}" -m 710 "${directory}"
    if [ ! -s "${certificate}" ] && [ ! -s "${key}" ]; then
        regenerate=1
    else
        [ -s "${certificate}" ] && [ -s "${key}" ] ||
            security_fail "certificate/key pair incomplete; restore the pinned pair before retrying"
        openssl x509 -in "${certificate}" -noout -checkend 0 >/dev/null 2>&1 ||
            security_fail "certificate expired or invalid; explicitly replace it and update the manager trust"
        if ! openssl x509 -in "${certificate}" -noout -checkend 86400 >/dev/null 2>&1; then
            printf '%s\n' "Certificate expires within 24 hours; arrange renewal and update manager trust: ${certificate}" >&2
        fi
        for ip_addr in ${cert_hosts}; do
            if [[ "${ip_addr}" == *:* ]] || [[ "${ip_addr}" =~ ^[0-9]+(\.[0-9]+){3}$ ]]; then
                openssl x509 -in "${certificate}" -noout -checkip "${ip_addr}" >/dev/null 2>&1 ||
                    security_fail "certificate does not cover ${ip_addr}; explicitly renew it and update manager trust"
            else
                openssl x509 -in "${certificate}" -noout -checkhost "${ip_addr}" >/dev/null 2>&1 ||
                    security_fail "certificate does not cover ${ip_addr}; explicitly renew it and update manager trust"
            fi
        done
    fi
    if [ "${regenerate}" = 1 ]; then
        local temporary config temporary_key temporary_certificate
        temporary=$(mktemp -d "${directory}/.certificate.XXXXXX")
        config="${temporary}/openssl.cnf"
        temporary_key="${temporary}/${name}.key"
        temporary_certificate="${temporary}/${name}.crt"
        cat > "${config}" <<'EOF'
[req]
default_bits = 3072
prompt = no
default_md = sha256
distinguished_name = dn
x509_extensions = v3_req
[dn]
CN = QimenIDC node
[v3_req]
subjectAltName = @alt_names
[alt_names]
DNS.1 = localhost
EOF
        while read -r ip_addr; do
            printf 'IP.%s = %s\n' "${ip_index}" "${ip_addr}" >> "${config}"
            ip_index=$((ip_index + 1))
        done < <(security_collect_ips)
        for ip_addr in ${cert_hosts}; do
            if [[ "${ip_addr}" == *:* ]] || [[ "${ip_addr}" =~ ^[0-9]+(\.[0-9]+){3}$ ]]; then
                printf 'IP.%s = %s\n' "${ip_index}" "${ip_addr}" >> "${config}"
                ip_index=$((ip_index + 1))
            else
                printf 'DNS.%s = %s\n' "${dns_index}" "${ip_addr}" >> "${config}"
                dns_index=$((dns_index + 1))
            fi
        done
        openssl req -x509 -nodes -days 365 -newkey rsa:3072 \
            -keyout "${temporary_key}" -out "${temporary_certificate}" -config "${config}"
        openssl x509 -in "${temporary_certificate}" -noout -checkend 86400 >/dev/null
        for ip_addr in ${cert_hosts}; do
            if [[ "${ip_addr}" == *:* ]] || [[ "${ip_addr}" =~ ^[0-9]+(\.[0-9]+){3}$ ]]; then
                openssl x509 -in "${temporary_certificate}" -noout -checkip "${ip_addr}" >/dev/null
            else
                openssl x509 -in "${temporary_certificate}" -noout -checkhost "${ip_addr}" >/dev/null
            fi
        done
        install -o "${key_owner}" -g "${key_owner}" -m 600 "${temporary_key}" "${key}"
        install -o root -g root -m 644 "${temporary_certificate}" "${certificate}"
        rm -f "${config}" "${temporary_key}" "${temporary_certificate}"
        rmdir "${temporary}"
    fi
    openssl x509 -in "${certificate}" -noout -checkend 0 ||
        security_fail "certificate expired; replace ${certificate} and ${key}, then rerun"
    chown "${key_owner}:${key_owner}" "${key}"
    chmod 600 "${key}"
    chown root:root "${certificate}"
    chmod 644 "${certificate}"
}

security_prepare_novnc_certificate() {
    local directory="${SECURITY_DIR}/novnc" legacy="${NOVNC_HOME}/certs/qimenidc-vnc"
    # 私钥迁出 noVNC 静态网站目录，禁止通过浏览器下载。
    if [ ! -e "${directory}/qimenidc-vnc.key" ] && [ -f "${legacy}.key" ] && [ -f "${legacy}.crt" ]; then
        security_regular_file "${legacy}.key"
        security_regular_file "${legacy}.crt"
        install -d -o root -g "${VNC_TOKEN_USER}" -m 710 "${directory}"
        install -o "${VNC_TOKEN_USER}" -g "${VNC_TOKEN_USER}" -m 600 "${legacy}.key" "${directory}/qimenidc-vnc.key"
        install -o root -g root -m 644 "${legacy}.crt" "${directory}/qimenidc-vnc.crt"
    fi
    security_ensure_certificate "${directory}" qimenidc-vnc "${VNC_TOKEN_USER}"
    rm -f "${legacy}.key"
}

security_write_novnc_unit() {
    cat > /etc/systemd/system/noVNC.service <<EOF
[Unit]
Description=QimenIDC noVNC HTTPS Service
After=network.target
[Service]
Type=simple
User=${VNC_TOKEN_USER}
Group=${VNC_TOKEN_USER}
WorkingDirectory=${NOVNC_HOME}
Environment=PYTHONPATH=${QAGENT_HOME}
ExecStart=/usr/bin/python3.10 ${NOVNC_HOME}/utils/websockify/run --web ${NOVNC_HOME} --token-plugin service.VncTokenPlugin.ExpiringTokenFile --token-source ${VNC_TOKEN_FILE} --cert ${SECURITY_DIR}/novnc/qimenidc-vnc.crt --key ${SECURITY_DIR}/novnc/qimenidc-vnc.key --ssl-only 6080
Restart=on-failure
UMask=0077
NoNewPrivileges=true
CapabilityBoundingSet=
ProtectSystem=strict
ProtectHome=read-only
PrivateTmp=true
PrivateDevices=true
ProtectKernelTunables=true
ProtectKernelModules=true
ProtectControlGroups=true
RestrictSUIDSGID=true
RestrictAddressFamilies=AF_INET AF_INET6 AF_UNIX
[Install]
WantedBy=multi-user.target
EOF
    chmod 644 /etc/systemd/system/noVNC.service
}

security_write_qagent_unit() {
    cat > /etc/systemd/system/qagent.service <<EOF
[Unit]
Description=QimenIDC Controller (local backend behind TLS IP whitelist)
After=network.target
[Service]
Type=simple
User=root
WorkingDirectory=${QAGENT_HOME}
Environment=QAGENT_BACKEND_PORT=${QAGENT_BACKEND_PORT}
Environment=QIMEN_VNC_TOKEN_USER=${VNC_TOKEN_USER}
Environment="QIMEN_VNC_PROXY_CIDRS=${QAGENT_VNC_PROXY_IPS}"
Environment="QIMENIDC_MANAGER_ENDPOINT=${MANAGER_ENDPOINT}"
Environment="QIMENIDC_CLUSTER_ID=${CLUSTER_ID}"
ExecStart=/usr/bin/python3.10 ${QAGENT_HOME}/main.py
Restart=on-failure
UMask=0077
NoNewPrivileges=true
[Install]
WantedBy=multi-user.target
EOF
    chmod 644 /etc/systemd/system/qagent.service
}

security_allowed_networks() {
    printf '%s %s\n' "${QAGENT_MANAGER_IPS}" "${QAGENT_MANAGER_PROBES:-}" | tr ' ' '\n' | sed '/^$/d' | awk '!seen[$0]++'
}

security_write_nginx_config() {
    local network
    cat > /etc/nginx/conf.d/qimenidc-qagent.conf <<EOF
geo \$realip_remote_addr \$qimenidc_qagent_allowed {
    default 0;
EOF
    for network in $(security_allowed_networks); do
        printf '    %s 1;\n' "${network}" >> /etc/nginx/conf.d/qimenidc-qagent.conf
    done
    cat >> /etc/nginx/conf.d/qimenidc-qagent.conf <<EOF
}

server {
    listen ${QAGENT_TLS_PORT} ssl;
    listen [::]:${QAGENT_TLS_PORT} ssl;
    server_name _;
    ssl_certificate ${SECURITY_DIR}/qagent/qimenidc-qagent.crt;
    ssl_certificate_key ${SECURITY_DIR}/qagent/qimenidc-qagent.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    access_log off;
    # 自救入口仍由 QAgent 校验 Token、密文和请求签名，其他业务继续限制来源。
    location = /net/trust {
        limit_except POST { deny all; }
        client_max_body_size 64k;
        proxy_pass http://127.0.0.1:${QAGENT_BACKEND_PORT};
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$realip_remote_addr;
        proxy_set_header X-Forwarded-For \$realip_remote_addr;
        proxy_set_header X-Forwarded-Proto https;
        proxy_read_timeout 30s;
    }
    location / {
        # ACL 使用原始 TCP 对端；请求头不能改变授权来源。
        if (\$qimenidc_qagent_allowed = 0) { return 403; }
        proxy_pass http://127.0.0.1:${QAGENT_BACKEND_PORT};
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$realip_remote_addr;
        proxy_set_header X-Forwarded-For \$realip_remote_addr;
        proxy_set_header X-Forwarded-Proto https;
        proxy_read_timeout 300s;
    }
}
EOF
    chmod 644 /etc/nginx/conf.d/qimenidc-qagent.conf
    nginx -t
}
