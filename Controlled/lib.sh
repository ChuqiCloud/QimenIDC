#!/bin/bash

# 更新源
function update_source(){
    apt-get install -y wget
}

# 安装QimenIDC Controller依赖
function install_qimenidc_controller_source(){
    pip3.10 install -r /home/software/Controller/requirements.txt
}

# 开始更新
function start_update(){
    update_source
    install_qimenidc_controller_source
}

start_update