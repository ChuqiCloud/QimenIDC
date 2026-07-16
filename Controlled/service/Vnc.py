import os
import signal
import subprocess
import sys
import threading
import time

from service.VncFileEditor import VncFileEditor


class Vnc:
    _state_guard = threading.Lock()
    _port_states = {}

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

    def get_port_state(self):
        with Vnc._state_guard:
            if self.port not in Vnc._port_states:
                Vnc._port_states[self.port] = {
                    "lock": threading.Lock(),
                    "stop_event": threading.Event(),
                    "monitor": None,
                    "expires_at": 0,
                    "generation": 0,
                }
            return Vnc._port_states[self.port]

    def get_lifetime_seconds(self):
        return max(self.time, 1) * 60

    def get_vnc_proxy_command(self):
        proxy_script = os.path.join(os.path.dirname(__file__), "VncTcpProxy.py")
        return [sys.executable, proxy_script, str(self.port), str(self.vmid)]

    def start_vnc(self, restart=False):
        state = self.get_port_state()
        with state["lock"]:
            state["stop_event"].clear()
            state["expires_at"] = time.time() + self.get_lifetime_seconds()
            if restart:
                state["generation"] += 1
            if self.has_port_listener() and (restart or not self.is_vnc_running()):
                self.kill_vnc_process()

            if not self.is_vnc_running():
                process = self.start_vnc_process()
                if process is None or not self.wait_until_vnc_running(process):
                    if process is not None and process.poll() is None:
                        process.kill()
                    return False
            else:
                process = None

            monitor = state["monitor"]
            if restart or monitor is None or not monitor.is_alive():
                state["generation"] += 1
                generation = state["generation"]
                monitor_thread = threading.Thread(
                    target=self.monitor_vnc_process,
                    args=(state, generation, process),
                    daemon=True,
                )
                state["monitor"] = monitor_thread
                monitor_thread.start()
            return True

    def start_vnc_process(self):
        environment = os.environ.copy()
        environment["QIMEN_VNC_TICKET"] = str(self.password)
        try:
            return subprocess.Popen(
                self.get_vnc_proxy_command(),
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                env=environment,
                start_new_session=True,
            )
        except Exception:
            return None

    def wait_until_vnc_running(self, process, timeout_seconds=5):
        deadline = time.time() + timeout_seconds
        while time.time() < deadline:
            if self.is_vnc_running():
                return True
            if process.poll() is not None:
                return False
            time.sleep(0.2)
        return self.is_vnc_running()

    def monitor_vnc_process(self, state, generation, process):
        while not state["stop_event"].wait(5):
            if state["generation"] != generation:
                return
            if time.time() >= state["expires_at"]:
                break
            if self.is_vnc_running():
                continue

            with state["lock"]:
                if state["generation"] != generation or state["stop_event"].is_set():
                    return
                if time.time() >= state["expires_at"]:
                    break
                if not self.is_vnc_running():
                    if self.has_port_listener():
                        self.kill_vnc_process()
                    process = self.start_vnc_process()
                    if process is None:
                        continue
                    self.wait_until_vnc_running(process)

        with state["lock"]:
            if state["generation"] == generation and not state["stop_event"].is_set():
                self.kill_vnc_process()
                state["monitor"] = None

    def restart_vnc(self):
        return self.start_vnc(restart=True)

    def ensure_vnc(self):
        return self.start_vnc(restart=False)

    def stop_vnc(self):
        state = self.get_port_state()
        with state["lock"]:
            state["stop_event"].set()
            self.kill_vnc_process()
            state["generation"] += 1
            state["monitor"] = None
        return True

    def get_port_pids(self, listen_only=False):
        command = ["lsof", "-nP", f"-iTCP:{self.port}", "-t"]
        if listen_only:
            command.insert(3, "-sTCP:LISTEN")
        try:
            result = subprocess.run(command, capture_output=True, text=True, check=False)
        except FileNotFoundError:
            return []
        if result.returncode != 0:
            return []
        return [pid.strip() for pid in result.stdout.splitlines() if pid.strip()]

    def is_vnc_running(self):
        proxy_script = os.path.abspath(os.path.join(os.path.dirname(__file__), "VncTcpProxy.py"))
        expected_args = {proxy_script, str(self.port), str(self.vmid)}
        for pid in self.get_port_pids(listen_only=True):
            try:
                with open(f"/proc/{pid}/cmdline", "rb") as command_file:
                    command_args = {
                        argument.decode(errors="replace")
                        for argument in command_file.read().split(b"\0")
                        if argument
                    }
            except OSError:
                continue
            if expected_args.issubset(command_args):
                return True
        return False

    def has_port_listener(self):
        return len(self.get_port_pids(listen_only=True)) > 0

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
        pids = self.get_port_pids(listen_only=True)
        process_groups = set()
        for pid in pids:
            try:
                process_groups.add(os.getpgid(int(pid)))
            except (ProcessLookupError, ValueError):
                continue

        for process_group in process_groups:
            try:
                os.killpg(process_group, signal.SIGTERM)
            except ProcessLookupError:
                continue

        deadline = time.time() + 2
        while time.time() < deadline and self.has_port_listener():
            time.sleep(0.1)

        for process_group in process_groups:
            try:
                os.killpg(process_group, signal.SIGKILL)
            except ProcessLookupError:
                continue
        return True

    def main(self, restart=False):
        self.insert_vnc_token()
        return self.start_vnc(restart=restart)
