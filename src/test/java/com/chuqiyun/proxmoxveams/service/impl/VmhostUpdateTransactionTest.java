package com.chuqiyun.proxmoxveams.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.constant.TaskType;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.dto.VmParams;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.TaskService;
import com.chuqiyun.proxmoxveams.service.VmIdReservationService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import com.chuqiyun.proxmoxveams.utils.SqlUpdateLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @Author: 星禾
 * @Description: 用独立数据库连接验证改配任务先提交，再等待关机，且并发改配和迁移都被持久化任务阻止。
 * @DateTime: 2026/11/2 10:30
 */
class VmhostUpdateTransactionTest {
    private JdbcTemplate jdbc;
    private JdbcTemplate observer;
    private DataSourceTransactionManager transactions;
    private VmIdReservationService reservations;
    private VmhostService api;
    private TestService target;
    private TaskService tasks;
    private ProxmoxApiUtil pve;
    private final AtomicInteger nextTaskId = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=1000";
        DriverManagerDataSource source = new DriverManagerDataSource(url, "sa", "");
        jdbc = new JdbcTemplate(source);
        observer = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        transactions = new DataSourceTransactionManager(source);
        for (String sql : new SqlUpdateLoader().getSqlUpdates("build", "1.3.5_2", 1)) {
            jdbc.execute(sql);
        }
        jdbc.execute("create table master(id int primary key, node_name varchar(64))");
        jdbc.execute("create table vmhost(id int primary key, nodeid int, vmid int, delete_state int, status int, memory int, natnum int)");
        jdbc.execute("create table task(id int primary key, nodeid int, vmid int, hostid int, type int, status int, "
                + "create_date bigint, params clob, error varchar(2000))");
        jdbc.update("insert into master values(1,'source'),(2,'target')");
        jdbc.update("insert into vmhost values(17,1,117,0,0,1024,0)");
        reservations = new VmIdReservationService(jdbc, transactions, mock(MasterService.class)) {
            @Override
            protected ClusterState loadClusterState(Integer nodeId) {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
                        "PVE集群查询不能在改配事务内执行");
                return new ClusterState(Set.of("source", "target"), Set.of(117)) {};
            }
        };
        pve = mock(ProxmoxApiUtil.class);
        target = spy(new TestService());
        ReflectionTestUtils.setField(target, "vmIdReservationService", reservations);
        ReflectionTestUtils.setField(target, "transactionManager", transactions);
        MasterService masters = mock(MasterService.class);
        Master node = new Master();
        node.setId(1);
        node.setNodeName("source");
        when(masters.getById(1)).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
                    "节点连接准备不能跨越行锁事务");
            return node;
        });
        when(masters.getMasterCookieMap(1)).thenReturn(new HashMap<>());
        ReflectionTestUtils.setField(target, "masterService", masters);
        tasks = mock(TaskService.class);
        when(tasks.save(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId(nextTaskId.incrementAndGet());
            return jdbc.update("insert into task values(?,?,?,?,?,?,?,?,?)", task.getId(), task.getNodeid(),
                    task.getVmid(), task.getHostid(), task.getType(), task.getStatus(), task.getCreateDate(),
                    JSON.toJSONString(task.getParams()), task.getError()) == 1;
        });
        when(tasks.getOne(any())).thenAnswer(invocation -> {
            AbstractWrapper<?, ?, ?> query = invocation.getArgument(0);
            var parameters = new ArrayList<Object>();
            String sql = bind("select * from task " + query.getCustomSqlSegment(), query, parameters);
            return jdbc.query(sql, (row, index) -> task(row), parameters.toArray())
                    .stream().findFirst().orElse(null);
        });
        when(tasks.getById(any())).thenAnswer(invocation -> jdbc.query(
                "select * from task where id=?", (row, index) -> task(row), (Object) invocation.getArgument(0))
                .stream().findFirst().orElse(null));
        when(tasks.updateTask(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            return jdbc.update("update task set status=?,params=?,error=? where id=?", task.getStatus(),
                    JSON.toJSONString(task.getParams()), task.getError(), task.getId()) == 1;
        });
        when(tasks.update(any(Wrapper.class))).thenAnswer(invocation -> update("task", invocation.getArgument(0)));
        ReflectionTestUtils.setField(target, "taskService", tasks);
        doAnswer(invocation -> jdbc.queryForObject("select * from vmhost where id=?", (row, index) -> {
            Vmhost vm = new Vmhost();
            vm.setId(row.getInt("id"));
            vm.setNodeid(row.getInt("nodeid"));
            vm.setVmid(row.getInt("vmid"));
            vm.setDeleteState(row.getInt("delete_state"));
            vm.setStatus(row.getInt("status"));
            vm.setMemory(row.getInt("memory"));
            return vm;
        }, (Object) invocation.getArgument(0))).when(target).getById(any());
        doAnswer(invocation -> {
            Vmhost vm = invocation.getArgument(0);
            if (vm.getStatus() != null) {
                jdbc.update("update vmhost set status=? where id=?", vm.getStatus(), vm.getId());
            }
            if (vm.getMemory() != null) {
                jdbc.update("update vmhost set memory=? where id=?", vm.getMemory(), vm.getId());
            }
            return true;
        }).when(target).updateById(any(Vmhost.class));
        doAnswer(invocation -> update("vmhost", invocation.getArgument(0))).when(target).update(any(Wrapper.class));
        doReturn(true).when(target).addVmHostTask(any(), any());
        doAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
                    "PVE改配请求不能在数据库事务内执行");
            return null;
        }).when(pve).resetVmConfig(any(), any(), eq(117), eq("memory"), eq("2048"));
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setInterfaces(VmhostService.class);
        proxy.setProxyTargetClass(false);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        api = (VmhostService) proxy.getProxy();
    }

    @AfterEach
    void tearDown() {
        if (target != null) {
            target.continueRestore.countDown();
        }
        if (jdbc != null) {
            jdbc.execute("drop all objects");
        }
    }

    @SuppressWarnings("unchecked")
    private Task task(java.sql.ResultSet row) throws java.sql.SQLException {
        Task task = new Task();
        task.setId(row.getInt("id"));
        task.setNodeid(row.getInt("nodeid"));
        task.setVmid(row.getInt("vmid"));
        task.setHostid(row.getInt("hostid"));
        task.setType(row.getInt("type"));
        task.setStatus(row.getInt("status"));
        task.setCreateDate(row.getLong("create_date"));
        task.setParams(JSON.parseObject(row.getString("params"), Map.class));
        task.setError(row.getString("error"));
        return task;
    }

    private VmParams memoryChange() {
        VmParams request = new VmParams();
        request.setHostid(17);
        request.setMemory(2048);
        return request;
    }

    private boolean update(String table, Wrapper<?> wrapper) {
        UpdateWrapper<?> values = (UpdateWrapper<?>) wrapper;
        String sql = "update " + table + " set " + values.getSqlSet() + " " + values.getCustomSqlSegment();
        var parameters = new ArrayList<Object>();
        return jdbc.update(bind(sql, values, parameters), parameters.toArray()) == 1;
    }

    private String bind(String sql, AbstractWrapper<?, ?, ?> wrapper, ArrayList<Object> parameters) {
        return Pattern.compile("#\\{ew\\.paramNameValuePairs\\.(\\w+)\\}").matcher(sql)
                .replaceAll(match -> {
                    parameters.add(wrapper.getParamNameValuePairs().get(match.group(1)));
                    return "?";
                });
    }

    @Test
    void shutdownAndUpdateMarkerAreCommittedBeforeWaitAndPreventDuplicateOrMigration() throws Exception {
        var pool = Executors.newSingleThreadExecutor();
        try {
            var update = pool.submit(() -> new TransactionTemplate(transactions)
                    .execute(transaction -> api.updateVm(memoryChange())));
            assertTrue(target.waiting.await(5, TimeUnit.SECONDS));
            assertEquals(1, observer.queryForObject("select count(*) from task where type=? and status=1",
                    Integer.class, TaskType.UPDATE_VM));
            assertEquals(1, observer.queryForObject("select count(*) from task where type=? and status=0",
                    Integer.class, TaskType.STOP_VM_FORCE));
            new TransactionTemplate(new DataSourceTransactionManager(observer.getDataSource()))
                    .executeWithoutResult(transaction -> assertEquals(17, observer.queryForObject(
                            "select id from vmhost where id=17 for update", Integer.class)));
            UnifiedResultDto<Object> duplicate = api.updateVm(memoryChange());
            assertNotEquals(UnifiedResultCode.SUCCESS, duplicate.getResultCode());
            assertTrue(duplicate.getMessage().contains("修改") || duplicate.getMessage().contains("改配"));
            assertEquals(Boolean.FALSE, api.power(17, "shutdown", null).get("status"));
            assertNotEquals(UnifiedResultCode.SUCCESS, api.deleteVm(17L).getResultCode());
            assertEquals(1, observer.queryForObject("select count(*) from task where type=?",
                    Integer.class, TaskType.STOP_VM_FORCE));
            assertEquals(0, observer.queryForObject("select delete_state from vmhost where id=17", Integer.class));
            IllegalStateException migration = assertThrows(IllegalStateException.class,
                    () -> reservations.reserve(2, 201, "migration:during-update", 17));
            assertTrue(migration.getMessage().contains("未完成"));
            target.continueRestore.countDown();
            assertEquals(UnifiedResultCode.SUCCESS, update.get(5, TimeUnit.SECONDS).getResultCode());
            assertEquals(2048, observer.queryForObject("select memory from vmhost where id=17", Integer.class));
            assertEquals(1, observer.queryForObject("select count(*) from task where type=? and status=2",
                    Integer.class, TaskType.UPDATE_VM));
            assertEquals(1, observer.queryForObject("select count(*) from task where type=? and status=0",
                    Integer.class, TaskType.START_VM));
        } finally {
            target.continueRestore.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void failedShutdownEnqueueDoesNotWaitOrApplyConfigurationAndFailsMarker() {
        doReturn(false).when(tasks).save(argThat(task -> TaskType.STOP_VM_FORCE.equals(task.getType())));

        UnifiedResultDto<Object> result = api.updateVm(memoryChange());

        assertEquals(UnifiedResultCode.ERROR_UNKNOWN, result.getResultCode());
        assertTrue(result.getMessage().contains("强制关机任务创建失败"));
        assertEquals(1, target.waiting.getCount());
        assertEquals(0, target.factoryCalls.get());
        assertEquals(0, observer.queryForObject("select status from vmhost where id=17", Integer.class));
        assertEquals(1024, observer.queryForObject("select memory from vmhost where id=17", Integer.class));
        assertEquals(1, observer.queryForObject("select count(*) from task where type=? and status=3",
                Integer.class, TaskType.UPDATE_VM));
        assertEquals(0, observer.queryForObject("select count(*) from task where type=?",
                Integer.class, TaskType.START_VM));
        verifyNoInteractions(pve);
    }

    @Test
    void shutdownTimeoutRecordsFailureAndRetryDoesNotConfigureStoppingVm() {
        target.waitFailure = new IllegalStateException("等待PVE虚拟机关机超时");
        target.claimShutdown = true;

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> api.updateVm(memoryChange()));

        assertEquals("等待PVE虚拟机关机超时", error.getMessage());
        assertEquals("等待PVE虚拟机关机超时", observer.queryForObject(
                "select error from task where type=? and status=3", String.class, TaskType.UPDATE_VM));
        assertEquals(1024, observer.queryForObject("select memory from vmhost where id=17", Integer.class));
        assertEquals(0, observer.queryForObject("select count(*) from task where type=?",
                Integer.class, TaskType.START_VM));
        assertEquals(9, observer.queryForObject("select status from vmhost where id=17", Integer.class));
        int factoryCalls = target.factoryCalls.get();
        target.waitFailure = null;

        UnifiedResultDto<Object> retry = assertTimeout(Duration.ofSeconds(2), () -> api.updateVm(memoryChange()));

        assertEquals(UnifiedResultCode.ERROR_INVALID_PARAM, retry.getResultCode());
        assertTrue(retry.getMessage().contains("正在关机"));
        assertEquals(factoryCalls, target.factoryCalls.get());
        assertEquals(1, observer.queryForObject("select count(*) from task where type=?",
                Integer.class, TaskType.UPDATE_VM));
        assertEquals(1024, observer.queryForObject("select memory from vmhost where id=17", Integer.class));
        assertEquals(0, observer.queryForObject("select count(*) from task where type=?",
                Integer.class, TaskType.START_VM));
        verifyNoInteractions(pve);
    }

    @Test
    void timeoutCancelsUnclaimedShutdownAndRestoresRunningState() {
        target.waitFailure = new IllegalStateException("等待PVE虚拟机关机超时");

        assertThrows(IllegalStateException.class, () -> api.updateVm(memoryChange()));

        assertEquals(1, observer.queryForObject("select count(*) from task where type=? and status=3",
                Integer.class, TaskType.STOP_VM_FORCE));
        assertEquals(0, observer.queryForObject("select status from vmhost where id=17", Integer.class));
        assertEquals(1, observer.queryForObject("select count(*) from task where type=? and status=3",
                Integer.class, TaskType.UPDATE_VM));
        assertEquals(0, observer.queryForObject("select count(*) from task where type=?",
                Integer.class, TaskType.START_VM));
        assertEquals(1024, observer.queryForObject("select memory from vmhost where id=17", Integer.class));
        verifyNoInteractions(pve);
    }

    @Test
    void stoppedVirtualMachineCanBeConfiguredWithoutEnqueuingShutdownOrPolling() {
        jdbc.update("update vmhost set status=1 where id=17");

        UnifiedResultDto<Object> result = api.updateVm(memoryChange());

        assertEquals(UnifiedResultCode.SUCCESS, result.getResultCode());
        assertEquals(1, target.waiting.getCount());
        assertEquals(0, observer.queryForObject("select count(*) from task where type=?",
                Integer.class, TaskType.STOP_VM_FORCE));
        assertEquals(2048, observer.queryForObject("select memory from vmhost where id=17", Integer.class));
        assertEquals(1, observer.queryForObject("select count(*) from task where type=? and status=2",
                Integer.class, TaskType.UPDATE_VM));
        verify(pve).resetVmConfig(any(), any(), eq(117), eq("memory"), eq("2048"));
    }

    @Test
    void quotaOnlyRequestRemainsAllowedWhileVmIsStopping() {
        jdbc.update("update vmhost set status=9 where id=17");
        VmParams request = new VmParams();
        request.setHostid(17);
        request.setNatnum(4);

        UnifiedResultDto<Object> result = api.updateVm(request);

        assertEquals(UnifiedResultCode.SUCCESS, result.getResultCode());
        assertEquals(4, observer.queryForObject("select natnum from vmhost where id=17", Integer.class));
        assertEquals(0, target.factoryCalls.get());
        assertEquals(1, observer.queryForObject("select count(*) from task where type=? and status=2",
                Integer.class, TaskType.UPDATE_VM));
        assertEquals(1, observer.queryForObject("select count(*) from task", Integer.class));
        verifyNoInteractions(pve);
    }

    private class TestService extends VmhostServiceImpl {
        private final CountDownLatch waiting = new CountDownLatch(1);
        private final CountDownLatch continueRestore = new CountDownLatch(1);
        private final AtomicInteger factoryCalls = new AtomicInteger();
        private RuntimeException waitFailure;
        private boolean claimShutdown;

        @Override
        protected ProxmoxApiUtil newProxmoxApi() {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            factoryCalls.incrementAndGet();
            return pve;
        }

        @Override
        protected void waitPveVmStopped(ProxmoxApiUtil api, Master node,
                                        HashMap<String, String> cookies, Integer vmid) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
                    "关机轮询不能持有数据库事务");
            if (claimShutdown) {
                jdbc.update("update task set status=1 where type=? and status=0", TaskType.STOP_VM_FORCE);
            }
            if (waitFailure != null) {
                throw waitFailure;
            }
            waiting.countDown();
            try {
                assertTrue(continueRestore.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(error);
            }
        }
    }
}
