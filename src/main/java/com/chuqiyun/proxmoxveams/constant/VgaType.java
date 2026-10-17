package com.chuqiyun.proxmoxveams.constant;

import java.util.Arrays;
import java.util.List;

/**
 * 虚拟机显示设备(vga)类型
 * 说明：不同显示设备的默认帧缓冲分辨率不同，客户机控制台(VNC)画面大小由此决定。
 * - std   : 默认类型，Bochs VBE 兼容，默认帧缓冲偏小(常见 1280x800 以下)
 * - qxl   : 默认 1024x768 起，推荐需要 1024x768 控制台画面的场景
 * - virtio: 需要客户机内安装 virtio-gpu 驱动
 * - vmware/cirrus/其他: 兼容老系统使用
 *
 * @author 星禾
 */
public class VgaType {
    /**
     * 默认显示设备类型
     */
    public static final String DEFAULT_VGA = "std";

    public static final String STD = "std";
    public static final String CIRRUS = "cirrus";
    public static final String VMWARE = "vmware";
    public static final String QXL = "qxl";
    public static final String QXL2 = "qxl2";
    public static final String QXL3 = "qxl3";
    public static final String QXL4 = "qxl4";
    public static final String VIRTIO = "virtio";
    public static final String NONE = "none";

    /**
     * 获取支持的显示设备类型列表
     */
    public static List<String> getVgaTypeList() {
        return Arrays.asList(STD, CIRRUS, VMWARE, QXL, QXL2, QXL3, QXL4, VIRTIO, NONE);
    }

    /**
     * 判断显示设备类型是否合法，null 视为合法(使用 PVE 默认值)
     */
    public static boolean isVgaTypeExist(String vgaType) {
        if (vgaType == null || vgaType.trim().isEmpty()) {
            return true;
        }
        return getVgaTypeList().contains(vgaType.trim().toLowerCase());
    }

    /**
     * 归一化显示设备类型，非法或为空时返回 null(不向 PVE 传递该参数)
     */
    public static String normalize(String vgaType) {
        if (!isVgaTypeExist(vgaType)) {
            return null;
        }
        if (vgaType == null || vgaType.trim().isEmpty()) {
            return null;
        }
        return vgaType.trim().toLowerCase();
    }
}
