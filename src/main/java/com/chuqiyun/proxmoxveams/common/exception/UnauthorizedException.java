package com.chuqiyun.proxmoxveams.common.exception;

/**
 * @author mryunqi
 * @date 2023/4/15
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String msg) {
        super(msg);
    }

    public UnauthorizedException() {
        super();
    }
}
