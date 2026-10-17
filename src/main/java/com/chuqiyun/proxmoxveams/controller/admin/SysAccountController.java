package com.chuqiyun.proxmoxveams.controller.admin;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import com.chuqiyun.proxmoxveams.entity.Sysuser;
import com.chuqiyun.proxmoxveams.service.SysuserService;
import com.chuqiyun.proxmoxveams.utils.EncryptUtil;
import com.chuqiyun.proxmoxveams.utils.UUIDUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Objects;

/**
 * @author mryunqi
 * @date 2023/4/15
 */
@RestController
@RequestMapping("/{adminPath}")
public class SysAccountController {
    @Resource
    private SysuserService sysuserService;

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
        Sysuser updates = new Sysuser();
        updates.setId(sysuser.getId());
        String encodedPassword = StringUtils.isNotBlank(param.getString("password"))
                ? EncryptUtil.md5(param.getString("password")) : null;
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

}
