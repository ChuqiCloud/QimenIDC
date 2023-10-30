#!/bin/bash

apt-get install -y wget curl expect openvswitch-switch ifupdown2 sudo
pip3.10 install -r /home/software/QAgent/requirements.txt
rm -rf /usr/share/qemu-server/bootsplash.jpg
mv /home/software/QAgent/images/bootsplash.jpg /usr/share/qemu-server/
# 重启pve 服务
systemctl restart pvedaemon.service