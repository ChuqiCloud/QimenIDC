# /migration 仍处于下线状态；迁移主流程改走 /migrationAction，主控只按阶段调用并被控逐步执行
RETIRED_PATHS = frozenset(('/update', '/readFile', '/deleteFile', '/wget', '/migration'))


def is_retired_endpoint(path):
    normalized = path.rstrip('/')
    return any(normalized == route or normalized.startswith(route + '/') for route in RETIRED_PATHS) or normalized == '/nat' or normalized.startswith('/nat/')
