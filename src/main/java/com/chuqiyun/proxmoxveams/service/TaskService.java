package com.chuqiyun.proxmoxveams.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chuqiyun.proxmoxveams.dto.TaskCountDto;
import com.chuqiyun.proxmoxveams.dto.TaskItemDto;
import com.chuqiyun.proxmoxveams.dto.TaskQueryDto;
import com.chuqiyun.proxmoxveams.entity.Task;

import java.util.List;
import java.util.Map;

/**
 * (Task)表服务接口
 *
 * @author mryunqi
 * @since 2023-06-29 22:38:31
 */
public interface TaskService extends IService<Task> {

    boolean insertTask(Task task);

    boolean deleteTask(Integer id);

    boolean updateTask(Task task);

    Page<Task> getTaskList(Integer page, Integer size);

    Page<Task> getTaskList(Integer page, Integer size, QueryWrapper<Task> queryWrapper);

    /**
     * 管理端任务列表，附带节点名、虚拟机名、类型名、状态名、耗时等可读信息。
     */
    TaskQueryDto getTaskPageForAdmin(TaskQueryDto query);

    /**
     * 管理端任务详情，任务不存在时返回 null。
     */
    TaskItemDto getTaskDetailForAdmin(Integer id);

    /**
     * 管理端任务计数，按任务类型或状态聚合，可选按创建时间范围过滤。
     */
    List<TaskCountDto> getTaskStatisticsForAdmin(String groupBy, Long startDate, Long endDate);

    /**
     * 执行中与等待中的任务数量（含执行中任务按类型拆分的明细）。
     */
    Map<String, Object> getRunningTaskSummary();
}

