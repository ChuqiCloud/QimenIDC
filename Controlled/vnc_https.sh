#!/bin/bash

set -euo pipefail
umask 077
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
source "${SCRIPT_DIR}/security.sh"
security_require_root
command -v openssl >/dev/null || { apt-get update; apt-get install -y openssl; }
security_init_directory
security_ensure_novnc_user
security_prepare_vnc_files
security_prepare_novnc_certificate
security_write_novnc_unit
systemctl daemon-reload
systemctl enable noVNC.service
systemctl restart noVNC.service
echo "noVNC HTTPS service runs as ${VNC_TOKEN_USER} on port 6080."
