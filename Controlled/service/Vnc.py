import os
import shlex
import subprocess

from service.VncFileEditor import VncFileEditor


class Vnc:
    def __init__(self, vnc_file_path, host, port, username, password, time, vmid):
        self.vnc_file_path = vnc_file_path
        self.host = host
        self.port = int(port)
        self.username = username
        self.password = password
        self.time = int(time)
        self.vmid = int(vmid)
        self.vnc_file_editor = VncFileEditor(self.vnc_file_path)

    def run_command(self, command):
        process = subprocess.Popen(command, shell=True)
        process.wait()
        return process.returncode

    def get_websocketd_ssl_args(self):
        cert_file = "/home/software/noVNC/certs/qimenidc-vnc.crt"
        key_file = "/home/software/noVNC/certs/qimenidc-vnc.key"
        if os.path.exists(cert_file) and os.path.exists(key_file):
            return f" --ssl --sslcert={shlex.quote(cert_file)} --sslkey={shlex.quote(key_file)}"
        return ""

    def start_vnc(self):
        if self.is_vnc_running():
            self.kill_vnc_process()

        ssl_args = self.get_websocketd_ssl_args()
        command = (
            f"/home/software/websocketd/websocketd{ssl_args}"
            f" --address={shlex.quote(str(self.host))}"
            f" --port {self.port}"
            " --binary=true"
            f" /home/software/QAgent/vnc.sh {self.vmid} {shlex.quote(str(self.password))}"
        )
        process = subprocess.Popen(command, shell=True)
        try:
            process.wait(timeout=self.time)
        except subprocess.TimeoutExpired:
            self.kill_vnc_process()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()

    def stop_vnc(self):
        if not self.is_vnc_running():
            return True
        self.kill_vnc_process()
        return True

    def is_vnc_running(self):
        command = f"lsof -i:{self.port}"
        output = os.popen(command).read().strip()
        return bool(output)

    def insert_vnc_token(self):
        vnc_list = self.vnc_file_editor.view_entries(self.username)
        if len(vnc_list) > 0:
            if len(vnc_list) > 1:
                self.vnc_file_editor.delete_entry(self.username)
                self.vnc_file_editor.add_entry(self.username, self.host, self.port)
                return True
            self.vnc_file_editor.update_entry(self.username, self.host, self.port)
            return True
        self.vnc_file_editor.add_entry(self.username, self.host, self.port)
        return True

    def kill_vnc_process(self):
        command = f"lsof -ti:{self.port} | xargs -r kill -9"
        self.run_command(command)
        return True

    def main(self):
        self.insert_vnc_token()
        self.start_vnc()
        return True
