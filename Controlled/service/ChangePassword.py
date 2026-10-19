import os
import signal
import subprocess
from pathlib import Path

'''
执行修改密码脚本
Execute the change password script
'''
def run_change_password(id: int, username: str, password: str):
    script = Path(__file__).resolve().parent.parent / 'change_password.sh'
    process = subprocess.Popen(['expect', str(script), str(id), username, password],
                               stdout=subprocess.PIPE, stderr=subprocess.PIPE, start_new_session=True)
    try:
        process.communicate(timeout=25)
    except subprocess.TimeoutExpired:
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        process.communicate()
        return False
    return process.returncode == 0