#!/bin/bash
# 导入指定目录下镜像到指定虚拟机
# 用法：importdisk.sh <vmid> <disk_dir> <storage>
# 例如：importdisk.sh 100 /home/iso local-lvm
# 说明：vmid为虚拟机ID，disk_dir为镜像所在目录，storage为存储名称
# 作者：mryunqi v1.0 2023/7/16

vmid=$1
disk_dir=$2
storage=$3

do
  qm importdisk $vmid $disk_dir $storage
done
