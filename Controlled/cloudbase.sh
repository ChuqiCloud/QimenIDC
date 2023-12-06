#!/bin/bash

# 获取版本号
version=$(dpkg -s qemu-server | grep "Version" | awk '{print $2}')

# 提取主要版本号
major_version=$(echo "$version" | cut -d'-' -f1)

# 构建目录路径
case "$major_version" in
    "7.4")
        directory="7.4-4"
        ;;
    "7.3")
        directory="7.3-2"
        ;;
    "7.2")
        directory="7.2-4"
        ;;
    "7.1")
        directory="7.1-2"
        ;;
    "8.0")
        directory="8.0.6"
        ;;
    *)
        echo "Unsupported version: $major_version"
        exit 1 
        ;;
esac # end case

# 构建目录路径
directory="/opt/chuqi-cloudbase-init/qemu-server-$directory"

# 备份原文件
cp /usr/share/perl5/PVE/QemuServer/Cloudinit.pm /usr/share/perl5/PVE/QemuServer/Cloudinit.pm.orig
cp /usr/share/perl5/PVE/API2/Qemu.pm /usr/share/perl5/PVE/API2/Qemu.pm.orig

# 克隆仓库
cd /opt/ && git clone https://gitee.com/chuqicloud/chuqi-cloudbase-init.git

# 检查并应用补丁
patch_file() {
    patch --force --forward --backup -p0 --directory / --input "$1" --dry-run && echo "You can apply patch" || { echo "Can't apply patch!"; exit 1; }
    patch --force --forward --backup -p0 --directory / --input "$1"
    echo "Patch applied successfully!"
}

patch_file "$directory/Cloudinit.pm.patch"
patch_file "$directory/Qemu.pm.patch"

# 重启服务
systemctl restart pvedaemon.service

echo -e "\033[32mProxmoxVE modification successfully!\033[0m"