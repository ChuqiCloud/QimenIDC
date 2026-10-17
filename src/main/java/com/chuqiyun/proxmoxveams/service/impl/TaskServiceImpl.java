package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.constant.TaskStatus;
import com.chuqiyun.proxmoxveams.constant.TaskStatusIdToName;
import com.chuqiyun.proxmoxveams.constant.TaskTypeIdToName;
import com.chuqiyun.proxmoxveams.dao.TaskDao;
import com.chuqiyun.proxmoxveams.dto.TaskCountDto;
import com.chuqiyun.proxmoxveams.dto.TaskItemDto;
import com.chuqiyun.proxmoxveams.dto.TaskQueryDto;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.TaskService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * (Task)表服务实现类
 *
 * @author mryunqi
 * @since 2023-06-29 22:38:31
 */
@Service("taskService")
public class TaskServiceImpl extends ServiceImpl<TaskDao, Task> implements TaskService {
    /** 单页最大条数，避免管理端一次性拉全表。 */
    private static final int MAX_PAGE_SIZE = 200;
    /** 关联名称每次最多的 IN 查询数量。 */
    private static final int RELATION_BATCH_SIZE = 500;

    /** 使用 @Lazy 打断 TaskServiceImpl 与业务服务之间的构造期循环依赖。 */
    @Lazy
    @Resource
    private MasterService masterService;
    @Lazy
    @Resource
    private VmhostService vmhostService;

    /**
     * @Author: mryunqi
     * @Description: 插入任务
     * @DateTime: 2023/6/29 22:41
     * @Params: Task task 任务
     * @Return  boolean
     */
    @Override
    public boolean insertTask(Task task) {
        return this.save(task);
    }

    /**
     * @Author: mryunqi
     * @Description: 删除任务
     * @DateTime: 2023/6/30 22:39
     * @Params: Integer id 任务id
     * @Return boolean
     */
    @Override
    public boolean deleteTask(Integer id) {
        return this.removeById(id);
    }

    /**
    * @Author: mryunqi
    * @Description: 更新任务
    * @DateTime: 2023/6/30 22:43
    * @Params: Task task 任务
    * @Return boolean
    */
    @Override
    public boolean updateTask(Task task) {
        return this.updateById(task);
    }

    /**
    * @Author: mryunqi
    * @Description: 分页获取任务列表
    * @DateTime: 2023/6/30 22:44
    * @Params: Integer page 页码 Integer size 每页数量
    * @Return Page<Task>
    */
    @Override
    public Page<Task> getTaskList(Integer page, Integer size) {
        Page<Task> taskPage = new Page<>(page, size);
        return this.page(taskPage);
    }

    /**
    * @Author: mryunqi
    * @Description: 分页获取任务列表带查询条件
    * @DateTime: 2023/6/30 22:45
    * @Params: Integer page 页码 Integer size 每页数量 QueryWrapper<Task> queryWrapper 查询条件
    * @Return Page<Task>
    */
    @Override
    public Page<Task> getTaskList(Integer page, Integer size, QueryWrapper<Task> queryWrapper) {
        Page<Task> taskPage = new Page<>(page, size);
        return this.page(taskPage,queryWrapper);
    }

    @Override
    public TaskQueryDto getTaskPageForAdmin(TaskQueryDto query) {
        TaskQueryDto condition = query == null ? new TaskQueryDto() : query;
        long page = condition.getPage() == null || condition.getPage() < 1 ? 1 : condition.getPage();
        long size = condition.getSize() == null || condition.getSize() < 1 ? 20 : Math.min(condition.getSize(), MAX_PAGE_SIZE);
        Page<Task> taskPage = this.page(new Page<>(page, size), buildTaskQueryWrapper(condition));

        TaskQueryDto result = new TaskQueryDto();
        result.setTotal(taskPage.getTotal());
        result.setPage(taskPage.getCurrent());
        result.setSize(taskPage.getSize());
        result.setPages(taskPage.getPages());
        result.setRecords(toTaskItemDtos(taskPage.getRecords()));
        return result;
    }

    @Override
    public TaskItemDto getTaskDetailForAdmin(Integer id) {
        if (id == null) {
            return null;
        }
        Task task = this.getById(id);
        if (task == null) {
            return null;
        }
        return toTaskItemDtos(Collections.singletonList(task)).get(0);
    }

    @Override
    public List<TaskCountDto> getTaskStatisticsForAdmin(String groupBy, Long startDate, Long endDate) {
        boolean groupByStatus = "status".equalsIgnoreCase(StringUtils.trimToEmpty(groupBy));
        String column = groupByStatus ? "status" : "type";
        QueryWrapper<Task> wrapper = new QueryWrapper<>();
        wrapper.select(column + " as group_key", "count(*) as total");
        applyDateRange(wrapper, startDate, endDate);

        Map<Integer, Long> counter = new LinkedHashMap<>();
        for (Map<String, Object> row : this.listMaps(wrapper)) {
            Integer key = row.get("group_key") == null ? null : Integer.valueOf(String.valueOf(row.get("group_key")));
            counter.merge(key, toLong(row.get("total")), Long::sum);
        }
        List<TaskCountDto> statistics = new ArrayList<>(counter.size());
        counter.forEach((key, count) -> statistics.add(groupByStatus
                ? new TaskCountDto(null, null, key, TaskStatusIdToName.getTaskStatusName(key), count)
                : new TaskCountDto(key, TaskTypeIdToName.getTaskTypeName(key), null, null, count)));
        return statistics;
    }

    @Override
    public Map<String, Object> getRunningTaskSummary() {
        Map<Integer, Long> runningByType = new LinkedHashMap<>();
        QueryWrapper<Task> runningWrapper = new QueryWrapper<>();
        runningWrapper.select("type as group_key", "count(*) as total");
        runningWrapper.eq("status", TaskStatus.RUNNING);
        for (Map<String, Object> row : this.listMaps(runningWrapper)) {
            Integer type = row.get("group_key") == null ? null : Integer.valueOf(String.valueOf(row.get("group_key")));
            runningByType.merge(type, toLong(row.get("total")), Long::sum);
        }

        List<TaskCountDto> runningDetail = new ArrayList<>(runningByType.size());
        runningByType.forEach((type, count) -> runningDetail.add(
                new TaskCountDto(type, TaskTypeIdToName.getTaskTypeName(type), TaskStatus.RUNNING,
                        TaskStatusIdToName.getTaskStatusName(TaskStatus.RUNNING), count)));

        Map<String, Object> summary = new HashMap<>();
        summary.put("waitingCount", this.count(new QueryWrapper<Task>().eq("status", TaskStatus.WAITING)));
        summary.put("runningCount", runningByType.values().stream().mapToLong(Long::longValue).sum());
        summary.put("runningByType", runningDetail);
        return summary;
    }

    /**
     * 管理端列表条件：节点、任务类型（支持逗号分隔）、状态（支持逗号分隔）、虚拟机、时间范围。
     */
    private QueryWrapper<Task> buildTaskQueryWrapper(TaskQueryDto query) {
        QueryWrapper<Task> wrapper = new QueryWrapper<>();
        if (query.getNodeid() != null) {
            wrapper.eq("nodeid", query.getNodeid());
        }
        if (query.getHostid() != null) {
            wrapper.eq("hostid", query.getHostid());
        }
        if (query.getVmid() != null) {
            wrapper.eq("vmid", query.getVmid());
        }
        applyInCondition(wrapper, "type", query.getType());
        applyInCondition(wrapper, "status", query.getStatus());
        applyDateRange(wrapper, query.getStartDate(), query.getEndDate());
        wrapper.orderByDesc("id");
        return wrapper;
    }

    private void applyInCondition(QueryWrapper<Task> wrapper, String column, String values) {
        if (StringUtils.isBlank(values)) {
            return;
        }
        Set<Integer> parsed = new LinkedHashSet<>();
        for (String item : values.split(",")) {
            String trimmed = item.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                parsed.add(Integer.valueOf(trimmed));
            } catch (NumberFormatException ignored) {
                // 非法筛选值直接忽略，避免把 500 抛给管理端
            }
        }
        if (!parsed.isEmpty()) {
            wrapper.in(column, parsed);
        }
    }

    private void applyDateRange(QueryWrapper<Task> wrapper, Long startDate, Long endDate) {
        if (startDate != null) {
            wrapper.apply("create_date >= {0}", String.valueOf(startDate));
        }
        if (endDate != null) {
            wrapper.apply("create_date <= {0}", String.valueOf(endDate));
        }
    }

    /**
     * 批量补齐节点名与虚拟机名，避免逐条查询。
     */
    private List<TaskItemDto> toTaskItemDtos(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Integer, String> nodeNames = loadNodeNames(tasks);
        Map<Integer, String> hostNames = loadHostNames(tasks);
        long now = System.currentTimeMillis();

        List<TaskItemDto> records = new ArrayList<>(tasks.size());
        for (Task task : tasks) {
            if (task == null) {
                continue;
            }
            TaskItemDto item = new TaskItemDto();
            item.setId(task.getId());
            item.setNodeid(task.getNodeid());
            item.setNodeName(nodeNames.get(task.getNodeid()));
            item.setHostid(task.getHostid());
            item.setVmid(task.getVmid());
            item.setHostname(hostNames.get(task.getHostid()));
            item.setType(task.getType());
            item.setTypeName(TaskTypeIdToName.getTaskTypeName(task.getType()));
            item.setStatus(task.getStatus());
            item.setStatusName(TaskStatusIdToName.getTaskStatusName(task.getStatus()));
            item.setError(task.getError());
            item.setCreateDate(task.getCreateDate());
            item.setParams(task.getParamsForApi());
            fillProgress(item, task);
            records.add(item);
        }
        return records;
    }

    /**
     * 进度与耗时都来自任务参数（迁移任务会写入 progress/stage/updateTime），没有上报时留空。
     */
    private void fillProgress(TaskItemDto item, Task task) {
        Map<Object, Object> params = task.getParamsForApi();
        if (params != null) {
            item.setProgress(toInteger(params.get("progress")));
            item.setStage(params.get("stage") == null ? null : String.valueOf(params.get("stage")));
            item.setUpdateTime(toLong(params.get("updateTime")));
        }
        Long createDate = task.getCreateDate();
        if (createDate == null || createDate <= 0) {
            return;
        }
        Long endTime = item.getUpdateTime();
        if (endTime == null && isFinished(task.getStatus())) {
            // 任务已结束但没有上报时间，无法算出准确耗时
            return;
        }
        item.setDuration((endTime == null ? now : endTime) - createDate);
    }

    private boolean isFinished(Integer status) {
        return Objects.equals(status, TaskStatus.SUCCESS)
                || Objects.equals(status, TaskStatus.FAILURE)
                || Objects.equals(status, TaskStatus.API_NON_ASYNC_SUCCESS);
    }

    private Map<Integer, String> loadNodeNames(List<Task> tasks) {
        Set<Integer> nodeIds = tasks.stream().map(Task::getNodeid)
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Integer, String> names = new HashMap<>();
        for (List<Integer> batch : partition(nodeIds)) {
            for (Master master : masterService.list(new QueryWrapper<Master>().in("id", batch))) {
                names.put(master.getId(), master.getName());
            }
        }
        return names;
    }

    private Map<Integer, String> loadHostNames(List<Task> tasks) {
        Set<Integer> hostIds = tasks.stream().map(Task::getHostid)
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Integer, String> names = new HashMap<>();
        for (List<Integer> batch : partition(hostIds)) {
            for (Vmhost vmhost : vmhostService.list(new QueryWrapper<Vmhost>().in("id", batch))) {
                names.put(vmhost.getId(), vmhost.getHostname());
            }
        }
        return names;
    }

    private List<List<Integer>> partition(Set<Integer> ids) {
        List<Integer> all = new ArrayList<>(ids);
        List<List<Integer>> batches = new ArrayList<>();
        for (int i = 0; i < all.size(); i += RELATION_BATCH_SIZE) {
            batches.add(all.subList(i, Math.min(i + RELATION_BATCH_SIZE, all.size())));
        }
        return batches;
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

}

