package com.chuqiyun.proxmoxveams.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import com.chuqiyun.proxmoxveams.entity.SystemLog;
import com.chuqiyun.proxmoxveams.service.SystemLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @Author: 星禾
 * @Description: 系统日志管理接口
 * @DateTime: 2026/6/7 11:18
 */
@RestController
@RequestMapping("/{adminPath}")
public class SysLogController {
    @Resource
    private SystemLogService systemLogService;

    /**
     * @Author: 星禾
     * @Description: 分页获取系统日志
     * @DateTime: 2026/6/7 11:18
     */
    @AdminApiCheck
    @GetMapping("/getSystemLogs")
    public ResponseResult<Page<SystemLog>> getSystemLogs(
            @RequestParam(name = "page", defaultValue = "1") Integer page,
            @RequestParam(name = "size", defaultValue = "20") Integer size,
            @RequestParam(name = "type", defaultValue = "all") String type,
            @RequestParam(name = "level", defaultValue = "all") String level,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "requestId", required = false) String requestId,
            @RequestParam(name = "date", required = false) String date
    ) throws UnauthorizedException {
        return ResponseResult.ok(systemLogService.getSystemLogs(page, size, type, level, keyword, requestId, date));
    }
}
