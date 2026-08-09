#!/bin/bash

# 获取版本号
version=$(dpkg-query -W -f='${Version}' qemu-server 2>/dev/null || true)

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
    9.*)
        directory="9.0.0"
        ;;
    *)
        echo "Unsupported version: $major_version"
        exit 1
        ;;
esac # end case

# 判断版本号第一个数字是否小于 8
if [ "${major_version:0:1}" -lt 8 ]; then
    # 启用 sdn
    echo -e "\nsource /etc/network/interfaces.d/*" >>/etc/network/interfaces
    ifreload -a
fi

# 构建目录路径
patch_root="${CHUQI_CLOUDBASE_PATCH_ROOT:-/opt/chuqi-cloudbase-init}"
directory="$patch_root/qemu-server-$directory"

# 备份原文件
cp /usr/share/perl5/PVE/QemuServer/Cloudinit.pm /usr/share/perl5/PVE/QemuServer/Cloudinit.pm.orig
cp /usr/share/perl5/PVE/API2/Qemu.pm /usr/share/perl5/PVE/API2/Qemu.pm.orig

# 克隆仓库
if [ ! -d "$patch_root" ]; then
    git clone https://gitee.com/chuqicloud/chuqi-cloudbase-init.git "$patch_root"
fi

if [ -n "${CHUQI_CLOUDBASE_PATCH_DIR:-}" ]; then
    directory="$CHUQI_CLOUDBASE_PATCH_DIR"
fi

if [ ! -f "$directory/Cloudinit.pm.patch" ] || [ ! -f "$directory/Qemu.pm.patch" ]; then
    echo "Cloudbase-Init patch files not found in $directory"
    exit 1
fi

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
