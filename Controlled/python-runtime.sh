#!/bin/bash

# QAgent 专用解释器；不修改 PVE 的 python、python3 或 /usr/bin 下的软链。
QAGENT_RUNTIME_ROOT="/home/software/qagent-python"
QAGENT_PRIVATE_PYTHON="${QAGENT_RUNTIME_ROOT}/bin/python3.10"
QAGENT_PYTHON_VERSION="3.10.22"
QAGENT_PYTHON_SHA256="c61fe9b1a1a7ec2a0b0388e664187c378a5325a89e3606d220abf5bdfccb2543"
QAGENT_PYTHON=""

qagent_runtime_fail() {
    echo "QAgent Python setup failed: $*" >&2
    return 1
}

qagent_python_check() {
    [ -x "$1" ] || return 1
    "$1" -I -c 'import sys; assert sys.version_info[:2] == (3, 10), "QAgent requires Python 3.10"; import ssl, sqlite3, ctypes, zlib; assert ssl.OPENSSL_VERSION_INFO >= (1, 1, 1)'
}

qagent_require_python() {
    # 忽略继承的解释器环境变量，禁止 root 服务被指向任意用户程序。
    QAGENT_PYTHON=""
    if [ -e "${QAGENT_RUNTIME_ROOT}" ] || [ -L "${QAGENT_RUNTIME_ROOT}" ]; then
        [ ! -L "${QAGENT_RUNTIME_ROOT}" ] && qagent_python_check "${QAGENT_PRIVATE_PYTHON}" || {
            qagent_runtime_fail "private runtime is invalid: ${QAGENT_RUNTIME_ROOT}; repair it before changing services"
            return 1
        }
        QAGENT_PYTHON="${QAGENT_PRIVATE_PYTHON}"
        return 0
    fi
    local candidate
    for candidate in /home/software/python3.10.5/bin/python3.10 /usr/bin/python3.10; do
        if qagent_python_check "${candidate}"; then
            QAGENT_PYTHON="${candidate}"
            return 0
        fi
    done
    qagent_runtime_fail "no working Python 3.10; run bash /home/software/QAgent/python-runtime.sh install"
}

qagent_python() {
    [ -n "${QAGENT_PYTHON:-}" ] || qagent_require_python || return 1
    "${QAGENT_PYTHON}" "$@"
}

qagent_install_python() (
    # 用临时安装镜像验证后发布，编译失败不留下半套运行环境。
    set -euo pipefail
    [ "$(id -u)" = 0 ] || { qagent_runtime_fail "run as root"; return 1; }
    [ ! -L /home/software ] && [ ! -L "${QAGENT_RUNTIME_ROOT}" ] || {
        qagent_runtime_fail "refusing symlink runtime directory"; return 1;
    }
    if [ -e "${QAGENT_RUNTIME_ROOT}" ]; then
        qagent_python_check "${QAGENT_PRIVATE_PYTHON}" || {
            qagent_runtime_fail "existing private runtime is incomplete; repair or move it aside before reinstalling"
            return 1
        }
        echo "QAgent private Python is ready: ${QAGENT_PRIVATE_PYTHON}"
        return 0
    fi
    apt-get update || return 1
    apt-get install -y build-essential ca-certificates curl xz-utils pkg-config \
        zlib1g-dev libncurses-dev libgdbm-dev libnss3-dev libssl-dev libreadline-dev \
        libffi-dev libsqlite3-dev libbz2-dev liblzma-dev || return 1
    install -d -o root -g root -m 755 /home/software || return 1
    local build_dir archive source_dir image
    build_dir=$(mktemp -d /home/software/.qagent-python-build.XXXXXX) || return 1
    trap 'rm -rf -- "${build_dir}"' EXIT
    archive="${build_dir}/Python-${QAGENT_PYTHON_VERSION}.tar.xz"
    source_dir="${build_dir}/Python-${QAGENT_PYTHON_VERSION}"
    image="${build_dir}/image${QAGENT_RUNTIME_ROOT}"
    curl --fail --location --proto '=https' --proto-redir '=https' \
        --connect-timeout 30 --max-time 600 --retry 2 \
        -o "${archive}" "https://mirror.chuqiyun.com/software/QAgent/Python-${QAGENT_PYTHON_VERSION}.tar.xz" || return 1
    printf '%s  %s\n' "${QAGENT_PYTHON_SHA256}" "${archive}" | sha256sum --check --status || {
        qagent_runtime_fail "Python source SHA-256 mismatch; nothing was built"; return 1;
    }
    tar -xJf "${archive}" -C "${build_dir}" || return 1
    # GCC 14 的 dom 优化阶段曾在 complex_pow 崩溃；禁用该优化，保留其余 O2 优化。
    # 不启用 PGO/LTO，也不复用之前失败构建的对象文件。
    (
        cd "${source_dir}" || exit 1
        ./configure --prefix="${QAGENT_RUNTIME_ROOT}" --with-ensurepip=no \
            CC=gcc CFLAGS='-O2 -fno-tree-dominator-opts' || exit 1
        make -j2 || exit 1
        make altinstall DESTDIR="${build_dir}/image" || exit 1
    ) || { qagent_runtime_fail "Python compilation failed; no interpreter or service was replaced"; return 1; }
    qagent_python_check "${image}/bin/python3.10" || {
        qagent_runtime_fail "built Python failed version/module checks"; return 1;
    }
    "${image}/bin/python3.10" -I -c 'import bz2, lzma, readline' || return 1
    # noVNC 的 qimenvnc 用户必须可读取 stdlib 及后续安装的离线依赖。
    chmod -R go+rX "${image}" || return 1
    mv -T -- "${image}" "${QAGENT_RUNTIME_ROOT}" || return 1
    echo "QAgent Python ${QAGENT_PYTHON_VERSION} installed at ${QAGENT_PRIVATE_PYTHON}; system Python was not changed."
)

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
    case "${1:-check}" in
        install) qagent_install_python ;;
        check) qagent_require_python && qagent_python -c 'import sys; print(sys.version); print(sys.executable)' ;;
        exec) shift; qagent_require_python && exec "${QAGENT_PYTHON}" "$@" ;;
        *) echo "Usage: bash python-runtime.sh [install|check|exec arguments...]" >&2; exit 1 ;;
    esac
fi
