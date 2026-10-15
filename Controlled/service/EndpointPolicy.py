RETIRED_PATHS = frozenset(('/update', '/readFile', '/deleteFile', '/wget', '/migration'))


def is_retired_endpoint(path):
    normalized = path.rstrip('/')
    return any(normalized == route or normalized.startswith(route + '/') for route in RETIRED_PATHS) or normalized == '/nat' or normalized.startswith('/nat/')
