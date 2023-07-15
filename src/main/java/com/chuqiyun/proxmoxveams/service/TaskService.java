package com.chuqiyun.proxmoxveams.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chuqiyun.proxmoxveams.entity.Task;

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
}

