#!/bin/bash

# 先获取 https://gitee.com/chuqicloud/soft-ware/raw/master/Cloud/update.txt 中文件下载链接
# 然后下载到 /home/software/ 中
function get_url(){
    local url=$(curl -s https://gitee.com/chuqicloud/soft-ware/raw/master/Cloud/update.txt)
    # 下载
    wget -P /home/software/ $url --no-check-certificate

}

get_url

# 删除 /home/software/QAgent 文件夹
if [ -d /home/software/QAgent ]; then
    # 删除 /home/software/QAgent 文件夹中除了 token.key和port 文件的所有文件
    #find /home/software/QAgent -mindepth 1 ! \( -name "token.key" -o -name "port" \) -exec rm -rf {} \;
    #find /home/software/QAgent -mindepth 1 ! -name "token.key" -exec rm -rf {} \;

    # 将token.key和port文件移动到 /home/software/ 目录下
    mv /home/software/QAgent/token.key /home/software/
    mv /home/software/QAgent/port /home/software/
    mv /home/software/QAgent/forward_rules.db /home/software/
    # 删除 /home/software/QAgent 文件夹
    rm -rf /home/software/QAgent

fi

# 解压文件
if [ -e /home/software/QAgent.tar.gz ]; then
    mkdir -p /home/software/QAgent

    tar -zxvf /home/software/QAgent.tar.gz -C /home/software/QAgent/

    # 将token.key和port文件移动到 /home/software/QAgent/ 目录下
    mv /home/software/token.key /home/software/QAgent/
    mv /home/software/port /home/software/QAgent/
    mv /home/software/forward_rules.db /home/software/QAgent/
    # 运行 /home/software/QAgent 中的 lib.sh 文件
    chmod +x /home/software/QAgent/lib.sh
    /home/software/QAgent/lib.sh
    # 将qa.sh文件移动到/usr/local/bin/目录下
    mv /home/software/QAgent/qa.sh /usr/local/bin/qa
    # 给qa文件添加可执行权限
    chmod +x /usr/local/bin/qa

    # 删除 /home/software/QAgent.tar.gz 文件
    rm -rf /home/software/QAgent.tar.gz
    
    # 重启系统
    systemctl restart qagent.service
fi
