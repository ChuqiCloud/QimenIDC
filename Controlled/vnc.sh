#!/bin/bash

# 检查是否提供了足够的参数
if [ $# -ne 2 ]; then
  echo "Usage: $0 <虚拟机ID> <VNC密码>"
  exit 1
fi

# 从命令行参数中获取虚拟机ID、password
vmid="$1"
password="$2"

# 设置独立的环境变量
export LC_PVE_TICKET="$password"

qm vncproxy "$vmid"