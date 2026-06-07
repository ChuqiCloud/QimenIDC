package com.chuqiyun.proxmoxveams.controller.admin;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
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

    private void writeLoginLog(HttpServletRequest request,
                               String username,
                               boolean success,
                               String reason,
                               Integer businessCode,
                               String businessMessage) {
        String content = buildLoginAuditContent(username, success, reason);
        saveLoginLogToDatabase(request, username, success, reason, businessCode, businessMessage, content);
    }

    private String buildLoginAuditContent(String username, boolean success, String reason) {
        StringBuilder builder = new StringBuilder();
        builder.append("LOGIN ")
                .append(success ? "SUCCESS" : "FAIL")
                .append(", username=")
                .append(StringUtils.defaultIfBlank(username, "-"));
        if (!success && StringUtils.isNotBlank(reason)) {
            builder.append(", reason=")
                    .append(StringUtils.abbreviate(reason, 200));
        }
        return builder.toString();
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
            return ResponseResult.ok("娣诲姞绠＄悊鍛樿处鎴锋垚鍔燂紒");
        } else {
            return ResponseResult.fail("娣诲姞绠＄悊鍛樿处鎴峰け璐ワ紒");
        }
    }

    @AdminApiCheck
    @GetMapping("/getSysuser")
    public ResponseResult<Page<Sysuser>> getSysuser(@RequestParam(name = "page", defaultValue = "1") Integer page,
                                                    @RequestParam(name = "size", defaultValue = "20") Integer size)
            throws UnauthorizedException {
        return ResponseResult.ok(sysuserService.selectUserPage(page, size));
    }

    @AdminApiCheck
    @GetMapping("/getSysuserByUuid")
    public ResponseResult<Sysuser> getSysuserByUuid(@RequestParam(name = "uuid") String uuid)
            throws UnauthorizedException {
        return ResponseResult.ok(sysuserService.getSysuserByUuid(uuid));
    }

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
            return ResponseResult.ok("淇敼绠＄悊鍛樿处鎴锋垚鍔燂紒");
        } else {
            return ResponseResult.fail("淇敼绠＄悊鍛樿处鎴峰け璐ワ紒");
        }
    }

    @AdminApiCheck
    @RequestMapping(value = "/deleteSysUserById/{id}", method = {RequestMethod.POST, RequestMethod.DELETE})
    public ResponseResult<String> deleteSysUserById(@PathVariable Long id)
            throws UnauthorizedException {
        if (sysuserService.removeById(id)) {
            return ResponseResult.ok("鍒犻櫎绠＄悊鍛樿处鎴锋垚鍔燂紒");
        } else {
            return ResponseResult.fail("鍒犻櫎绠＄悊鍛樿处鎴峰け璐ワ紒");
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
        systemLog.setBusinessMessage(success ? "SUCCESS" : "FAIL");
        systemLog.setException(success ? "" : StringUtils.defaultString(reason));
        systemLog.setContent(content);
        systemLog.setCreateTime(System.currentTimeMillis());
        systemLogService.saveSystemLogAsync(systemLog);
    }
}
