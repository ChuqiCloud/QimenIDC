import json
import math
import os
import subprocess


COMMAND_TIMEOUT_SECONDS = 15


def _run(command):
    return subprocess.run(
        command,
        capture_output=True,
        text=True,
        check=False,
        timeout=COMMAND_TIMEOUT_SECONDS,
    )


def _require_success(result, operation):
    if result.returncode == 0:
        return result.stdout.strip()
    message = result.stderr.strip() or result.stdout.strip() or "unknown error"
    raise RuntimeError(f"{operation} failed: {message}")


def _number(value):
    if value is None or value == "":
        return None
    return float(str(value).strip())


def _measure_lvm(path):
    result = _run([
        "lvs",
        "--reportformat", "json",
        "--units", "b",
        "--nosuffix",
        "-o", "lv_size,data_percent",
        path,
    ])
    output = _require_success(result, "lvs")
    reports = json.loads(output).get("report", [])
    volumes = reports[0].get("lv", []) if reports else []
    if not volumes:
        raise RuntimeError("lvs did not return the requested volume")

    volume = volumes[0]
    provisioned = int(_number(volume.get("lv_size")))
    data_percent = _number(volume.get("data_percent"))
    if data_percent is None:
        return provisioned, provisioned, "lvm"
    actual = int(math.ceil(provisioned * data_percent / 100.0))
    return actual, provisioned, "lvm-thin"


def _measure_zfs(path):
    dataset = path[len("/dev/zvol/"):]
    result = _run([
        "zfs", "get", "-Hp", "-o", "value",
        "usedbydataset,volsize", dataset,
    ])
    values = _require_success(result, "zfs get").splitlines()
    if len(values) < 2:
        raise RuntimeError("zfs get returned incomplete volume data")
    return int(values[0]), int(values[1]), "zfspool"


def _parse_rbd_path(path):
    if path.startswith("rbd:"):
        path = path[len("rbd:"):]
    elif path.startswith("/dev/rbd/"):
        path = path[len("/dev/rbd/"):]
    else:
        raise RuntimeError(f"unsupported RBD path: {path}")
    values = path.split(":")
    image = values[0]
    options = {}
    for value in values[1:]:
        if "=" in value:
            key, option_value = value.split("=", 1)
            options[key] = option_value
    return image, options


def _measure_rbd(path):
    image, options = _parse_rbd_path(path)
    command = ["rbd", "du", image, "--format", "json"]
    if options.get("conf"):
        command.extend(["--conf", options["conf"]])
    if options.get("id"):
        command.extend(["--id", options["id"]])
    if options.get("keyring"):
        command.extend(["--keyring", options["keyring"]])

    data = json.loads(_require_success(_run(command), "rbd du"))
    images = data.get("images", [])
    if not images:
        raise RuntimeError("rbd du did not return the requested image")
    image_data = images[0]
    return int(image_data["used_size"]), int(image_data["provisioned_size"]), "rbd"


def _measure_file(path):
    result = _run(["qemu-img", "info", "--output=json", path])
    data = json.loads(_require_success(result, "qemu-img info"))
    actual = data.get("actual-size")
    provisioned = data.get("virtual-size")
    if actual is None:
        raise RuntimeError("qemu-img info did not return actual-size")
    return int(actual), int(provisioned) if provisioned is not None else None, "file"


def _measure_volume(volume):
    path = _require_success(_run(["pvesm", "path", volume]), "pvesm path")
    if path.startswith("/dev/zvol/"):
        return _measure_zfs(path)
    if path.startswith("rbd:") or path.startswith("/dev/rbd/"):
        return _measure_rbd(path)
    if os.path.isfile(path):
        return _measure_file(path)

    try:
        return _measure_lvm(path)
    except (RuntimeError, ValueError, KeyError, json.JSONDecodeError):
        return _measure_file(path)


def get_vm_disk_usage(item):
    measured_disks = []
    for disk in item.disks:
        storage = disk.volume.split(":", 1)[0] if ":" in disk.volume else None
        measured = {
            "device": disk.device,
            "storage": storage,
            "volume": disk.volume,
            "provisionedBytes": disk.provisioned_bytes,
            "actualBytes": None,
            "backend": None,
            "available": False,
        }
        try:
            actual, provisioned, backend = _measure_volume(disk.volume)
            measured["actualBytes"] = actual
            measured["provisionedBytes"] = provisioned or disk.provisioned_bytes
            measured["backend"] = backend
            measured["available"] = True
        except (OSError, subprocess.SubprocessError, RuntimeError, ValueError, KeyError, json.JSONDecodeError) as error:
            measured["error"] = str(error)
        measured_disks.append(measured)

    system_disk = next(
        (disk for disk in measured_disks if disk["device"] == item.system_device),
        None,
    )
    data_disks = [disk for disk in measured_disks if disk["device"] != item.system_device]
    available_disks = [disk for disk in measured_disks if disk["available"]]
    total_provisioned_bytes = sum(
        disk["provisionedBytes"]
        for disk in available_disks
        if disk["provisionedBytes"] is not None
    )
    return {
        "systemDisk": system_disk,
        "dataDisks": data_disks,
        "totalActualBytes": sum(disk["actualBytes"] for disk in available_disks),
        "totalProvisionedBytes": total_provisioned_bytes,
        "complete": len(available_disks) == len(measured_disks),
    }
