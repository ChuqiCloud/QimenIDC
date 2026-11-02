package com.chuqiyun.proxmoxveams.cron;

import com.chuqiyun.proxmoxveams.entity.Config;
import com.chuqiyun.proxmoxveams.manager.TrustedNetworkManager;
import com.chuqiyun.proxmoxveams.security.StoredCredentialMigration;
import com.chuqiyun.proxmoxveams.service.ConfigService;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.SqlService;
import com.chuqiyun.proxmoxveams.service.SysuserService;
import com.chuqiyun.proxmoxveams.utils.ClientApiUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @Author: 星禾
 * @Description: 以真实 Spring 生命周期验证客户端晚于启动编排创建时仍能同步白名单。
 * @DateTime: 2026/11/2 21:50
 */
class StartUpWhitelistLifecycleTest {
    @TempDir
    Path directory;

    @Test
    void synchronizesAfterDatabaseWarmupAndClientInjection() throws Exception {
        verifyLifecycle(false);
    }

    @Test
    void synchronizationFailureDoesNotAbortStartupOrRunTwiceOnRefreshEvent() throws Exception {
        verifyLifecycle(true);
    }

    private void verifyLifecycle(boolean failSync) throws Exception {
        String previousDirectory = System.getProperty("user.dir");
        Object previousService = ReflectionTestUtils.getField(ClientApiUtil.class, "masterService");
        Object previousVersion = ReflectionTestUtils.getField(ClientApiUtil.class, "masterVersion");
        Files.createDirectories(directory.resolve("config"));
        Files.createFile(directory.resolve("config/install.lock"));
        ConfigService configService = mock(ConfigService.class);
        MasterService masterService = mock(MasterService.class);
        SysuserService sysuserService = mock(SysuserService.class);
        SqlService sqlService = mock(SqlService.class);
        StoredCredentialMigration migration = mock(StoredCredentialMigration.class);
        TrustedNetworkManager manager = mock(TrustedNetworkManager.class);
        Config config = new Config();
        config.setToken("startup-test-token");
        when(configService.getById(1)).thenReturn(config);
        when(configService.getBuild()).thenReturn("test-build");
        when(sysuserService.count()).thenReturn(1L);
        when(sqlService.testConnection()).thenReturn(true);
        when(sqlService.checkAndUpdateDatabase()).thenReturn(true);
        doAnswer(call -> {
            // 强制重现旧顺序：数据库预热时静态客户端还未注入。
            assertNull(ReflectionTestUtils.getField(ClientApiUtil.class, "masterService"));
            verifyNoInteractions(manager);
            return null;
        }).when(masterService).updateAllNodeCookie();
        when(manager.initialSync()).thenAnswer(call -> {
            assertSame(masterService, ReflectionTestUtils.getField(ClientApiUtil.class, "masterService"));
            verify(migration).migrate();
            verify(masterService).updateAllNodeCookie();
            if (failSync) {
                throw new IllegalStateException("simulated node unavailable");
            }
            return 1;
        });
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            System.setProperty("user.dir", directory.toString());
            ReflectionTestUtils.setField(ClientApiUtil.class, "masterService", null);
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "info.application.build.version", "test-build", "info.application.version", "test-product")));
            // 注册顺序刻意让 StartUp 先创建，禁止测试依靠扫描顺序碰巧成功。
            context.registerBean(StartUp.class);
            context.registerBean(ClientApiUtil.class);
            context.getBeanFactory().registerSingleton("configService", configService);
            context.getBeanFactory().registerSingleton("masterService", masterService);
            context.getBeanFactory().registerSingleton("sysuserService", sysuserService);
            context.getBeanFactory().registerSingleton("sqlService", sqlService);
            context.getBeanFactory().registerSingleton("storedCredentialMigration", migration);
            context.getBeanFactory().registerSingleton("trustedNetworkManager", manager);
            assertDoesNotThrow(context::refresh);
            assertTrue(context.isActive());
            context.publishEvent(new org.springframework.context.event.ContextRefreshedEvent(context));
            verify(manager, times(1)).initialSync();
        } finally {
            System.setProperty("user.dir", previousDirectory);
            ReflectionTestUtils.setField(ClientApiUtil.class, "masterService", previousService);
            ReflectionTestUtils.setField(ClientApiUtil.class, "masterVersion", previousVersion);
        }
    }
}
