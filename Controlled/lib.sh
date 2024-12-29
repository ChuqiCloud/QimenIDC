#!/bin/bash

apt-get install -y wget curl expect openvswitch-switch ifupdown2 sudo conntrack
pip3.10 install -r /home/software/QAgent/requirements.txt
rm -rf /usr/share/qemu-server/bootsplash.jpg
mv /home/software/QAgent/images/bootsplash.jpg /usr/share/qemu-server/

# 停止novnc服务
systemctl stop noVNC.service

# 删除noVNC文件夹
if [ -d /home/software/noVNC ]; then
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

    url="https://mirrors.leapteam.cn:8899/software/websocketd/$(check_system)/websocketd"

    wget "$url" --no-check-certificate
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

    url="https://mirrors.leapteam.cn:8899/software/noVNC/noVNC.tar.gz"

    wget "$url" --no-check-certificate
    tar -zxvf noVNC.tar.gz
    rm -rf noVNC.tar.gz
    # 赋予start_novnc权限
    chmod +x /home/software/noVNC/start_novnc.sh
}

# 安装websockify
function install_websockify(){
    cd /home/software/noVNC/utils/websockify
    python3.10 setup.py install
    chmod +x /home/software/noVNC/utils/websockify/run
}

# 配置noVNC开机自启动以及systemd服务
function config_noVNC_service(){
    echo "[Unit]
Description=noVNC Service
After=network.target
[Service]
Type=simple
ExecStart=/home/software/noVNC/utils/websockify/run --web /home/software/noVNC --target-config /home/software/vnc 6080
Restart=on-failure
User=root
[Install]
WantedBy=multi-user.target" > /etc/systemd/system/noVNC.service
    systemctl daemon-reload
    systemctl enable noVNC.service
    systemctl start noVNC.service
}

# 判断/home/software/目录下是否存在noVNC文件夹，不存在则创建
if [ ! -d /home/software/noVNC ]; then
    mkdir -p /home/software/noVNC
    download_noVNC
    install_websockify
    config_noVNC_service
fi

# 判断/home/software/QAgent/目录下是否存在port文件，不存在则创建
if [ ! -f /home/software/QAgent/port ]; then
    # 将7600写入port文件中
    echo "7600" > /home/software/QAgent/port
fi

# 重启pve 服务
systemctl restart pvedaemon.service