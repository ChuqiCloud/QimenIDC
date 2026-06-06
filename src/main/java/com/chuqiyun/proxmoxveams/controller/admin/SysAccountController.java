package com.chuqiyun.proxmoxveams.controller.admin;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.UnifiedLogger;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import com.chuqiyun.proxmoxveams.entity.SystemLog;
import com.chuqiyun.proxmoxveams.entity.Sysuser;
import com.chuqiyun.proxmoxveams.service.SystemLogService;
import com.chuqiyun.proxmoxveams.service.SysuserService;
import com.chuqiyun.proxmoxveams.utils.EncryptUtil;
import com.chuqiyun.proxmoxveams.utils.JWTUtil;
import com.chuqiyun.proxmoxveams.utils.UUIDUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
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

    @Resource
    private SystemLogService systemLogService;

    /**
     * 管理员登录接口
     *
     * @param param JSONObject
     * @return ResponseResult<String>
     */
    @PostMapping("/loginDo")
    public ResponseResult<String> loginDo(@RequestBody JSONObject param,
                                          HttpServletRequest request,
                                          HttpServletResponse response)
            throws UnauthorizedException {
        String username = param.getString("username");
        String password = param.getString("password");
        if (StringUtils.isBlank(username)) {
            writeLoginLog(request, username, false, "username is blank",
                    ResponseResult.RespCode.LOGIN_NO_ACCOUNT.getCode(),
                    ResponseResult.RespCode.LOGIN_NO_ACCOUNT.getMessage());
            return ResponseResult.fail(ResponseResult.RespCode.LOGIN_NO_ACCOUNT);
        }
        if (StringUtils.isBlank(password)) {
            writeLoginLog(request, username, false, "password is blank",
                    ResponseResult.RespCode.LOGIN_NO_ACCOUNT.getCode(),
                    ResponseResult.RespCode.LOGIN_NO_ACCOUNT.getMessage());
            return ResponseResult.fail(ResponseResult.RespCode.LOGIN_NO_ACCOUNT);
        }
        Sysuser sysuser = sysuserService.getSysuserByUsername(username);
        if (Objects.isNull(sysuser)) {
            writeLoginLog(request, username, false, "account not found",
                    ResponseResult.RespCode.LOGIN_NO_ACCOUNT.getCode(),
                    ResponseResult.RespCode.LOGIN_NO_ACCOUNT.getMessage());
            return ResponseResult.fail(ResponseResult.RespCode.LOGIN_NO_ACCOUNT);
        }
        if (!sysuser.getPassword().equals(EncryptUtil.md5(password))) {
            writeLoginLog(request, username, false, "password incorrect",
                    ResponseResult.RespCode.LOGIN_FAIL.getCode(),
                    ResponseResult.RespCode.LOGIN_FAIL.getMessage());
            return ResponseResult.fail(ResponseResult.RespCode.LOGIN_FAIL);
        }
        Long nowDate = System.currentTimeMillis();
        sysuser.setLogindate(nowDate);
        sysuser.updateById();
        String jwtToken = JWTUtil.sign(sysuser.getUuid(), secret);
        response.addHeader("Authorization", jwtToken);
        writeLoginLog(request, sysuser.getUsername(), true, "login success",
                ResponseResult.RespCode.OK.getCode(),
                ResponseResult.RespCode.OK.getMessage());
        return ResponseResult.ok(jwtToken);
    }

    /**
     * @Author: 星禾
     * @Description: 记录管理员登录日志
     * @DateTime: 2026/6/7 11:25
     */
    private void writeLoginLog(HttpServletRequest request,
                               String username,
                               boolean success,
                               String reason,
                               Integer businessCode,
                               String businessMessage) {
        Map<String, Object> loginLog = new LinkedHashMap<>();
        loginLog.put("event", "login");
        loginLog.put("requestId", StringUtils.defaultIfBlank(MDC.get("requestId"), "-"));
        loginLog.put("username", StringUtils.defaultIfBlank(username, "-"));
        loginLog.put("clientIp", getClientIp(request));
        loginLog.put("success", success);
        loginLog.put("reason", reason);
        loginLog.put("businessCode", businessCode);
        loginLog.put("businessMessage", businessMessage);
        String content = JSON.toJSONString(loginLog);
        if (success) {
            UnifiedLogger.log(UnifiedLogger.LogType.LOGIN, "{}", content);
        } else {
            UnifiedLogger.warn(UnifiedLogger.LogType.LOGIN, "{}", content);
        }
        saveLoginLogToDatabase(request, username, success, reason, businessCode, businessMessage, content);
    }

    private String getClientIp(HttpServletRequest request) {
        String[] headerNames = {
                "X-Forwarded-For",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_CLIENT_IP",
                "HTTP_X_FORWARDED_FOR"
        };
        for (String headerName : headerNames) {
            String ip = request.getHeader(headerName);
            if (StringUtils.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
                return StringUtils.substringBefore(ip, ",").trim();
            }
        }
        return request.getRemoteAddr();
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
        if (!Objects.isNull(sysuser)) {
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
            return ResponseResult.ok("添加管理员账户成功！");
        } else {
            return ResponseResult.fail("添加管理员账户失败！");
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
    public ResponseResult<Page<Sysuser>> getSysuser(@RequestParam(name = "page", defaultValue = "1") Integer page,
                                                    @RequestParam(name = "size", defaultValue = "20") Integer size)
            throws UnauthorizedException {
        return ResponseResult.ok(sysuserService.selectUserPage(page, size));
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
        if (Objects.isNull(sysuser)) {
            return ResponseResult.fail(ResponseResult.RespCode.REG_EXISTING_USER);
        }
        if (StringUtils.isNotBlank(param.getString("password"))) {
            sysuser.setPassword(EncryptUtil.md5(param.getString("password")));
        }
        sysuser.setUsername(param.getString("username"));
        sysuser.setEmail(param.getString("email"));
        sysuser.setPhone(param.getString("phone"));
        sysuser.setName(param.getString("name"));
        if (sysuserService.updateById(sysuser)) {
            return ResponseResult.ok("修改管理员账户成功！");
        } else {
            return ResponseResult.fail("修改管理员账户失败！");
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 删除超管账号接口
    * @DateTime: 2024/2/17 17:11
    */
    @AdminApiCheck
    @RequestMapping(value = "/deleteSysUserById/{id}", method = {RequestMethod.POST, RequestMethod.DELETE})
    public ResponseResult<String> deleteSysUserById(@PathVariable Long id)
            throws UnauthorizedException {
        if (sysuserService.removeById(id)) {
            return ResponseResult.ok("删除管理员账户成功！");
        } else {
            return ResponseResult.fail("删除管理员账户失败！");
        }
    }

    private void saveLoginLogToDatabase(HttpServletRequest request,
                                        String username,
                                        boolean success,
                                        String reason,
                                        Integer businessCode,
                                        String businessMessage,
                                        String content) {
        SystemLog systemLog = new SystemLog();
        systemLog.setRequestId(StringUtils.defaultIfBlank(MDC.get("requestId"), "-"));
        systemLog.setLogType("LOGIN");
        systemLog.setLevel(success ? "INFO" : "WARN");
        systemLog.setMethod(request.getMethod());
        systemLog.setUri(request.getRequestURI());
        systemLog.setClientIp(getClientIp(request));
        systemLog.setOperator("admin:" + StringUtils.defaultIfBlank(username, "-"));
        systemLog.setAuthType("ANONYMOUS");
        systemLog.setHttpStatus(200);
        systemLog.setBusinessCode(businessCode);
        systemLog.setBusinessMessage(businessMessage);
        systemLog.setException(success ? "" : reason);
        systemLog.setContent(content);
        systemLog.setCreateTime(System.currentTimeMillis());
        systemLogService.saveSystemLogAsync(systemLog);
    }
}
