package com.chuqiyun.proxmoxveams.task;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Component
public class TaskHandlerFactory {

    private final Map<Integer, TaskHandler> handlers = new HashMap<>();

    @Resource
    private ApplicationContext context;

    @PostConstruct
    public void init() {
        Map<String, TaskHandler> beans = context.getBeansOfType(TaskHandler.class);
        for (TaskHandler handler : beans.values()) {
            handlers.put(handler.getType(), handler);
        }
    }

    public TaskHandler getHandler(int type) {
        return handlers.get(type);
    }
}
