package com.chuqiyun.proxmoxveams.controller.admin;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.entity.Sysuser;
import com.chuqiyun.proxmoxveams.service.SysuserService;
import com.chuqiyun.proxmoxveams.utils.EncryptUtil;
import com.chuqiyun.proxmoxveams.utils.JWTUtil;
import com.chuqiyun.proxmoxveams.utils.ResponseResult;
import com.chuqiyun.proxmoxveams.utils.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.util.Objects;

/**
 * @author mryunqi
 * @date 2023/4/15
 */
@RestController
public class SysAccountController {
    @Value("${config.admin_path}")
    private String ADMIN_PATH;

    @Value("${config.secret}")
    private String secret;
    @Resource
    private SysuserService sysuserService;
    /**
     * 管理员登录接口
     * @param adminPath 自定义后台路径
     * @param param JSONObject
     * @return ResponseResult<String>
     */
    @PostMapping("/{adminPath}/loginDo")
    public ResponseResult<String> loginDo(@PathVariable("adminPath") String adminPath,
                                          @RequestBody JSONObject param,
                                          HttpServletResponse response)
            throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
        Sysuser sysuserPhone = sysuserService.getSysuser(param.getString("phone"));
        // 如果为用户名登录
        Sysuser sysuserName = sysuserService.getSysuser(param.getString("username"));

        // 判断哪个字段不为空，则赋值给sysuser
        Sysuser sysuser = Objects.isNull(sysuserPhone) ? sysuserName : sysuserPhone;

        if (Objects.isNull(sysuser)){
            //判断用户是否存在
            return ResponseResult.fail(ResponseResult.RespCode.LOGIN_NO_ACCOUNT);
        }else if (!sysuser.getPassword().equals(EncryptUtil.md5(param.getString("password")))){
            //判断用户密码是否正确
            return ResponseResult.fail(ResponseResult.RespCode.LOGIN_FAIL);
        }
        Long nowDate = System.currentTimeMillis();
        sysuser.setLogindate(nowDate);
        sysuser.updateById();
        String jwtToken = JWTUtil.sign(param.getString("phone"),secret);
        Cookie cookie = new Cookie("token", jwtToken);
        // 120秒失效
        cookie.setMaxAge(7200);
        response.addCookie(cookie);
        return ResponseResult.ok(jwtToken);
    }

    /**
    * @Author: mryunqi
    * @Description: 添加超管账号接口
    * @DateTime: 2023/4/16 11:48
    * @Params: adminPath 自定义后台路径
    * @Params: param JSONObject
    * @Return ResponseResult<String>
    */
    @AdminApiCheck
    @PostMapping("/{adminPath}/registerDo")
    public ResponseResult<String> registerDo(@PathVariable("adminPath") String adminPath,
                                             @RequestBody JSONObject param)
            throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
        Sysuser sysuser = sysuserService.getSysuser(param.getString("phone"));
        if (!Objects.isNull(sysuser)){
            //判断用户是否存在
            return ResponseResult.fail(ResponseResult.RespCode.REG_EXISTING_USER);
        }
        Sysuser sysUser = new Sysuser();
        sysUser.setUsername(param.getString("username"));
        sysUser.setPassword(EncryptUtil.md5(param.getString("password")));
        sysUser.setEmail(param.getString("email"));
        sysUser.setPhone(param.getString("phone"));
        sysUser.setName(param.getString("name"));
        if (sysuserService.save(sysUser)) {
            return ResponseResult.ok("添加管理账号成功！");
        } else {
            return ResponseResult.fail("添加管理账号失败！");
        }
    }
}
