package com.chuqiyun.proxmoxveams.aop;

import com.chuqiyun.proxmoxveams.common.ResponseResult;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Objects;

/**
 * @author mryunqi
 * @date 2023/11/22
 */

@Aspect
@Component
@Order(0)
public class PermissionAdminPath {
    @Value("${config.admin_path}")
    private String ADMIN_PATH;
    @Pointcut("@annotation(com.chuqiyun.proxmoxveams.annotation.AdminApiCheck)")
    public void adminApiCheck() {
    }

    @Around("adminApiCheck()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 通过HttpServletRequest获取请求地址
        HttpServletRequest request = ((ServletRequestAttributes) Objects.requireNonNull(RequestContextHolder.getRequestAttributes())).getRequest();
        String requestURI = request.getRequestURI();
        // 筛选出第一个路径，如果没有或者不是adminPath，返回错误
        String[] split = requestURI.split("/");
        if (split.length < 2){
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
        // 获取第一个路径
        String adminPath = split[1];
        // 判断是否是adminPath
        if (!ADMIN_PATH.equals(adminPath)){
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }

        return joinPoint.proceed();
    }

}
