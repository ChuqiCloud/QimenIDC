#!/bin/bash

# 定义qagent.service的状态检查函数
check_qagent_status() {
    if systemctl is-active qagent.service &>/dev/null; then
        echo "QAgent is running."
    else
        echo "QAgent is not running."
    fi
}

# 保存主控下发的 token，不在被控端生成。
reset_token() {
    local token
    IFS= read -r -s -p "请输入主控 token: " token || return 1
    echo
    if printf '%s\n' "$token" | sudo /usr/bin/python3.10 /home/software/QAgent/service/Authentication.py set; then
        echo "主控 token 已保存。"
    else
        echo "保存失败，请检查输入；原 token 未改变。"
    fi
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
    echo "7. 设置 QAgent token"
    echo "8. 查看 QAgent port"
    echo "9. 重置 QAgent port"
    echo "10. 更新 QAgent 程序"
    echo "11. 查看主控可信白名单"
    echo "12. 输出 QAgent 公钥"
    echo "13. 更新 QAgent 证书"
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
            sudo cat /home/software/QAgent/token.key
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
            read -r port
            if [ -z "${port}" ];then
                port=7600
            fi
            if ! [[ "${port}" =~ ^[0-9]{1,5}$ ]] || [ "${port}" -lt 1 ] || [ "${port}" -gt 65535 ]; then
                echo "Invalid port"
            elif [ "${port}" = 6080 ]; then
                echo "Controller TLS port cannot collide with noVNC port 6080"
            else
                printf '%s\n' "${port}" | sudo tee /home/software/QAgent/port >/dev/null
                sudo bash /home/software/QAgent/harden.sh --configure
            fi
            ;;
        11)
            echo -e "\033[33m--->当前生效的主控来源白名单:\033[0m"
            sudo sed -n 's/^QAGENT_MANAGER_IPS=//p;s/^QAGENT_MANAGER_PROBES=//p' /etc/qimenidc/security.conf
            sudo sed -n '/qimenidc_qagent_allowed/,/^}/p' /etc/nginx/conf.d/qimenidc-qagent.conf
            ;;
        10)
            echo "---> 5秒后将更新最新版被控程序..."
            sleep 5
            # 停止QAgent
            sudo systemctl stop qagent.service
            # 运行/home/software/QAgent中的update.sh文件
            chmod +x /home/software/QAgent/update.sh
            # 执行update.sh文件
            if /home/software/QAgent/update.sh; then
                exit 0
            else
                echo -e "\033[31m--->QAgent update failed!\033[0m"
                sudo systemctl start qagent.service
                exit 1
            fi
            ;;
        12)
            echo -e "\033[33m--->节点公钥（复制完整 PEM 证书到主控）:\033[0m"
            if ! sudo openssl x509 -in /etc/qimenidc/qagent/qimenidc-qagent.crt -outform PEM; then
                echo "输出失败，请确认节点证书已生成，可通过 harden.sh 完成安全配置。"
            fi
            ;;
        13)
            echo "更新后请在主控重新保存节点公钥，否则主控将无法信任新证书。"
            sudo bash /home/software/QAgent/renew-certificate.sh
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
