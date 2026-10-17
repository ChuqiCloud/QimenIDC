package com.chuqiyun.proxmoxveams.service;

import com.chuqiyun.proxmoxveams.dto.VmMigrationBatchDto;
import com.chuqiyun.proxmoxveams.dto.VmMigrationLogView;
import com.chuqiyun.proxmoxveams.dto.VmMigrationParams;
import com.chuqiyun.proxmoxveams.dto.VmMigrationTaskView;
import com.chuqiyun.proxmoxveams.entity.Task;

import java.util.List;
import java.util.Map;

/**
 * 虚拟机离线迁移：任务排队、断点续跑与过程日志。
 */
public interface VmMigrationService {

    /**
     * 提交单台或批量迁移，按提交顺序入队，由调度器逐个执行。
     *
     * @return 每台虚拟机的受理结果
     */
    VmMigrationBatchDto submit(VmMigrationParams params);

    /**
     * 迁移任务列表，供前端刷新后恢复进度展示。
     */
    List<VmMigrationTaskView> listTasks(Integer taskId, Integer hostId, String batchId, Integer page, Integer size);

    /**
     * 任务详情，包含当前阶段、进度与可重试步骤。
     */
    VmMigrationTaskView getTaskView(Integer taskId);

    /**
     * 过程日志：offset 为空时返回尾部日志，否则按偏移增量返回。
     */
    VmMigrationLogView getTaskLog(Integer taskId, Long offset);

    /**
     * 从最近失败的一步继续后续阶段，不重新打包。
     */
    Task resume(Integer taskId);

    /**
     * 放弃排队中的迁移任务。
     */
    boolean cancel(Integer taskId);

    /**
     * 当前是否有迁移任务在跑，供调度器做单并发控制。
     */
    boolean hasRunningTask();

    /**
     * 追加一条主控侧迁移日志，最多保留最近若干行。
     */
    void appendTaskLog(Task task, Map<Object, Object> params, String message);
}
