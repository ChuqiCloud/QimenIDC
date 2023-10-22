package com.chuqiyun.proxmoxveams.common;

/**
 * @author mryunqi
 * @date 2023/8/6
 */
public enum UnifiedResultCode {
    /*通用错误码范围：1xxx*/

    // 未知错误
    ERROR_UNKNOWN(1000, "Unknown error"),

    // 无效参数
    ERROR_INVALID_PARAM(1001, "Invalid parameter"),

    // 节点不存在
    ERROR_NODE_NOT_EXIST(1002, "Node does not exist"),
    // 节点不可用
    ERROR_NODE_NOT_AVAILABLE(1003, "Node is not available"),
    // 没有可用IPV4地址
    ERROR_NO_AVAILABLE_IPV4(1004, "No available IPV4 address"),
    // 没有可用IPV6地址
    ERROR_NO_AVAILABLE_IPV6(1005, "No available IPV6 address"),
    // 镜像不能为空
    ERROR_IMAGE_NOT_NULL(1006, "Image cannot be empty"),
    // cpu类型不支持
    ERROR_CPU_TYPE_NOT_EXIST(1007, "CPU type does not exist"),
    // os类型不能为空
    ERROR_OS_TYPE_NOT_NULL(1008, "OS type cannot be empty"),
    // 系统架构不支持
    ERROR_ARCHITECTURE_NOT_EXIST(1009, "Architecture does not exist"),
    // cpu类型不支持嵌套虚拟化
    ERROR_CPU_TYPE_NOT_SUPPORT_NESTED(1010, "CPU type does not support nested virtualization"),
    // 创建虚拟机失败
    ERROR_CREATE_VM_FAILED(1011, "Failed to create virtual machine"),
    // 用户名不能为空
    ERROR_USERNAME_NOT_NULL(1012, "Username cannot be empty"),
    // 密码不能为空
    ERROR_PASSWORD_NOT_NULL(1013, "Password cannot be empty"),
    // 镜像不存在
    ERROR_CLOUD_IMAGE_NOT_EXIST(1014, "Cloud Image does not exist"),
    // 镜像不可用
    ERROR_CLOUD_IMAGE_NOT_AVAILABLE(1015, "Cloud Image is not available"),
    // vm不存在
    ERROR_VM_NOT_EXIST(1016, "Virtual machine does not exist"),
    // 重置系统失败
    ERROR_RESET_SYSTEM_FAILED(1017, "Failed to reset system"),
    // 虚拟机为禁用状态
    ERROR_VM_IS_DISABLED(1018, "Virtual machine is disabled"),
    // 删除虚拟机失败
    ERROR_DELETE_VM_FAILED(1019, "Failed to delete virtual machine"),

    // 不存在该配置模板
    ERROR_CONFIGURE_TEMPLATE_NOT_EXIST(1020, "The configuration template does not exist"),
    // 虚拟机已到期
    ERROR_VM_IS_EXPIRED(1021, "Virtual machine has expired"),
    // 新密码不能为空
    ERROR_NEW_PASSWORD_NOT_NULL(1022, "New password cannot be empty"),
    // 重置密码失败
    ERROR_RESET_PASSWORD_FAILED(1023, "Failed to reset password"),
    // 虚拟机续期失败
    ERROR_RENEWAL_FAILED(1024, "Failed to renew virtual machine"),
    // 该节点还有虚拟机存在，请先删除虚拟机
    ERROR_NODE_HAS_VM(1025, "There are still virtual machines on this node, please delete the virtual machine first"),
    // 虚拟机删除失败，未知错误
    ERROR_DELETE_VM_UNKNOWN(1026, "Failed to delete virtual machine, unknown error"),
    /*pve错误码范围：2xxx*/

    /*成功*/
    SUCCESS(200, "Success");


    private final int code;
    private final String message;

    UnifiedResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
