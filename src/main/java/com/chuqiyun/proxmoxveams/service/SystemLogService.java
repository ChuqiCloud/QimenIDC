package com.chuqiyun.proxmoxveams.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chuqiyun.proxmoxveams.entity.SystemLog;

/**
 * @Author: 星禾
 * @Description: 系统日志服务接口
 * @DateTime: 2026/6/7 11:18
 */
public interface SystemLogService extends IService<SystemLog> {
    void saveSystemLogAsync(SystemLog systemLog);

    Page<SystemLog> getSystemLogs(Integer page,
                                  Integer size,
                                  String type,
                                  String level,
                                  String keyword,
                                  String requestId,
                                  String date);
}
