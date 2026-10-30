package com.chuqiyun.proxmoxveams.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chuqiyun.proxmoxveams.constant.TaskType;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.MigrationExecutionLock;
import com.chuqiyun.proxmoxveams.service.MigrationTerminationGuard;
import com.chuqiyun.proxmoxveams.service.TaskService;
import com.chuqiyun.proxmoxveams.service.VmIdReservationService;
import com.chuqiyun.proxmoxveams.utils.SqlUpdateLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * @Author: 星禾
 * @Description: 使用真实数据库事务验证终结任务与释放预留必须一起提交或回滚。
 * @DateTime: 2026/10/30 17:05
 */
class VmMigrationTerminationTransactionTest {
    private JdbcTemplate jdbc;
    private TaskService tasks;
    private VmIdReservationService reservations;
    private VmMigrationServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        DriverManagerDataSource source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        DataSourceTransactionManager transactions = new DataSourceTransactionManager(source);
        for (String sql : new SqlUpdateLoader().getSqlUpdates("build", "1.3.5_2", 1)) {
            jdbc.execute(sql);
        }
        jdbc.execute("create table task(id int primary key, hostid int, type int, status int, params clob, error varchar(1000))");
        Map<Object, Object> params = new HashMap<>(Map.of("stage", "FAILED", "lastFailedStep", "RESTORE",
                "sourceNodeId", 1, "sourceVmid", 101, "targetNodeId", 2, "targetVmid", 201,
                "vmIdReservationOwner", "migration:9", "checkpointStage", "BACKUP"));
        jdbc.update("insert into task(id, hostid, type, status, params) values (9, 1, ?, 3, ?)",
                TaskType.MIGRATE_VM, JSON.toJSONString(params));
        jdbc.update("insert into vm_id_reservation(node_id, vmid, owner, host_id, source_node_id, create_time) "
                + "values (2, 201, 'migration:9', 1, 1, 1)");
        tasks = mock(TaskService.class);
        when(tasks.getById(9)).thenAnswer(invocation -> readTask());
        when(tasks.update(any(Task.class), any())).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            return jdbc.update("update task set status = ?, params = ?, error = ? where id = ? and status = 3",
                    task.getStatus(), JSON.toJSONString(task.getParams()), task.getError(), task.getId()) == 1;
        });
        MigrationExecutionLock executionLock = mock(MigrationExecutionLock.class);
        when(executionLock.runLocked(any())).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return true;
        });
        reservations = spy(new VmIdReservationService(jdbc, transactions, mock(MasterService.class)));
        service = new VmMigrationServiceImpl();
        ReflectionTestUtils.setField(service, "taskService", tasks);
        ReflectionTestUtils.setField(service, "vmIdReservationService", reservations);
        ReflectionTestUtils.setField(service, "migrationExecutionLock", executionLock);
        ReflectionTestUtils.setField(service, "migrationTerminationGuard", mock(MigrationTerminationGuard.class));
        ReflectionTestUtils.setField(service, "transactionManager", transactions);
    }

    @AfterEach
    void tearDown() {
        if (jdbc != null) {
            jdbc.execute("drop all objects");
        }
    }

    @SuppressWarnings("unchecked")
    private Task readTask() {
        return jdbc.queryForObject("select * from task where id = 9", (row, index) -> {
            Task task = new Task();
            task.setId(row.getInt("id"));
            task.setHostid(row.getInt("hostid"));
            task.setType(row.getInt("type"));
            task.setStatus(row.getInt("status"));
            task.setParams(JSON.parseObject(row.getString("params"), Map.class));
            task.setError(row.getString("error"));
            return task;
        });
    }

    @Test
    void terminationCommitsTerminalTaskAndReservationDeletionTogether() {
        service.terminate(9);

        assertEquals("TERMINATED", readTask().getParams().get("stage"));
        assertEquals(0, jdbc.queryForObject("select count(*) from vm_id_reservation", Integer.class));
        assertThrows(IllegalStateException.class, () -> service.resume(9));
        service.terminate(9);
        assertEquals("TERMINATED", readTask().getParams().get("stage"));
    }

    @Test
    void failureAfterDeletingReservationRollsBackBothTaskAndReservation() {
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("injected failure after reservation delete");
        }).when(reservations).release("migration:9");

        assertThrows(IllegalStateException.class, () -> service.terminate(9));

        assertEquals("FAILED", readTask().getParams().get("stage"));
        assertEquals(1, jdbc.queryForObject("select count(*) from vm_id_reservation where owner = 'migration:9'",
                Integer.class));
        reservations.assertOwned(2, 201, "migration:9");
    }

    @Test
    void failedTaskCompareAndSetLeavesReservationIntact() {
        doReturn(false).when(tasks).update(any(Task.class), any());

        assertThrows(IllegalStateException.class, () -> service.terminate(9));

        assertEquals("FAILED", readTask().getParams().get("stage"));
        reservations.assertOwned(2, 201, "migration:9");
    }
}
