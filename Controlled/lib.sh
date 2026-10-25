#!/bin/bash
set -euo pipefail
umask 077

find /home/software/QAgent -type f -name "*.sh" -exec sed -i 's/\r$//' {} \; 2>/dev/null || true
source /home/software/QAgent/python-runtime.sh
qagent_require_python

# 存量节点先补录并验证安全配置，缺失时不安装依赖、不停止 noVNC。
bash /home/software/QAgent/harden.sh --prepare-config

# 校验、解析并安装自有镜像站依赖，完成前不能停止 noVNC 或替换其目录。
(umask 022; qagent_python /home/software/QAgent/service/DependencyBundle.py install)

apt-get install -y wget curl expect openvswitch-switch ifupdown2 sudo conntrack libsqlite3-dev openssl

# lsof is required by the VNC proxy process to detect its listening port.
function ensure_lsof(){
    if command -v lsof >/dev/null 2>&1; then
        return 0
    fi

    if ! command -v apt-get >/dev/null 2>&1; then
        echo "Error: lsof is required, but apt-get is not available."
        exit 1
    fi

    echo "lsof is not installed. Installing it now..."
    apt-get install -y lsof

    if ! command -v lsof >/dev/null 2>&1; then
        echo "Error: lsof installation failed."
        exit 1
    fi
}

ensure_lsof
rm -rf /usr/share/qemu-server/bootsplash.jpg
mv /home/software/QAgent/images/bootsplash.jpg /usr/share/qemu-server/

# 停止novnc服务
systemctl stop noVNC.service

# 替换前保留旧目录中的证书；已有私钥必须沿用，不能重新生成。
if [ -d /home/software/noVNC ]; then
    bash -euo pipefail -c '
        source /home/software/QAgent/security.sh
        security_init_directory
        security_ensure_novnc_user
        security_prepare_novnc_certificate
    '
    rm -rf /home/software/noVNC
fi

# 赋予指定文件权限
chmod +x /home/software/QAgent/vnc.sh

# 清空 /home/software/websocketd 文件夹
if [ -d /home/software/websocketd ]; then
    find /home/software/websocketd -mindepth 1 -exec rm -rf {} \;
fi

# 判断系统是64位还是32位或者ARM
function check_system(){
    if [ $(uname -m) == "x86_64" ];then
        echo "64"
    elif [ $(uname -m) == "i386" ];then
        echo "32"
    elif [ $(uname -m) == "aarch64" ];then
        echo "arm"
    fi
}

function download_websocketd(){
    cd /home/software/websocketd

    url="https://mirror.chuqiyun.com/software/websocketd/$(check_system)/websocketd"

    wget "$url"
    chmod +x /home/software/websocketd/websocketd
}

# 下载websocketd程序到 /home/software/websocketd 文件夹中
mkdir -p /home/software/websocketd
download_websocketd

# 判断/home/software/目录下是否存在vnc文件，不存在则创建
if [ ! -f /home/software/vnc ]; then
    touch /home/software/vnc
fi

# 下载noVNC程序
function download_noVNC(){
    cd /home/software/noVNC

    url="https://mirror.chuqiyun.com/software/noVNC/noVNC.tar.gz"

    wget "$url"
    tar -zxvf noVNC.tar.gz
    rm -rf noVNC.tar.gz
    # 赋予start_novnc权限
    chmod +x /home/software/noVNC/start_novnc.sh
}

# 安装websockify
function install_websockify(){
    cd /home/software/noVNC/utils/websockify
    qagent_python -c 'import websockify, redis, jwcrypto, requests'
    chmod +x /home/software/noVNC/utils/websockify/run
}

# 配置noVNC开机自启动以及systemd服务
function config_noVNC_service(){
    chmod +x /home/software/QAgent/vnc_https.sh
    bash /home/software/QAgent/vnc_https.sh
}

# 判断/home/software/目录下是否存在noVNC文件夹，不存在则创建
if [ ! -d /home/software/noVNC ]; then
    mkdir -p /home/software/noVNC
    download_noVNC
    install_websockify
fi

# 判断/home/software/QAgent/目录下是否存在port文件，不存在则创建
if [ ! -f /home/software/QAgent/port ]; then
    # 将7600写入port文件中
    echo "7600" > /home/software/QAgent/port
fi

# 安装/更新共用同一套单位文件，保留已保存的白名单及证书。
bash /home/software/QAgent/harden.sh --configure

# 重启pve 服务
systemctl restart pvedaemon.service
