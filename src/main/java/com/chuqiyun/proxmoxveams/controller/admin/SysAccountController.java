package com.chuqiyun.proxmoxveams.controller.admin;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.common.AuthException;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import com.chuqiyun.proxmoxveams.entity.Sysuser;
import com.chuqiyun.proxmoxveams.security.PasswordPolicy;
import com.chuqiyun.proxmoxveams.service.SysuserMfaService;
import com.chuqiyun.proxmoxveams.service.SysuserService;
import com.chuqiyun.proxmoxveams.utils.EncryptUtil;
import com.chuqiyun.proxmoxveams.utils.ServletUtil;
import com.chuqiyun.proxmoxveams.utils.UUIDUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
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
    private SysuserMfaService sysuserMfaService;

    @AdminApiCheck
    @PostMapping("/registerDo")
    public ResponseResult<String> registerDo(@RequestBody JSONObject param)
            throws UnauthorizedException {
        Sysuser sysuser = sysuserService.getSysuserByUsername(param.getString("username"));
        if (!Objects.isNull(sysuser)) {
            return ResponseResult.fail(ResponseResult.RespCode.REG_EXISTING_USER);
        }
        String password = param.getString("password");
        String passwordError = PasswordPolicy.validate(password);
        if (passwordError != null) {
            return ResponseResult.fail(ResponseResult.RespCode.VALID_DATA_ERROR, passwordError);
        }
        Sysuser sysUser = new Sysuser();
        sysUser.setUsername(param.getString("username"));
        sysUser.setPassword(EncryptUtil.md5(password));
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
        Sysuser updates = new Sysuser();
        updates.setId(sysuser.getId());
        String rawPassword = param.getString("password");
        if (StringUtils.isNotBlank(rawPassword)) {
            String passwordError = PasswordPolicy.validate(rawPassword);
            if (passwordError != null) {
                return ResponseResult.fail(ResponseResult.RespCode.VALID_DATA_ERROR, passwordError);
            }
        }
        String encodedPassword = StringUtils.isNotBlank(rawPassword)
                ? EncryptUtil.md5(rawPassword) : null;
        updates.setUsername(param.getString("username"));
        updates.setEmail(param.getString("email"));
        updates.setPhone(param.getString("phone"));
        updates.setName(param.getString("name"));
        if (sysuserService.updateAccountDetails(updates, encodedPassword)) {
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
            return ResponseResult.ok("删除管理员账户成功！");
        } else {
            return ResponseResult.fail("删除管理员账户失败！");
        }
    }

    /** 管理员清除指定账号的 TOTP 绑定，账号需要重新绑定。 */
    @AdminApiCheck
    @PostMapping("/resetSysuserTotp")
    public ResponseResult<String> resetSysuserTotp(@RequestBody JSONObject param, HttpServletRequest request) {
        Integer userId = resetUserId(param);
        if (userId == null) {
            return ResponseResult.fail(ResponseResult.RespCode.DATA_ERROR, "id 必须为正整数用户 ID");
        }
        if (!verifyResetOperator(request, param)) {
            return ResponseResult.fail(ResponseResult.RespCode.OAUTH_PASSWORD_FAIL, "当前管理员密码错误，无法重置");
        }
        return sysuserMfaService.resetTotp(userId)
                ? ResponseResult.ok("TOTP 已重置，该用户需重新登录并绑定")
                : ResponseResult.fail(ResponseResult.RespCode.DATA_ERROR, "用户不存在");
    }

    /** 管理员清除指定账号的全部 Passkey，不影响其它账号的凭据。 */
    @AdminApiCheck
    @PostMapping("/resetSysuserPasskey")
    public ResponseResult<String> resetSysuserPasskey(@RequestBody JSONObject param, HttpServletRequest request) {
        Integer userId = resetUserId(param);
        if (userId == null) {
            return ResponseResult.fail(ResponseResult.RespCode.DATA_ERROR, "id 必须为正整数用户 ID");
        }
        if (!verifyResetOperator(request, param)) {
            return ResponseResult.fail(ResponseResult.RespCode.OAUTH_PASSWORD_FAIL, "当前管理员密码错误，无法重置");
        }
        return sysuserMfaService.resetPasskey(userId)
                ? ResponseResult.ok("Passkey 已重置，该用户需重新登录并绑定")
                : ResponseResult.fail(ResponseResult.RespCode.DATA_ERROR, "用户不存在");
    }

    private boolean verifyResetOperator(HttpServletRequest request, JSONObject param) {
        Sysuser member = ServletUtil.getSysLoginMember(request, secret);
        Sysuser operator = member == null ? null : sysuserService.getSysuserByUuid(member.getUuid());
        if (operator == null) {
            throw new AuthException(ResponseResult.RespCode.UNAUTHORIZED);
        }
        return sysuserService.verifyPassword(operator.getId(), param.getString("password"));
    }

    private Integer resetUserId(JSONObject param) {
        String id = param == null ? null : param.getString("id");
        if (id == null || !id.matches("[1-9][0-9]*")) {
            return null;
        }
        try {
            return Integer.valueOf(id);
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
