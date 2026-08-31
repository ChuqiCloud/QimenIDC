import os
import sys
import unittest
from types import SimpleNamespace
from unittest.mock import patch


sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from service.DiskUsage import get_vm_disk_usage


class DiskUsageTest(unittest.TestCase):
    @patch("service.DiskUsage._measure_volume")
    def test_separates_system_and_multiple_data_disks(self, measure_volume):
        measure_volume.side_effect = [
            (10, 100, "lvm-thin"),
            (20, 200, "file"),
            (30, 300, "zfspool"),
        ]
        item = SimpleNamespace(
            system_device="scsi0",
            disks=[
                SimpleNamespace(device="scsi0", volume="local-lvm:vm-100-disk-0", provisioned_bytes=100),
                SimpleNamespace(device="scsi1", volume="local:100/vm-100-disk-1.qcow2", provisioned_bytes=200),
                SimpleNamespace(device="scsi2", volume="local-zfs:vm-100-disk-2", provisioned_bytes=300),
            ],
        )

        result = get_vm_disk_usage(item)

        self.assertEqual("scsi0", result["systemDisk"]["device"])
        self.assertEqual(2, len(result["dataDisks"]))
        self.assertEqual(60, result["totalActualBytes"])
        self.assertTrue(result["complete"])

    @patch("service.DiskUsage._measure_volume")
    def test_returns_empty_data_disk_list(self, measure_volume):
        measure_volume.return_value = (10, 100, "lvm-thin")
        item = SimpleNamespace(
            system_device="scsi0",
            disks=[SimpleNamespace(
                device="scsi0",
                volume="local-lvm:vm-100-disk-0",
                provisioned_bytes=100,
            )],
        )

        result = get_vm_disk_usage(item)

        self.assertEqual([], result["dataDisks"])
        self.assertEqual(10, result["totalActualBytes"])


if __name__ == "__main__":
    unittest.main()
