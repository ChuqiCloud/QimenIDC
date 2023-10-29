#!/bin/bash

# 先获取https://gitee.com/chuqicloud/soft-ware/raw/master/Cloud/update.txt中文件下载链接
# 然后下载到/home/software/中
local url=$(curl -s https://gitee.com/chuqicloud/soft-ware/raw/master/Cloud/update.txt)
wget -P /home/software/ $url
# 解压/home/software/中的文件QAgent.tar.gz
tar -zxvf /home/software/QAgent.tar.gz -C /home/software/QAgent/
# 运行/home/software/QAgent中的lib.sh文件
chmod +x /home/software/QAgent/lib.sh
# 执行lib.sh文件
sh /home/software/QAgent/lib.sh
# 重启系统
systemctl restart qagent.service