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

# 输出/home/software/QAgent/token.key文件内容
cat_token() {
    echo -e "\033[33m--->QAgent token:\033[0m"
    cat /home/software/QAgent/token.key
}

# 菜单循环
while true; do
    clear
    echo "=====QimenIDC -QAgent(被控端) 控制菜单====="
    echo "1. 启动 QAgent"
    echo "2. 重启 QAgent"
    echo "3. 停止 QAgent"
    echo "4. 检查 QAgent 状态"
    echo "5. 查看 QAgent 运行状态"
    echo "6. 查看 QAgent token"
    echo "7. 重置 QAgent token"
    echo "8. 查看 QAgent port"
    echo "9. 重置 QAgent port"
    echo "10. 更新被控端程序"
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
            sudo systemctl status qagent.service
            ;;
        6)
            cat_token
            ;;
        7)
            reset_token
            ;;
        8)
            echo -e "\033[33m--->QAgent port:\033[0m"
            cat /home/software/QAgent/port
            ;;
        9)
            echo -e "\033[33m↓¯¯¯请输入QimenIDC被控端口,直接回车默认7600端口\033[0m"
            echo -e "\033[33m--->Please input QimenIDC Controlled port(default 7600): \033[0m"
            read port
            if [ -z $port ];then
                port=7600
            fi
            echo $port > /home/software/QAgent/port

            # 提示是否现在重启QAgent
            echo -e "\033[33m↓¯¯¯是否现在重启QAgent？（输入y或n）\033[0m"
            echo -e "\033[33m--->Do you want to restart QAgent now? (input y or n)\033[0m"

            # 读取用户输入 如果为y,yes,Y,YES或不输入则重启QAgent
            read choice
            if [ -z $choice ] || [ $choice == "y" ] || [ $choice == "yes" ] || [ $choice == "Y" ] || [ $choice == "YES" ]; then
                sudo systemctl restart qagent.service
                echo "QAgent 重启中..."
                sleep 2
                check_qagent_status
            fi
            ;;
        10)
            echo "---> 5秒后将更新最新版被控程序..."
            sleep 5
            # 停止QAgent
            sudo systemctl stop qagent.service
            # 运行/home/software/QAgent中的update.sh文件
            chmod +x /home/software/QAgent/update.sh
            # 执行update.sh文件
            /home/software/QAgent/update.sh
            echo -e "\033[33m--->QAgent update success!\033[0m"
            exit 0 && qa
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
