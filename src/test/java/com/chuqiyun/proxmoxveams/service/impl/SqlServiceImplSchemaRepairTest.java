package com.chuqiyun.proxmoxveams.service.impl;

import com.chuqiyun.proxmoxveams.security.SecuritySchemaMigration;
import com.chuqiyun.proxmoxveams.utils.SqlUpdateLoader;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import com.baomidou.mybatisplus.extension.toolkit.SqlRunner;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Author: 星禾
 * @Description: 启动结构修复的入参保护与列定义一致性；information_schema 查询依赖真实数据库
 * @DateTime: 2026/10/18 00:20
 */
class SqlServiceImplSchemaRepairTest {
    private final SqlServiceImpl service = new SqlServiceImpl();

    @Test
    void unknownBuildAppliesSchemaRepairMigrationWithoutReplayingHistory() {
        SqlServiceImpl checked = spy(service);
        ConfigServiceImpl configService = mock(ConfigServiceImpl.class);
        when(configService.getBuild()).thenReturn(null);
        ReflectionTestUtils.setField(checked, "configService", configService);
        ReflectionTestUtils.setField(checked, "securitySchemaMigration", mock(SecuritySchemaMigration.class));
        ReflectionTestUtils.setField(checked, "nowBuildVersion", "1.3.4_9");
        doReturn(true).when(checked).ensureColumn(anyString(), anyString(), anyString());

        assertTrue(checked.checkAndUpdateDatabase());

        verify(checked).ensureColumn("master", "public_ip_mode",
                "varchar(20) default 'legacy' not null comment 'VPC公网接入模式 legacy/routed/l2_alias/proxy_arp'");
        verify(checked).ensureColumn("vpc_ip_binding", "last_error", "varchar(1024) null");
    }

    @Test
    void currentBuildReappliesSchemaRepairFromSqlUpdate() {
        SqlServiceImpl checked = spy(service);
        ConfigServiceImpl configService = mock(ConfigServiceImpl.class);
        when(configService.getBuild()).thenReturn("1.3.4_9");
        ReflectionTestUtils.setField(checked, "configService", configService);
        ReflectionTestUtils.setField(checked, "securitySchemaMigration", mock(SecuritySchemaMigration.class));
        ReflectionTestUtils.setField(checked, "nowBuildVersion", "1.3.4_9");
        doReturn(true).when(checked).ensureColumn(anyString(), anyString(), anyString());

        assertTrue(checked.checkAndUpdateDatabase());

        verify(checked).ensureColumn("configuretemplate", "vga",
                "varchar(20) null comment '显示设备类型(std/qxl/virtio等)，决定VNC控制台默认分辨率'");
        verify(checked).ensureColumn("master", "public_ip_mode",
                "varchar(20) default 'legacy' not null comment 'VPC公网接入模式 legacy/routed/l2_alias/proxy_arp'");
        verify(checked).ensureColumn("vpc_ip_binding", "last_error", "varchar(1024) null");
    }

    @Test
    void queriesRowsAndRepairsMissingColumnsIdempotently() throws Exception {
        Object previousFactory = ReflectionTestUtils.getField(SqlRunner.DEFAULT, "sqlSessionFactory");
        var previousGlobalFactory = SqlHelper.FACTORY;
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:public;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table config (id int primary key)");
        jdbc.execute("insert into config values (1)");
        jdbc.execute("create table master (id int primary key)");
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(new MybatisConfiguration());
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setEnableSqlRunner(true);
        factory.setGlobalConfig(globalConfig);
        try {
            ReflectionTestUtils.setField(SqlRunner.DEFAULT, "sqlSessionFactory", factory.getObject());
            assertTrue(service.hasRow("config", "id", 1));
            assertFalse(service.hasRow("config", "id", 2));
            assertTrue(service.ensureColumn("master", "node_public_key", "text null"));
            assertTrue(service.ensureColumn("master", "node_public_key", "text null"));
            jdbc.queryForList("select node_public_key from master");
            assertFalse(service.ensureColumn("missing_table", "node_public_key", "text null"));
        } finally {
            jdbc.execute("drop all objects");
            ReflectionTestUtils.setField(SqlRunner.DEFAULT, "sqlSessionFactory", previousFactory);
            SqlHelper.FACTORY = previousGlobalFactory;
        }
    }

    @Test
    void ensureColumnRejectsIncompleteArguments() {
        assertFalse(service.ensureColumn(null, "node_public_key", "text null"));
        assertFalse(service.ensureColumn("master", " ", "text null"));
        assertFalse(service.ensureColumn("master", "node_public_key", "  "));
    }

    @Test
    void hasRowRejectsIncompleteArguments() {
        assertFalse(service.hasRow(null, "id", 1));
        assertFalse(service.hasRow("config", " ", 1));
        assertFalse(service.hasRow("config", "id", null));
    }

    @Test
    void repairDefinitionMatchesUpgradeScript() throws IOException {
        List<String> repairUpdates = new SqlUpdateLoader().getSqlUpdates("build", "1.3.4_9", 1);
        String repairSql = String.join("\n", repairUpdates);
        assertTrue(repairSql.contains("add node_public_key text null"));
        assertTrue(repairSql.contains("add public_ip_mode varchar(20) default 'legacy' not null"));
        assertTrue(repairSql.contains("add public_ip_interface varchar(64) null"));
        assertTrue(repairSql.contains("add delivery_mode varchar(20) default 'legacy' not null"));
        assertTrue(repairSql.contains("add interface_name varchar(64) null"));
        assertTrue(repairSql.contains("add address_installed int default 0 not null"));
        assertTrue(repairSql.contains("add route_installed int default 0 not null"));
        assertTrue(repairSql.contains("add last_check_time bigint null"));
        assertTrue(repairSql.contains("add last_error varchar(1024) null"));
        assertTrue(repairSql.contains("add vga varchar(20) null"));
    }
}
