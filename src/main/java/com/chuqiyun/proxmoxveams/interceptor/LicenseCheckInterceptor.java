package com.chuqiyun.proxmoxveams.interceptor;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.annotation.AdminLoginCheck;
import com.chuqiyun.proxmoxveams.annotation.ApiCheck;
import com.chuqiyun.proxmoxveams.annotation.PublicSysApiCheck;
import com.chuqiyun.proxmoxveams.entity.Sysapi;
import com.chuqiyun.proxmoxveams.entity.Sysuser;
import com.chuqiyun.proxmoxveams.service.SysapiService;
import com.chuqiyun.proxmoxveams.service.SysuserService;
import com.chuqiyun.proxmoxveams.utils.JWTUtil;
import com.chuqiyun.proxmoxveams.utils.ServletUtil;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

import static com.chuqiyun.proxmoxveams.utils.Base64Decoder.decodeBase64;


/**
 * @author mryunqi
 * @date 2023/1/9
 */

@Component
public class LicenseCheckInterceptor implements HandlerInterceptor {

    @Resource
    private SysuserService sysuserService;
    @Resource
    private SysapiService sysapiService;

    private static String secret;
    private static String ADMIN_PATH;

    @Value("${config.secret}")
    public void setSecret(String secret){
        LicenseCheckInterceptor.secret = secret;
    }

    @Value("${config.admin_path}")
    public void setAdminPath(String adminPATH){
        LicenseCheckInterceptor.ADMIN_PATH = adminPATH;
    }

    @Override
    public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) throws Exception {
        // 自定义headers跨域支持
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "*");
        response.setHeader("Access-Control-Allow-Headers", "*");
        response.setHeader("Access-Control-Max-Age", "3600");
        // 如果是OPTIONS则结束请求
        if ("OPTIONS".equals(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return false;
        }
        //判断请求的方法上是否有注解
        boolean haveAnnotation = handler.getClass().isAssignableFrom(HandlerMethod.class);
        if (haveAnnotation){
            // 强转
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            // 获取方法
            Method method = handlerMethod.getMethod();
            if (method.isAnnotationPresent(AdminLoginCheck.class)) {
                //如果有注解，判断是否是AdminLoginCheck
                AdminLoginCheck ma = method.getAnnotation(AdminLoginCheck.class);
                //如果存在该注解
                if (null != ma) {
                    //获得名为member的对象
                    Sysuser members = ServletUtil.getSysLoginMember(request,secret);
                    if (null == members) {
                        //如果不是转发到/index上
                        response.sendRedirect("/"+ADMIN_PATH+"/login");
                        return false;
                    }
                    Sysuser sysuser = sysuserService.getSysuserByUuid(members.getUuid());
                    if (Objects.isNull(sysuser)){
                        response.sendRedirect("/"+ADMIN_PATH+"/login");
                        return false;
                    }

                    //对比登录时间与jwt的生成时间
                    Long jwtTokenDate = JWTUtil.getTokenData(ServletUtil.getCookie(request,"token"),secret);
                    if (jwtTokenDate == null){
                        jwtTokenDate = 0L;
                    }
                    //给予系统容错5秒误差
                    jwtTokenDate = jwtTokenDate - 5*1000;

                    Long sysUserDate = sysuser.getLogindate();
                    if (jwtTokenDate> sysUserDate){
                        response.sendRedirect("/"+ADMIN_PATH+"/login");
                        return false;
                    }

                }
            } else if (method.isAnnotationPresent(AdminApiCheck.class)) {
                AdminApiCheck apiCheck = method.getAnnotation(AdminApiCheck.class);

                //如果存在该注解
                if (null != apiCheck) {
                    //获得名为member的对象
                    Sysuser members = ServletUtil.getSysLoginMember(request,secret);
                    if (null == members){
                        return authError(response);
                    }

                    Sysuser sysuser = sysuserService.getSysuserByUuid(members.getUuid());
                    if (Objects.isNull(sysuser)){
                        return authError(response);
                    }
                    //对比登录时间与jwt的生成时间
                    Long jwtTokenDate = JWTUtil.getTokenData(ServletUtil.getCookie(request,"token"),secret);
                    if (jwtTokenDate == null){
                        jwtTokenDate = 0L;
                    }
                    //给予系统容错5秒误差
                    jwtTokenDate = jwtTokenDate - 5*1000;

                    Long sysUserDate = sysuser.getLogindate();
                    if (jwtTokenDate> sysUserDate){
                        return authError(response);
                    }else {
                        return true;
                    }
                }
            } else if(method.isAnnotationPresent(PublicSysApiCheck.class)){
                PublicSysApiCheck publicSysApiCheck = method.getAnnotation(PublicSysApiCheck.class);
                //如果存在该注解
                if(null != publicSysApiCheck){
                    //获取头部中的授权信息
                    String auth = request.getHeader("Authorization");
                    if (StringUtils.isEmpty(auth)) {
                        response.setStatus(401);
                        response.setHeader("WWW-Authenticate", "Basic realm=\"input AppId and AppKey\"");
                        return authError(response);
                    }
                    String[] idAndKey = decodeBase64(auth.split(" ")[1]).split(":");
                    Sysapi sysapi = sysapiService.getSysapi(idAndKey[0]);
                    if (null == sysapi){
                        return authError(response);
                    }
                    if (!sysapi.getAppkey().equals(idAndKey[1])){
                        return authError(response);
                    }else {
                        return true;
                    }
                }
            }else {
                return true;
            }
        }

        return invalidApi(response);
    }

    private boolean authError(@NotNull HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=utf-8");
        JSONObject obj = new JSONObject();
        obj.put("code", 501002);
        obj.put("message", "API鉴权失败");
        response.getWriter().print(obj);
        response.getWriter().flush();
        return false;
    }

    /**
     * 无效接口
     */
    private boolean invalidApi(@NotNull HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=utf-8");
        JSONObject obj = new JSONObject();
        obj.put("code", 501001);
        obj.put("message", "无效的API");
        response.getWriter().print(obj);
        response.getWriter().flush();
        return false;
    }
}
