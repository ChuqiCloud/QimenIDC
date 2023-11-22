class VncFileEditor:
    def __init__(self, file_path: str):
        self.file_path = file_path

    # 读取文件
    def read_file(self):
        try:
            with open(self.file_path, 'r') as file:
                return [line.strip() for line in file]
        except FileNotFoundError:
            return []

    # 写入文件
    def write_file(self, data):
        try:
            with open(self.file_path, 'w') as file:
                for line in data:
                    file.write(line + '\n')
        except FileNotFoundError:
            return False

    # 增加
    def add_entry(self, username, ip, port):
        self.clean_file()
        data = self.read_file()
        new_entry = f"{username}: {ip}:{port}"
        data.append(new_entry)
        if self.write_file(data):
            return True
        else:
            return False

    # 删除
    def delete_entry(self, username):
        self.clean_file()
        data = self.read_file()
        updated_data = [line for line in data if not line.startswith(username + ':')]
        if self.write_file(updated_data):
            return True
        else:
            return False

    # 更新
    def update_entry(self, username, new_ip, new_port):
        self.clean_file()
        data = self.read_file()
        updated_data = [line if not line.startswith(username + ':') else f"{username}: {new_ip}:{new_port}" for line in data]
        if self.write_file(updated_data):
            return True
        else:
            return False

    # 查看
    def view_entries(self,username):
        self.clean_file()
        # 查找指定用户名的行号，然后读取该行
        data = self.read_file()
        list = []
        for line in data:
            if line.startswith(username + ':'):
                list.append(line)
        return list

    # 清理文件中的空行
    def clean_file(self):
        data = self.read_file()
        data = [line for line in data if line.strip()]
        self.write_file(data)


# 使用示例
#file_editor = VncFileEditor('/home/software/vnc')
#file_editor = VncFileEditor('D:\Project\初七云\ProxmoxVE-AMS\Controlled/vnc')
# 增加
#file_editor.add_entry('user3', '192.168.1.1', '591')

# 查看
#file_editor.view_entries()

# 更新
#file_editor.update_entry('user1', '192.168.1.5', '6902')

# 删除
#file_editor.delete_entry('user2')

# 查看更新后的内容
#data_list = file_editor.view_entries("user3")
#print(data_list)
