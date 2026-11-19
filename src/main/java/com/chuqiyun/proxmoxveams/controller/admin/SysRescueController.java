package com.chuqiyun.proxmoxveams.controller.admin;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import com.chuqiyun.proxmoxveams.entity.RescueVmState;
import com.chuqiyun.proxmoxveams.service.RescueService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/** 管理员救援系统操作。 */
@RestController
@RequestMapping("/{adminPath}/rescue")
@Slf4j
public class SysRescueController {
    @Resource
    private RescueService rescueService;

    @AdminApiCheck
    @GetMapping("/images")
    public ResponseResult<Object> images() throws UnauthorizedException {
        return ResponseResult.ok(rescueService.listImages());
    }

    @AdminApiCheck
    @GetMapping("/status")
    public ResponseResult<Object> status(@RequestParam("hostId") Integer hostId) throws UnauthorizedException {
        if (hostId == null || hostId <= 0) {
            return ResponseResult.fail("hostId必须是正整数");
        }
        return ResponseResult.ok(rescueService.getState(hostId));
    }

    @AdminApiCheck
    @PostMapping("/enable")
    public ResponseResult<Object> enable(@RequestBody JSONObject params) throws UnauthorizedException {
        if (params == null || params.getInteger("hostId") == null
                || params.getInteger("hostId") <= 0) {
            return ResponseResult.fail("hostId必须是正整数");
        }
        try {
            RescueVmState state = rescueService.enable(params.getInteger("hostId"),
                    params.getString("imageId"), params.getString("storage"));
            return ResponseResult.ok(state);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseResult.fail(e.getMessage());
        } catch (RuntimeException e) {
            log.error("[Rescue] enable request failed: hostId={}", params.getInteger("hostId"), e);
            return ResponseResult.fail("救援任务初始化失败，请检查数据库迁移状态和节点配置");
        }
    }

    @AdminApiCheck
    @PostMapping("/disable")
    public ResponseResult<Object> disable(@RequestBody JSONObject params) throws UnauthorizedException {
        if (params == null || params.getInteger("hostId") == null
                || params.getInteger("hostId") <= 0) {
            return ResponseResult.fail("hostId必须是正整数");
        }
        try {
            RescueVmState state = rescueService.disable(params.getInteger("hostId"), params.getBoolean("start"));
            return ResponseResult.ok(state);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseResult.fail(e.getMessage());
        } catch (RuntimeException e) {
            log.error("[Rescue] disable request failed: hostId={}", params.getInteger("hostId"), e);
            return ResponseResult.fail("救援任务初始化失败，请检查数据库迁移状态和节点配置");
        }
    }
}
