package com.chuqiyun.proxmoxveams.annotation;

import java.lang.annotation.*;

/**
 * @author mryunqi
 * @date 2023/4/17
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AdminApiCheck {
}
