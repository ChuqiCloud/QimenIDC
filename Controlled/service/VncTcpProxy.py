import os
import signal
import socket
import subprocess
import sys
import threading


BUFFER_SIZE = 64 * 1024
TICKET_ENV_NAME = "QIMEN_VNC_TICKET"


class VncTcpProxy:
    def __init__(self, port, vmid, ticket):
        self.port = int(port)
        self.vmid = int(vmid)
        self.ticket = ticket
        self.stop_event = threading.Event()
        self.listener = None

    def stop(self, *_args):
        self.stop_event.set()
        if self.listener is not None:
            try:
                self.listener.close()
            except OSError:
                pass

    def run(self):
        signal.signal(signal.SIGTERM, self.stop)
        signal.signal(signal.SIGINT, self.stop)

        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as listener:
            self.listener = listener
            listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            listener.bind(("0.0.0.0", self.port))
            listener.listen(16)
            listener.settimeout(1)

            while not self.stop_event.is_set():
                try:
                    client, _address = listener.accept()
                except socket.timeout:
                    continue
                except OSError:
                    if self.stop_event.is_set():
                        break
                    raise

                threading.Thread(
                    target=self.handle_client,
                    args=(client,),
                    daemon=True,
                ).start()

    def handle_client(self, client):
        environment = os.environ.copy()
        environment["LC_PVE_TICKET"] = self.ticket
        process = None
        socket_reader = None
        try:
            process = subprocess.Popen(
                ["qm", "vncproxy", str(self.vmid)],
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
                env=environment,
                bufsize=0,
            )
            socket_reader = threading.Thread(
                target=self.forward_socket_to_process,
                args=(client, process),
                daemon=True,
            )
            socket_reader.start()
            self.forward_process_to_socket(process, client)
        finally:
            try:
                client.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
            client.close()

            if process is not None and process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=2)
                except subprocess.TimeoutExpired:
                    process.kill()
            if socket_reader is not None:
                socket_reader.join(timeout=1)

    def forward_socket_to_process(self, client, process):
        try:
            while True:
                data = client.recv(BUFFER_SIZE)
                if not data:
                    break
                offset = 0
                while offset < len(data):
                    written = process.stdin.write(data[offset:])
                    if written is None or written <= 0:
                        raise BrokenPipeError
                    offset += written
        except (BrokenPipeError, ConnectionError, OSError):
            pass
        finally:
            if process.stdin is not None:
                try:
                    process.stdin.close()
                except OSError:
                    pass

    def forward_process_to_socket(self, process, client):
        try:
            while True:
                data = process.stdout.read(BUFFER_SIZE)
                if not data:
                    break
                client.sendall(data)
        except (BrokenPipeError, ConnectionError, OSError):
            pass


def main():
    if len(sys.argv) != 3:
        raise SystemExit("Usage: VncTcpProxy.py <port> <vmid>")

    ticket = os.environ.get(TICKET_ENV_NAME)
    if not ticket:
        raise SystemExit(f"{TICKET_ENV_NAME} is required")

    VncTcpProxy(sys.argv[1], sys.argv[2], ticket).run()


if __name__ == "__main__":
    main()
