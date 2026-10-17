package com.chuqiyun.proxmoxveams.controller.admin;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.common.AuthException;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.dto.LoginResult;
import com.chuqiyun.proxmoxveams.entity.SystemLog;
import com.chuqiyun.proxmoxveams.service.AuthService;
import com.chuqiyun.proxmoxveams.service.SystemLogService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @Author: 星禾
 * @Description: 管理员登录（密码 + TOTP / Passkey 二步验证）
 * @DateTime: 2026/10/17 16:40
 */
@RestController
@RequestMapping("/{adminPath}")
public class SysLoginController {

    @Resource
    private AuthService authService;
    @Resource
    private SystemLogService systemLogService;

    /**
     * 账号密码登录。启用二步验证时返回 challenge，不下发 token
     */
    @PostMapping("/loginDo")
    public ResponseResult<Object> loginDo(@RequestBody JSONObject param,
                                          HttpServletRequest request,
                                          HttpServletResponse response) {
        String username = param.getString("username");
        try {
            LoginResult result = authService.loginWithPassword(username, param.getString("password"));
            if (!result.isCompleted()) {
                writeLoginLog(request, username, true, "password ok, mfa required: " + result.getMethod(), "MFA_CHALLENGE");
                return ResponseResult.ok(buildChallengeBody(result));
            }
            response.addHeader("Authorization", result.getToken());
            writeLoginLog(request, username, true, "login success", "SUCCESS");
            return ResponseResult.ok(buildTokenBody(result.getToken()));
        } catch (AuthException e) {
            writeLoginLog(request, username, false, e.getMessage(), "FAIL");
            throw e;
        }
    }

    /**
     * 二步验证：TOTP 动态码
     */
    @PostMapping("/login/totp")
    public ResponseResult<Object> loginWithTotp(@RequestBody JSONObject param,
                                                HttpServletRequest request,
                                                HttpServletResponse response) {
        try {
            String token = authService.loginWithTotp(param.getString("ticket"), param.getString("code"));
            response.addHeader("Authorization", token);
            writeLoginLog(request, null, true, "totp login success", "SUCCESS");
            return ResponseResult.ok(buildTokenBody(token));
        } catch (AuthException e) {
            writeLoginLog(request, null, false, e.getMessage(), "FAIL");
            throw e;
        }
    }

    /**
     * 二步验证：Passkey 挑战
     */
    @PostMapping("/login/passkey/challenge")
    public ResponseResult<Object> passkeyLoginChallenge(@RequestBody JSONObject param) {
        return ResponseResult.ok(authService.passkeyLoginChallenge(param.getString("ticket")));
    }

    /**
     * 二步验证：提交 Passkey 断言
     */
    @PostMapping("/login/passkey")
    public ResponseResult<Object> loginWithPasskey(@RequestBody JSONObject param,
                                                   HttpServletRequest request,
                                                   HttpServletResponse response) {
        try {
            String token = authService.loginWithPasskey(param.getString("ticket"), param.getJSONObject("credential"));
            response.addHeader("Authorization", token);
            writeLoginLog(request, null, true, "passkey mfa login success", "SUCCESS");
            return ResponseResult.ok(buildTokenBody(token));
        } catch (AuthException e) {
            writeLoginLog(request, null, false, e.getMessage(), "FAIL");
            throw e;
        }
    }

    /**
     * Passkey 直接登录：挑战，username 可选（支持无用户名登录）
     */
    @PostMapping("/login/passkey/direct/challenge")
    public ResponseResult<Object> passkeyDirectChallenge(@RequestBody(required = false) JSONObject param) {
        String username = param == null ? null : param.getString("username");
        return ResponseResult.ok(authService.passkeyDirectChallenge(username));
    }

    /**
     * Passkey 直接登录：提交断言，返回 token 或 TOTP 二步验证挑战
     */
    @PostMapping("/login/passkey/direct")
    public ResponseResult<Object> loginWithPasskeyDirect(@RequestBody JSONObject param,
                                                         HttpServletRequest request,
                                                         HttpServletResponse response) {
        String username = param.getString("username");
        try {
            LoginResult result = authService.loginWithPasskeyDirect(username, param.getJSONObject("credential"));
            if (!result.isCompleted()) {
                writeLoginLog(request, username, true, "passkey ok, totp required", "MFA_CHALLENGE");
                return ResponseResult.ok(buildChallengeBody(result));
            }
            response.addHeader("Authorization", result.getToken());
            writeLoginLog(request, username, true, "passkey login success", "SUCCESS");
            return ResponseResult.ok(buildTokenBody(result.getToken()));
        } catch (AuthException e) {
            writeLoginLog(request, username, false, e.getMessage(), "FAIL");
            throw e;
        }
    }

    private Map<String, Object> buildTokenBody(String token) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", token);
        body.put("mfaRequired", false);
        return body;
    }

    private Map<String, Object> buildChallengeBody(LoginResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", null);
        body.put("mfaRequired", true);
        body.put("method", result.getMethod());
        body.put("ticket", result.getTicket());
        return body;
    }

    private void writeLoginLog(HttpServletRequest request, String username, boolean success,
                               String reason, String businessMessage) {
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
        systemLog.setBusinessCode(success ? ResponseResult.RespCode.OK.getCode()
                : ResponseResult.RespCode.LOGIN_FAIL.getCode());
        systemLog.setBusinessMessage(businessMessage);
        systemLog.setException(success ? "" : StringUtils.defaultString(reason));
        systemLog.setContent("LOGIN " + (success ? "SUCCESS" : "FAIL")
                + ", username=" + StringUtils.defaultIfBlank(username, "-")
                + (success ? "" : ", reason=" + StringUtils.abbreviate(StringUtils.defaultString(reason), 200)));
        systemLog.setCreateTime(System.currentTimeMillis());
        systemLogService.saveSystemLogAsync(systemLog);
    }

    private String getClientIp(HttpServletRequest request) {
        String[] headerNames = {"X-Forwarded-For", "Proxy-Client-IP", "WL-Proxy-Client-IP",
                "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR"};
        for (String headerName : headerNames) {
            String ip = request.getHeader(headerName);
            if (StringUtils.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
                return StringUtils.substringBefore(ip, ",").trim();
            }
        }
        return request.getRemoteAddr();
    }
}
