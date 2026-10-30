package com.chuqiyun.proxmoxveams.service;

import com.chuqiyun.proxmoxveams.utils.SqlUpdateLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class VmIdReservationServiceTest {
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactions;

    @BeforeEach
    void setUp() throws Exception {
        DriverManagerDataSource source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        jdbc = new JdbcTemplate(source);
        transactions = new DataSourceTransactionManager(source);
        // 测试直接执行发布升级里的DDL，防止测试表与真实升级脚本各自漂移。
        for (String sql : new SqlUpdateLoader().getSqlUpdates("build", "1.3.5_2", 1)) {
            jdbc.execute(sql);
        }
        jdbc.execute("create table master(id int primary key, node_name varchar(64))");
        jdbc.execute("create table vmhost(id int primary key, nodeid int, vmid int, delete_state int)");
        jdbc.execute("create table task(id int primary key, hostid int, status int)");
        jdbc.update("insert into master values (1, 'pve-a'), (2, 'pve-b'), (3, 'standalone')");
        for (int i = 1; i <= 20; i++) {
            jdbc.update("insert into vmhost values(?, 3, ?, 0)", i, 1000 + i);
        }
    }

    private VmIdReservationService service(Set<String> nodes, Set<Integer> vmids) {
        return new VmIdReservationService(jdbc, transactions, mock(MasterService.class)) {
            @Override
            protected ClusterState loadClusterState(Integer nodeId) {
                return new ClusterState(nodes, vmids);
            }
        };
    }

    private VmIdReservationService clusterService() {
        return service(Set.of("pve-a", "pve-b"), Set.of(100, 103));
    }

    @Test
    void concurrentCreateAndMigrationAcrossManagersAndClusterNodesNeverReuseVmid() throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 16; i++) {
                final int index = i;
                // 独立service实例模拟不同主控进程；两条入口使用不同PVE集群节点。
                VmIdReservationService service = clusterService();
                Callable<Integer> allocation = () -> {
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return service.reserve(index % 2 == 0 ? 1 : 2, null,
                            (index % 2 == 0 ? "create:" : "migration:") + index, index % 2 == 0 ? null : index);
                };
                results.add(pool.submit(allocation));
            }
            start.countDown();
            Set<Integer> allocated = new HashSet<>();
            for (Future<Integer> result : results) {
                assertTrue(allocated.add(result.get(15, TimeUnit.SECONDS)), "duplicate VMID");
            }
            assertEquals(16, allocated.size());
            assertEquals(16, jdbc.queryForObject("select count(*) from vm_id_reservation", Integer.class));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void explicitVmidRejectsPveLxcAndOtherNodeReservationAndRecycleBinVm() {
        VmIdReservationService first = clusterService();
        first.reserve(1, 104, "migration:first", 11);
        VmIdReservationService second = clusterService();
        assertThrows(IllegalStateException.class, () -> second.reserve(2, 104, "create:second"));
        assertThrows(IllegalStateException.class, () -> second.reserve(2, 103, "migration:pve", 12));
        jdbc.update("insert into vmhost values(81, 1, 105, 1)");
        assertThrows(IllegalStateException.class, () -> second.reserve(2, 105, "migration:recycle", 13));
        assertEquals(1, jdbc.queryForObject("select count(*) from vm_id_reservation", Integer.class));
    }

    @Test
    void independentClusterMayUseSameVmidAndCompletedDeletionDoesNotBlockAllocation() {
        clusterService().reserve(1, 104, "migration:first", 11);
        jdbc.update("insert into vmhost values(81, 3, 105, 2)");
        VmIdReservationService standalone = service(Set.of("standalone"), Set.of(100));
        assertEquals(104, standalone.reserve(3, 104, "create:standalone"));
        assertEquals(105, standalone.reserve(3, 105, "create:deleted"));
    }

    @Test
    void sameSourceHostCannotBeReservedByTwoMigrationsAndFailedReservationSurvivesNewService() {
        assertEquals(104, clusterService().reserve(1, 104, "migration:failed", 11));
        assertThrows(IllegalStateException.class, () -> clusterService().reserve(2, 106, "migration:other", 11));
        clusterService().assertOwned(1, 104, "migration:failed");
        assertThrows(IllegalStateException.class, () -> clusterService().assertOwned(1, 104, "migration:other"));
        clusterService().release("migration:other");
        assertEquals(1, jdbc.queryForObject("select count(*) from vm_id_reservation", Integer.class));
        clusterService().release("migration:failed");
        assertEquals(106, clusterService().reserve(2, 106, "migration:retry-after-cleanup", 11));
    }

    @Test
    void idempotentReservationCanBeUsedAfterOwnRestoreHasCreatedTargetVm() {
        clusterService().reserve(1, 104, "migration:own", 11);
        VmIdReservationService afterRestore = service(Set.of("pve-a", "pve-b"), Set.of(100, 103, 104));
        assertEquals(104, afterRestore.reserve(1, 104, "migration:own", 11));
        afterRestore.assertOwned(1, 104, "migration:own");
        assertThrows(IllegalStateException.class, () -> afterRestore.reserve(2, 104, "migration:own", 11));
        assertThrows(IllegalStateException.class, () -> afterRestore.reserve(1, 105, "migration:own", 11));
    }

    @Test
    void failedClusterQueryAndInvalidVmidNeverReserveAnything() {
        VmIdReservationService unavailable = new VmIdReservationService(jdbc, transactions, mock(MasterService.class)) {
            @Override
            protected ClusterState loadClusterState(Integer nodeId) {
                throw new IllegalStateException("unavailable");
            }
        };
        assertThrows(IllegalStateException.class, () -> unavailable.reserve(1, null, "migration:offline", 11));
        assertThrows(IllegalStateException.class, () -> clusterService().reserve(1, 99, "migration:invalid", 11));
        assertEquals(0, jdbc.queryForObject("select count(*) from vm_id_reservation", Integer.class));
    }

    @Test
    void mutationQueuedBeforeReservationMakesMigrationFailAndReservedHostBlocksMutations() {
        VmIdReservationService service = clusterService();
        TransactionTemplate tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> {
            assertFalse(service.lockAndIsHostReserved(11));
            jdbc.update("insert into task values(1, 11, 0)");
        });
        assertThrows(IllegalStateException.class, () -> service.reserve(2, 110, "migration:queued-operation", 11));
        jdbc.update("update task set status = 2 where id = 1");
        service.reserve(2, 110, "migration:locked", 11);
        tx.executeWithoutResult(status -> assertTrue(service.lockAndIsHostReserved(11)));
        assertThrows(IllegalStateException.class, () -> service.lockAndIsHostReserved(11));
    }

    @Test
    void nodeSyncBarrierRetainsSourceNodeAfterVmhostSwitchesToTarget() {
        VmIdReservationService service = clusterService();
        service.reserve(2, 110, "migration:barrier", 11);
        assertTrue(service.isNodeInMigration(3));
        assertTrue(service.isNodeInMigration(2));
        assertFalse(service.isNodeInMigration(1));
        jdbc.update("update vmhost set nodeid = 2, vmid = 110 where id = 11");
        assertTrue(service.isNodeInMigration(3));
        service.release("migration:barrier");
        assertFalse(service.isNodeInMigration(3));
        assertFalse(service.isNodeInMigration(2));
    }

    @Test
    void concurrentMutationKeepsMigrationOutUntilQueuedTaskIsCommitted() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch mutationLocked = new CountDownLatch(1);
        CountDownLatch migrationStarted = new CountDownLatch(1);
        CountDownLatch releaseMutation = new CountDownLatch(1);
        try {
            Future<?> mutation = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                assertFalse(clusterService().lockAndIsHostReserved(11));
                mutationLocked.countDown();
                try {
                    assertTrue(releaseMutation.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                jdbc.update("insert into task values(1, 11, 0)");
            }));
            assertTrue(mutationLocked.await(5, TimeUnit.SECONDS));
            Future<?> migration = pool.submit(() -> {
                migrationStarted.countDown();
                return clusterService().reserve(2, 110, "migration:concurrent", 11);
            });
            assertTrue(migrationStarted.await(5, TimeUnit.SECONDS));
            assertThrows(java.util.concurrent.TimeoutException.class, () -> migration.get(150, TimeUnit.MILLISECONDS));
            releaseMutation.countDown();
            mutation.get(5, TimeUnit.SECONDS);
            java.util.concurrent.ExecutionException error = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> migration.get(5, TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class, error.getCause());
            assertTrue(error.getCause().getMessage().contains("未完成"));
            assertEquals(0, jdbc.queryForObject("select count(*) from vm_id_reservation", Integer.class));
        } finally {
            releaseMutation.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void migrationReservationWaitsForExistingNatSynchronizationToFinish() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch syncLocked = new CountDownLatch(1);
        CountDownLatch releaseSync = new CountDownLatch(1);
        try {
            Future<?> sync = pool.submit(() -> clusterService().withAllocationLock(() -> {
                assertFalse(clusterService().isNodeInMigration(2));
                syncLocked.countDown();
                try {
                    assertTrue(releaseSync.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }));
            assertTrue(syncLocked.await(5, TimeUnit.SECONDS));
            Future<Integer> migration = pool.submit(() -> clusterService().reserve(2, 110, "migration:sync", 11));
            assertThrows(java.util.concurrent.TimeoutException.class, () -> migration.get(150, TimeUnit.MILLISECONDS));
            releaseSync.countDown();
            sync.get(5, TimeUnit.SECONDS);
            assertEquals(110, migration.get(5, TimeUnit.SECONDS));
            clusterService().withAllocationLock(() -> assertTrue(clusterService().isNodeInMigration(2)));
        } finally {
            releaseSync.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void freshInstallScriptsContainTheSameAllocationSchema() throws Exception {
        String resource = SqlUpdateLoader.readSqlScript();
        String standalone = java.nio.file.Files.readString(java.nio.file.Path.of("SQL/script.sql"));
        for (String table : List.of("vm_id_allocation_lock", "vm_id_reservation")) {
            assertTrue(resource.contains("create table " + table));
            assertTrue(standalone.contains("create table " + table));
        }
        assertTrue(resource.contains("unique index uk_vm_id_reservation_host (host_id)"));
        assertTrue(standalone.contains("unique index uk_vm_id_reservation_host (host_id)"));
    }
}
