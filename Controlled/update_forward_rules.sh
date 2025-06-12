#!/bin/bash
DB_PATH="/home/software/QAgent/forward_rules.db"
if [ ! -f "$DB_PATH" ]; then
    echo "Error: Database file $DB_PATH does not exist. Exiting."
    exit 1
fi
# 2. 检查 forward_rules 表是否存在 source_ip 列
if sqlite3 "$DB_PATH" "PRAGMA table_info(forward_rules);" | grep -q "source_ip"; then
    echo "字段 'source_ip' 已经存在，无需新增."
    exit 0
fi
# 3. 获取本机 IP 地址
get_default_ip() {
    # 尝试通过 4.ipw.cn 获取公网 IP
    IP=$(curl -s --connect-timeout 3 4.ipw.cn)
    if [ -n "$IP" ]; then
        echo "$IP"
        return
    fi
    # 尝试获取 vmbr0 网卡的 IP（适用于 Proxmox 等环境）
    if ip a show vmbr0 &>/dev/null; then
        IP=$(ip -4 addr show vmbr0 | grep -oP 'inet \K[\d.]+')
        if [ -n "$IP" ]; then
            echo "$IP"
            return
        fi
    fi
    echo "0.0.0.0"
}

DEFAULT_IP=$(get_default_ip)
echo "获取到的source_ip地址为: $DEFAULT_IP"

sqlite3 "$DB_PATH" <<EOF
CREATE TABLE temp_forward_rules (
    id INTEGER NOT NULL,
    source_ip VARCHAR NOT NULL DEFAULT '$DEFAULT_IP',
    source_port INTEGER NOT NULL,
    destination_ip VARCHAR NOT NULL,
    destination_port INTEGER NOT NULL,
    protocol VARCHAR(3) NOT NULL,
    vm VARCHAR NOT NULL,
    PRIMARY KEY (id)
);
INSERT INTO temp_forward_rules (
    id, source_ip, source_port, destination_ip, destination_port, protocol, vm
)
SELECT
    id, '$DEFAULT_IP', source_port, destination_ip, destination_port, protocol, vm
FROM forward_rules;
DROP TABLE forward_rules;
ALTER TABLE temp_forward_rules RENAME TO forward_rules;
.schema forward_rules;
EOF

iptables -t nat -F
# 6. 循环添加 NAT 转发规则
echo "重新激活NAT规则..."
sqlite3 "$DB_PATH" "SELECT source_ip, source_port, destination_ip, destination_port, protocol FROM forward_rules;" | while read -r line; do
    IFS='|' read -r src_ip src_port dst_ip dst_port proto <<< "$line"
    iptables -t nat -A PREROUTING \
        -d "$src_ip" -p "$proto" --dport "$src_port" \
        -j DNAT --to-destination "$dst_ip:$dst_port"
done

INTERFACES_FILE="/etc/network/interfaces"
TARGET_LINE="post-up echo 1 > /proc/sys/net/ipv4/ip_forward"
NAT_COMMAND=$(grep -A1 "$TARGET_LINE" "$INTERFACES_FILE" | tail -n1 | sed 's/^[[:space:]]*post-up[[:space:]]*//')
eval "$NAT_COMMAND"
if [ $? -eq 0 ]; then
    echo "所有NAT规则添加完成！"
else
    echo "错误：执行命令失败！请测试虚拟机是否有网络，若没网请联系技术支持！"
fi