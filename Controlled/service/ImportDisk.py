import subprocess

'''
导入磁盘镜像调用函数
Import disk image call function
'''
def import_disk_to_vm(vmid, image_path, save_path):
    # 构建命令
    command = f"qm importdisk {vmid} {image_path} {save_path}"

    # 执行命令
    try:
        subprocess.run(command, shell=True, check=True)
        return True
    except subprocess.CalledProcessError as e:
        return False