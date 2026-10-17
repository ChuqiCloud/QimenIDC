package com.chuqiyun.proxmoxveams.controller.admin;

import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import com.chuqiyun.proxmoxveams.dto.TaskItemDto;
import com.chuqiyun.proxmoxveams.dto.TaskQueryDto;
import com.chuqiyun.proxmoxveams.service.TaskService;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 管理端任务查询接口。
 *
 * <p>任务表里混着虚拟机创建、重装、迁移等异步流程，此前只有迁移任务有查询入口，
 * 其余任务只能翻数据库。这里按管理员视角补齐列表、详情和汇总，参数中的凭据不会输出。</p>
 *
 * @author CodeBuddy
 * @date 2026/10/17
 */
@RestController
@RequestMapping("/{adminPath}")
public class SysTaskController {
    @Resource
    private TaskService taskService;

    @ApiOperation(value = "分页查询任务", notes = "按节点、任务类型、状态、虚拟机与创建时间筛选，按创建顺序倒序返回")
    @AdminApiCheck
    @GetMapping(value = "/getTaskByPage")
    public ResponseResult<Object> getTaskByPage(
            @ApiParam("页码，从1开始") @RequestParam(name = "page", defaultValue = "1") Integer page,
            @ApiParam("每页数量，最大200") @RequestParam(name = "size", defaultValue = "20") Integer size,
            @ApiParam("节点ID") @RequestParam(name = "nodeid", required = false) Integer nodeid,
            @ApiParam("虚拟机记录ID") @RequestParam(name = "hostid", required = false) Integer hostid,
            @ApiParam("PVE虚拟机ID") @RequestParam(name = "vmid", required = false) Integer vmid,
            @ApiParam("任务类型，多个用逗号分隔") @RequestParam(name = "type", required = false) String type,
            @ApiParam("任务状态，多个用逗号分隔") @RequestParam(name = "status", required = false) String status,
            @ApiParam("创建时间起（毫秒时间戳）") @RequestParam(name = "startDate", required = false) Long startDate,
            @ApiParam("创建时间止（毫秒时间戳）") @RequestParam(name = "endDate", required = false) Long endDate
    ) throws UnauthorizedException {
        TaskQueryDto query = new TaskQueryDto();
        query.setPage(page == null ? 1L : page.longValue());
        query.setSize(size == null ? 20L : size.longValue());
        query.setNodeid(nodeid);
        query.setHostid(hostid);
        query.setVmid(vmid);
        query.setType(type);
        query.setStatus(status);
        query.setStartDate(startDate);
        query.setEndDate(endDate);
        return ResponseResult.ok(taskService.getTaskPageForAdmin(query));
    }

    @ApiOperation(value = "查询任务详情", notes = "返回任务状态、进度、耗时、失败原因与脱敏后的参数")
    @AdminApiCheck
    @GetMapping(value = "/getTaskDetail/{id}")
    public ResponseResult<Object> getTaskDetail(@PathVariable("id") Integer id) throws UnauthorizedException {
        TaskItemDto task = taskService.getTaskDetailForAdmin(id);
        if (task == null) {
            return ResponseResult.fail("任务不存在");
        }
        return ResponseResult.ok(task);
    }

    @ApiOperation(value = "任务数量统计", notes = "按任务类型（默认）或状态聚合，可按创建时间范围过滤")
    @AdminApiCheck
    @GetMapping(value = "/getTaskStatistics")
    public ResponseResult<Object> getTaskStatistics(
            @ApiParam("聚合维度：type 或 status") @RequestParam(name = "groupBy", defaultValue = "type") String groupBy,
            @ApiParam("创建时间起（毫秒时间戳）") @RequestParam(name = "startDate", required = false) Long startDate,
            @ApiParam("创建时间止（毫秒时间戳）") @RequestParam(name = "endDate", required = false) Long endDate
    ) throws UnauthorizedException {
        return ResponseResult.ok(taskService.getTaskStatisticsForAdmin(groupBy, startDate, endDate));
    }

    @ApiOperation(value = "执行中任务概览", notes = "等待与执行中的任务数量，执行中按任务类型拆分")
    @AdminApiCheck
    @GetMapping(value = "/getRunningTaskSummary")
    public ResponseResult<Object> getRunningTaskSummary() throws UnauthorizedException {
        return ResponseResult.ok(taskService.getRunningTaskSummary());
    }
}
