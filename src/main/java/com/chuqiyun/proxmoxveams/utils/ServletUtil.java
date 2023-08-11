package com.chuqiyun.proxmoxveams.utils;

import com.chuqiyun.proxmoxveams.entity.Sysuser;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import java.util.Arrays;

import static com.chuqiyun.proxmoxveams.utils.JWTUtil.getUsername;


/**
 * @author mryunqi
 * @date 2023/1/11
 */
public class ServletUtil {
    /**
    * @Author: mryunqi
    * @Description: 解析出admin接口 token中的phone
    * @DateTime: 2023/4/17 22:51
    * @Params: HttpServletRequest request
    * @Return Sysuser
    */
    public static Sysuser getSysLoginMember(HttpServletRequest request,String secret) {
        String token = getCookie(request,"token");
        if (null == token) {
            return null;
        }
        String uuid = getUsername(token,secret);
        if (uuid == null){
            return null;
        }
        Sysuser member = new Sysuser();
        member.setUuid(uuid);
        return member;
    }


    /**
    * @Author: mryunqi
    * @Description: 获取cookie中的属性值
    * @DateTime: 2023/4/17 22:49
    * @Params: HttpServletRequest request
    * @Params: String var
    * @Return String Cookie
    */
    public static String getCookie(HttpServletRequest request, String var) {
        /*Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(var)) {
                    return cookie.getValue();
                }
            }
        }*/
        // 如果还没有则获取Authorization
        return request.getHeader("Authorization");
    }


}
