package com.chuqiyun.proxmoxveams.common;

/**
 * @Author: 星禾
 * @Description: 携带业务码的认证异常，用于区分密码错误、二步验证失败等场景
 * @DateTime: 2026/10/17 15:45
 */
public class AuthException extends RuntimeException {
    private final ResponseResult.RespCode respCode;

    public AuthException(ResponseResult.RespCode respCode) {
        super(respCode.getMessage());
        this.respCode = respCode;
    }

    public ResponseResult.RespCode getRespCode() {
        return respCode;
    }

    public ResponseResult<?> toResult() {
        return ResponseResult.fail(respCode);
    }
}
