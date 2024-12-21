package com.chuqiyun.proxmoxveams.controller.admin;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.entity.Sysuser;
import com.chuqiyun.proxmoxveams.service.SysuserService;
import com.chuqiyun.proxmoxveams.utils.EncryptUtil;
import com.chuqiyun.proxmoxveams.utils.JWTUtil;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.utils.UUIDUtil;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.util.Objects;

/**
 * @author mryunqi
 * @date 2023/4/15
 */
@RestController
@RequestMapping("/{adminPath}")
public class SysAccountController {
    @Value("${config.secret}")
    private String secret;
    @Resource
    private SysuserService sysuserService;
    /**
     * 管理员登录接口
     * @param param JSONObject
     * @return ResponseResult<String>
     */
    @PostMapping("/loginDo")
    public ResponseResult<String> loginDo(@RequestBody JSONObject param,
                                          HttpServletResponse response)
            throws UnauthorizedException {
        // 判断username是否同时为空
        if (StringUtils.isBlank(param.getString("username"))){
            return ResponseResult.fail(ResponseResult.RespCode.LOGIN_NO_ACCOUNT);
        }
        // 判断password是否为空
        if (StringUtils.isBlank(param.getString("password"))){
            return ResponseResult.fail(ResponseResult.RespCode.LOGIN_NO_ACCOUNT);
        }
        Sysuser sysuser = sysuserService.getSysuserByUsername(param.getString("username"));


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
        String jwtToken = JWTUtil.sign(sysuser.getUuid(),secret);
        /*Cookie cookie = new Cookie("token", jwtToken);
        cookie.setMaxAge(7200);
        response.addCookie(cookie);*/
        response.addHeader("Authorization", jwtToken);
        return ResponseResult.ok(jwtToken);
    }

    /**
    * @Author: mryunqi
    * @Description: 添加超管账号接口
    * @DateTime: 2023/4/16 11:48
    * @Params: param JSONObject
    * @Return ResponseResult<String>
    */
    @AdminApiCheck
    @PostMapping("/registerDo")
    public ResponseResult<String> registerDo(@RequestBody JSONObject param)
            throws UnauthorizedException {
        Sysuser sysuser = sysuserService.getSysuserByUsername(param.getString("username"));
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
        sysUser.setUuid(UUIDUtil.getUUIDByThreadString());
        if (sysuserService.save(sysUser)) {
            return ResponseResult.ok("添加管理账号成功！");
        } else {
            return ResponseResult.fail("添加管理账号失败！");
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 查询超管账号接口
    * @DateTime: 2023/8/5 9:28
    * @Params: page 页码 size 每页数量
    */
    @AdminApiCheck
    @GetMapping("/getSysuser")
    public ResponseResult<Page<Sysuser>> getSysuser(@RequestParam(name = "page",defaultValue = "1") Integer page,
                                           @RequestParam(name = "size",defaultValue = "20") Integer size)
            throws UnauthorizedException {
        return ResponseResult.ok(sysuserService.selectUserPage(page,size));
    }

    /**
    * @Author: mryunqi
    * @Description: 根据UUID查询账号
    * @DateTime: 2024/2/17 16:07
    */
    @AdminApiCheck
    @GetMapping("/getSysuserByUuid")
    public ResponseResult<Sysuser> getSysuserByUuid(@RequestParam(name = "uuid") String uuid)
            throws UnauthorizedException {
        return ResponseResult.ok(sysuserService.getSysuserByUuid(uuid));
    }

    /**
    * @Author: mryunqi
    * @Description: 修改超管账号接口
    * @DateTime: 2023/8/5 10:33
    * @Params: param JSONObject
    * @Return  ResponseResult<String>
    */
    @AdminApiCheck
    @PostMapping("/updateSysuser")
    public ResponseResult<String> updateSysuser(@RequestBody JSONObject param)
            throws UnauthorizedException {
        Sysuser sysuser = sysuserService.getById(param.getLong("id"));
        if (Objects.isNull(sysuser)){
            //判断用户是否存在
            return ResponseResult.fail(ResponseResult.RespCode.REG_EXISTING_USER);
        }
        // 判断密码是否为空
        if (StringUtils.isNotBlank(param.getString("password"))){
            sysuser.setPassword(EncryptUtil.md5(param.getString("password")));
        }
        sysuser.setUsername(param.getString("username"));
        sysuser.setEmail(param.getString("email"));
        sysuser.setPhone(param.getString("phone"));
        sysuser.setName(param.getString("name"));
        if (sysuserService.updateById(sysuser)) {
            return ResponseResult.ok("修改管理账号成功！");
        } else {
            return ResponseResult.fail("修改管理账号失败！");
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 删除超管账号接口
    * @DateTime: 2024/2/17 17:11
    */
    @AdminApiCheck
    @RequestMapping(value = "/deleteSysUserById/{id}",method = {RequestMethod.POST,RequestMethod.DELETE})
    public ResponseResult<String> deleteSysUserById(@PathVariable Long id)
            throws UnauthorizedException {
        if (sysuserService.removeById(id)) {
            return ResponseResult.ok("删除管理账号成功！");
        } else {
            return ResponseResult.fail("删除管理账号失败！");
        }
    }
}
