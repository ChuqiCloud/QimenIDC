package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.TaskDao;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.service.TaskService;
import org.springframework.stereotype.Service;

/**
 * (Task)表服务实现类
 *
 * @author mryunqi
 * @since 2023-06-29 22:38:31
 */
@Service("taskService")
public class TaskServiceImpl extends ServiceImpl<TaskDao, Task> implements TaskService {
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

}

