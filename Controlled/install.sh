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
    apt-get install -y wget curl expect openvswitch-switch ifupdown2 sudo
}
# 初始化系统软件目录
function init_system_dir(){
    mkdir -p /home/images
    mkdir -p /home/software
    mkdir -p /home/software/websocketd
    mkdir -p /home/software/noVNC
    mkdir -p /home/software/QAgent
    mkdir -p /home/software/python3.10.5
    # 创建空文件vnc
    touch /home/software/vnc
}
# 安装必须环境依赖
function install_python_source(){
    apt-get install -y build-essential zlib1g-dev libncurses5-dev libgdbm-dev libnss3-dev libssl-dev libreadline-dev libffi-dev expect git
}
# 安装Python3.10.5
function install_python(){
    #判断是否安装python3.10.5
    if [ -f "/usr/bin/python3.10" ];then
        echo "python3.10.5 has been installed!"
    else
        cd /home/software
        # wget https://www.python.org/ftp/python/3.10.5/Python-3.10.5.tgz
        wget http://mirrors.leapteam.cn:8899/software/Python/Python-3.10.5.tgz --no-check-certificate
        tar -zxvf Python-3.10.5.tgz
        cd Python-3.10.5
        ./configure --prefix=/home/software/python3.10.5
        make && make install
        ln -s /home/software/python3.10.5/bin/python3.10 /usr/bin/python3.10
        ln -s /home/software/python3.10.5/bin/pip3.10 /usr/bin/pip3.10
        pip3.10 config set global.index-url https://pypi.mirrors.ustc.edu.cn/simple/
        pip3.10 install --upgrade pip
    fi
}


# 下载QimenIDC Controller
function download_qimenidc_controller(){
    cd /home/software
    wget http://mirrors.leapteam.cn:8899/software/QAgent/QAgent.tar.gz --no-check-certificate
    tar -zxvf /home/software/QAgent.tar.gz -C /home/software/QAgent/
}

# 安装QimenIDC Controller依赖
function install_qimenidc_controller_source(){
    pip3.10 install -r /home/software/QAgent/requirements.txt
}

# 配置QimenIDC Controller token文件
function config_qimenidc_controller_token(){
    # 黄色字体
    echo -e "\033[33m↓¯¯¯请输入QimenIDS Controlled token\033[0m"
    echo -e "\033[33m--->Please input QimenIDS Controlled token:\033[0m"
    read token
    echo $token > /home/software/QAgent/token.key
}

# 配置QimenIDC Controller端口
function config_qimenidc_controller_port(){
    # 黄色字体
    echo -e "\033[33m↓¯¯¯请输入QimenIDS被控端口,直接回车默认7600端口\033[0m"
    echo -e "\033[33m--->Please input QimenIDS Controlled port(default 7600): \033[0m"
    # 默认端口为7600
    read port
    # 判断是否输入端口
    if [ -z $port ];then
        port=7600
    fi
    echo $port > /home/software/QAgent/port
}

# 配置程序开机自启动以及systemd服务
function config_qimenidc_controller_service(){
    echo "[Unit]
Description=QimenIDC Controller
After=network.target
[Service]
Type=simple
ExecStart=/usr/bin/python3.10 /home/software/QAgent/main.py
Restart=on-failure
User=root
[Install]
WantedBy=multi-user.target" > /etc/systemd/system/qagent.service
    systemctl daemon-reload
    systemctl enable qagent.service
    systemctl start qagent.service
}

# 删除安装文件
function delete_install_file(){
    rm -rf /home/software/Python-3.10.5
    rm -rf /home/software/Python-3.10.5.tgz
    rm -rf /home/software/QAgent.tar.gz
}

# 安装命令菜单脚本
function install_qa(){
    # 将qa.sh文件移动到/usr/local/bin/目录下
    mv /home/software/QAgent/qa.sh /usr/local/bin/qa
    # 给qa文件添加可执行权限
    chmod +x /usr/local/bin/qa
}

# 替换虚拟机启动公屏图片
function replace_start_image(){
    # 将bootsplash.jpg文件f覆盖到/usr/share/qemu-server/bootsplash.jpg
    rm -rf /usr/share/qemu-server/bootsplash.jpg
    mv /home/software/QAgent/images/bootsplash.jpg /usr/share/qemu-server/
    # 重启pve 服务
    systemctl restart pvedaemon.service
}

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

# 下载websocketd程序
function download_websocketd(){
    cd /home/software/websocketd

    url="http://mirrors.leapteam.cn:8899/software/websocketd/$(check_system)/websocketd"

    wget "$url" --no-check-certificate
    chmod +x /home/software/websocketd/websocketd
}

# 下载noVNC程序
function download_noVNC(){
    cd /home/software/noVNC

    url="http://mirrors.leapteam.cn:8899/software/noVNC/noVNC.tar.gz"

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

# 执行cloudbase.sh脚本
function execute_cloudbase(){
    # 赋予权限
    chmod +x /home/software/QAgent/cloudbase.sh
    # 执行脚本
    /home/software/QAgent/cloudbase.sh
}

# 开始安装
function start_install(){
    update_source
    init_system_dir
    install_python_source
    install_python
    download_qimenidc_controller
    install_qimenidc_controller_source
    config_qimenidc_controller_port
    config_qimenidc_controller_token
    config_qimenidc_controller_service
    install_qa
    download_websocketd
    download_noVNC
    install_websockify
    config_noVNC_service
    delete_install_file
    systemctl status qagent.service
    replace_start_image
    execute_cloudbase
    echo -e "\033[32mQimenIDC Controller install success!\033[0m"
    echo -e "\033[32m-->start: systemctl start qagent.service\033[0m"
    echo -e "\033[32m-->stop: systemctl stop qagent.service\033[0m"
    echo -e "\033[32m-->restart: systemctl restart qagent.service\033[0m"
    echo -e "\033[32m-->status: systemctl status qagent.service\033[0m"
    echo -e "\033[32m可通过发送 qa 命令唤起快捷操作面板 \033[0m"
}

# 判断是否为root用户
if [ $(id -u) != "0" ];then
    echo -e "\033[31mError: You must be root to run this script!\033[0m"
    exit 1
else
    start_install
fi