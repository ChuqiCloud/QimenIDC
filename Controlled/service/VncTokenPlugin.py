from service.VncFileEditor import VncFileEditor


class ExpiringTokenFile:
    # 每次websocket握手检查磁盘上的过期时间，代理重启后仍执行同一策略。
    def __init__(self, src):
        self.editor = VncFileEditor(src)

    def lookup(self, token):
        return self.editor.lookup(token)
