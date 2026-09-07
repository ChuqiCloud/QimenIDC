package com.chuqiyun.proxmoxveams.dto;

/**
 * NAT 操作结果，保留失败原因供接口直接返回。
 */
public final class NatOperationResult {
    private final boolean success;
    private final String message;

    private NatOperationResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static NatOperationResult success() {
        return new NatOperationResult(true, "操作成功");
    }

    public static NatOperationResult failure(String message) {
        return new NatOperationResult(false, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
