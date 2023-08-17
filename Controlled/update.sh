#!/bin/bash

# 下载url get_update_info_url（）中的文件连接到/home/software/
function download_file() {
    # 先获取https://gitee.com/chuqicloud/soft-ware/raw/master/Cloud/update.txt中文件下载链接
    # 然后下载到/home/software/中
    local url=$(curl -s https://gitee.com/chuqicloud/soft-ware/raw/master/Cloud/update.txt)
    wget -P /home/software/ $url
}

# 解压/home/software/中的文件Controller.tar.gz
function unzip_file() {
    tar -zxvf /home/software/Controller.tar.gz -C /home/software/Controller/
}

# 运行/home/software/Controller中的lib.sh文件
function run_lib() {
    chmod +x /home/software/Controller/lib.sh
    /home/software/Controller/lib.sh
}

# 重启系统
function reboot_system() {
    systemctl restart qimenidc_controller.service
}
#开始更新
download_file
unzip_file
run_lib
reboot_system
