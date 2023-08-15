#!/bin/bash
echo "##############################################"
echo "#                                            #"
echo "#        QimenIDC Controller Install         #"
echo "#                                            #"
echo "##############################################"
echo "-Author: mryunqi"
echo "-Email:434658198@qq.com"
echo -e "\033[33mThis script is used to install QimenIDC Controller.\033[0m"
echo -e "\033[33mThis script is applicable to Debian series OS.\033[0m"
echo -e ""
echo -e "The script will continue to execute in 8 seconds, you can use Ctrl+C to pause......"
sleep 8
# debian更新源
function update_source(){
    apt-get update
    apt-get upgrade -y
    apt-get install -y wget curl expect
}
# 初始化系统软件目录
function init_system_dir(){
    mkdir -p /home/images
    mkdir -p /home/software
    mkdir -p /home/software/Controller
    mkdir -p /home/software/python3.10.5
}
# 安装必须环境依赖
function install_python_source(){
    apt-get install -y build-essential zlib1g-dev libncurses5-dev libgdbm-dev libnss3-dev libssl-dev libreadline-dev libffi-dev expect
}
# 安装Python3.10.5
function install_python(){
    #判断是否安装python3.10.5
    if [ -f "/usr/bin/python3.10" ];then
        echo "python3.10.5 has been installed!"
    else
        cd /home/software
        wget https://www.python.org/ftp/python/3.10.5/Python-3.10.5.tgz
        tar -zxvf Python-3.10.5.tgz
        cd Python-3.10.5
        ./configure --prefix=/home/software/python3.10.5
        make && make install
        ln -s /home/software/python3.10.5/bin/python3.10 /usr/bin/python3.10
        ln -s /home/software/python3.10.5/bin/pip3.10 /usr/bin/pip3.10
        pip3.10 install --upgrade pip
    fi
}


# 下载QimenIDC Controller
function download_qimenidc_controller(){
    cd /home/software/Controller
    wget http://mirror.chuqiyun.com/software/Controller/main.py
    wget http://mirror.chuqiyun.com/software/Controller/requirements.txt
    wget http://mirror.chuqiyun.com/software/Controller/importdisk.sh
    wget http://mirror.chuqiyun.com/software/Controller/change_password.sh
    wget http://mirror.chuqiyun.com/software/Controller/update.sh
}

# 安装QimenIDC Controller依赖
function install_qimenidc_controller_source(){
    pip3.10 install -r /home/software/Controller/requirements.txt
}

# 配置QimenIDC Controller token文件
function config_qimenidc_controller_token(){
    # 黄色字体
    echo -e "\033[33m-->Please input QimenIDS Master token:\033[0m"
    read token
    echo $token > /home/software/Controller/token.key
}

# 配置程序开机自启动以及systemd服务
function config_qimenidc_controller_service(){
    echo "[Unit]
Description=QimenIDC Controller
After=network.target
[Service]
Type=simple
ExecStart=/usr/bin/python3.10 /home/software/Controller/main.py
Restart=on-failure
User=root
[Install]
WantedBy=multi-user.target" > /etc/systemd/system/qimenidc_controller.service
    systemctl daemon-reload
    systemctl enable qimenidc_controller.service
    systemctl start qimenidc_controller.service
}

# 删除安装文件
function delete_install_file(){
    rm -rf /home/software/Python-3.10.5
    rm -rf /home/software/Python-3.10.5.tgz
}

# 开始安装
function start_install(){
    update_source
    init_system_dir
    install_python_source
    install_python
    download_qimenidc_controller
    install_qimenidc_controller_source
    config_qimenidc_controller_token
    config_qimenidc_controller_service
    delete_install_file
    systemctl status qimenidc_controller.service
    echo -e "\033[32mQimenIDC Controller install success!\033[0m"
    echo -e "\033[32m-->start: systemctl start qimenidc_controller.service\033[0m"
    echo -e "\033[32m-->stop: systemctl stop qimenidc_controller.service\033[0m"
    echo -e "\033[32m-->restart: systemctl restart qimenidc_controller.service\033[0m"
    echo -e "\033[32m-->status: systemctl status qimenidc_controller.service\033[0m"
}

# 判断是否为root用户
if [ $(id -u) != "0" ];then
    echo -e "\033[31mError: You must be root to run this script!\033[0m"
    exit 1
else
    start_install
fi