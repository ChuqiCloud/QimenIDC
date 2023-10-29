#!/bin/bash

# 定义qagent.service的状态检查函数
check_qagent_status() {
    if systemctl is-active qagent.service &>/dev/null; then
        echo "QAgent is running."
    else
        echo "QAgent is not running."
    fi
}

# 重置/home/software/QAgent/token.key文件内容
reset_token() {
    echo -e "\033[33m--->Please input QimenIDS Master token:\033[0m"
    read token
    echo $token > /home/software/QAgent/token.key
}

# 菜单循环
while true; do
    clear
    echo "=====QimenIDC -QAgent(被控端) 控制菜单====="
    echo "1. 启动 QAgent"
    echo "2. 重启 QAgent"
    echo "3. 停止 QAgent"
    echo "4. 检查 QAgent 状态"
    echo "5. 重置 QAgent token"
    echo "6. 更新被控端程序"
    echo "q. 退出"
    read -p "请选择序号操作: " choice

    case $choice in
        1)
            sudo systemctl start qagent.service
            echo "QAgent 启动中..."
            sleep 2
            check_qagent_status
            ;;
        2)
            sudo systemctl restart qagent.service
            echo "QAgent 重启中..."
            sleep 2
            check_qagent_status
            ;;
        3)
            sudo systemctl stop qagent.service
            echo "QAgent 停止中..."
            sleep 2
            check_qagent_status
            ;;
        4)
            check_qagent_status
            ;;
        5)
            reset_token
            ;;
        6)
            echo "---> 5秒后将更新最新版被控程序..."
            sleep 5
            # 运行/home/software/QAgent中的update.sh文件
            chmod +x /home/software/QAgent/update.sh
            # 执行update.sh文件
            sh /home/software/QAgent/update.sh
            ;;
        q)
            echo "退出菜单"
            exit 0
            ;;
        *)
            echo "无效的选择，请重新选择。"
            ;;
    esac

    read -p "按Enter键继续..."
done
