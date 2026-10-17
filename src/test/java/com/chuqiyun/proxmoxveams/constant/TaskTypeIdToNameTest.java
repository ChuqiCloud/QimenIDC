package com.chuqiyun.proxmoxveams.constant;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TaskTypeIdToNameTest {
    @Test
    void resolvesNewlyExposedTypes() {
        assertEquals("离线迁移虚拟机", TaskTypeIdToName.getTaskTypeName(TaskType.MIGRATE_VM));
        assertEquals("导入系统镜像并挂载", TaskTypeIdToName.getTaskTypeName(TaskType.IMPORT_SYSTEM_DISK_NEW));
        assertEquals("应用Windows虚拟机IP", TaskTypeIdToName.getTaskTypeName(TaskType.APPLY_WINDOWS_VM_IP));
        assertEquals("执行QEMU初始化脚本", TaskTypeIdToName.getTaskTypeName(TaskType.RUN_QEMU_INIT_SCRIPT));
    }

    @Test
    void everyTaskTypeHasAName() throws IllegalAccessException {
        Set<Integer> types = new HashSet<>();
        for (Field field : TaskType.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !Modifier.isPublic(field.getModifiers())
                    || !Integer.class.equals(field.getType())) {
                continue;
            }
            Integer type = (Integer) field.get(null);
            if (types.add(type)) {
                assertFalse("未知任务类型".equals(TaskTypeIdToName.getTaskTypeName(type)),
                        "任务类型 " + field.getName() + "(" + type + ") 缺少名称");
            }
        }
        assertFalse(types.isEmpty());
    }

    @Test
    void fallsBackForUnknownType() {
        assertEquals("未知任务类型", TaskTypeIdToName.getTaskTypeName(null));
        assertEquals("未知任务类型", TaskTypeIdToName.getTaskTypeName(-1));
    }
}
