import os

def get_port_file_path() -> str:
    return os.path.join('/home/software/QAgent', 'port')

def get_port() -> int:
    port_file_path = get_port_file_path()

    try:
        # 判断是否存在port文件，否则创建
        if not os.path.exists(port_file_path):
            with open(port_file_path, 'w') as f:
                f.write('7600')

        # 读取port文件中的端口号
        with open(port_file_path, 'r') as f:
            port = f.read()

        # 删除结尾的换行符
        port = port.strip()

        # 转换为int类型
        port = int(port)
        return port
    except:
        return 7600
    