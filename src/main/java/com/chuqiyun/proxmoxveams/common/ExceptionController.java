package com.chuqiyun.proxmoxveams.common;

import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.shiro.ShiroException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * @author mryunqi
 * @date 2023/4/16
 */
@Slf4j
@RestControllerAdvice
public class ExceptionController {

    // 捕捉shiro的异常
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(ShiroException.class)
    public ResponseResult<String> handle401(ShiroException e) {
        return ResponseResult.fail(e.getMessage());
    }

    // 捕捉UnauthorizedException
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseResult<String> handle401() {
        return ResponseResult.fail(ResponseResult.RespCode.UNAUTHORIZED);
    }

    /**
     * @Author: 星禾
     * @Description: 忽略客户端主动断开连接异常
     * @DateTime: 2026/5/29 23:43
     */
    @ExceptionHandler(ClientAbortException.class)
    public void handleClientAbortException(ClientAbortException e) {
        log.debug("客户端已断开连接: {}", e.getMessage());
    }

    // 捕捉其他所有异常
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseResult<String> globalException(HttpServletRequest request, Throwable ex) {
        if (isClientAbortException(ex)) {
            log.debug("客户端已断开连接: {}", ex.getMessage());
            return null;
        }
        return new ResponseResult<>(getStatus(request).value(), ex.getMessage(), null);
    }

    private boolean isClientAbortException(Throwable ex) {
        Throwable throwable = ex;
        while (throwable != null) {
            if (throwable instanceof ClientAbortException) {
                return true;
            }
            if (throwable instanceof IOException && throwable.getMessage() != null) {
                String message = throwable.getMessage().toLowerCase();
                if (message.contains("broken pipe") || message.contains("connection reset")) {
                    return true;
                }
            }
            throwable = throwable.getCause();
        }
        return false;
    }

    private HttpStatus getStatus(HttpServletRequest request) {
        Integer statusCode = (Integer) request.getAttribute("javax.servlet.error.status_code");
        if (statusCode == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return HttpStatus.valueOf(statusCode);
    }
}
