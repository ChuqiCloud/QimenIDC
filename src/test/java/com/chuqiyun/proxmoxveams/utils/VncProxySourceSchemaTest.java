package com.chuqiyun.proxmoxveams.utils;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import com.baomidou.mybatisplus.extension.toolkit.SqlRunner;
import com.chuqiyun.proxmoxveams.dao.VncnodeDao;
import com.chuqiyun.proxmoxveams.entity.Vncnode;
import com.chuqiyun.proxmoxveams.security.SecuritySchemaMigration;
import com.chuqiyun.proxmoxveams.service.impl.ConfigServiceImpl;
import com.chuqiyun.proxmoxveams.service.impl.SqlServiceImpl;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @Author: 星禾
 * @Description: VNC 出口字段的旧库升级重试、新装结构和局部更新验证
 * @DateTime: 2026/11/4 10:22
 */
class VncProxySourceSchemaTest {
    @Test
    void unknownBuildRepairsMissingProxySourcesWithoutOverwritingStoredSourcesOnRetry() throws Exception {
        verifyRecoveryPath(null);
    }

    @Test
    void currentBuildRepairsMissingProxySourcesWithoutOverwritingStoredSourcesOnRetry() throws Exception {
        verifyRecoveryPath("1.3.5_10");
    }

    @Test
    void upgradeFromBuildNineCanRetryWithoutOverwritingConfiguredSources() throws Exception {
        Object previousFactory = ReflectionTestUtils.getField(SqlRunner.DEFAULT, "sqlSessionFactory");
        var previousGlobalFactory = SqlHelper.FACTORY;
        DriverManagerDataSource source = new DriverManagerDataSource(
                "jdbc:h2:mem:public;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        try {
            jdbc.execute(vncTableSql().replaceAll("(?m)^.*proxy_source_ips.*\\R", ""));
            jdbc.update("insert into vncnode(id, name, host) values (?, ?, ?)", 1, "原有中转", "192.0.2.1");
            MybatisSqlSessionFactoryBean factory = factory(source);
            ReflectionTestUtils.setField(SqlRunner.DEFAULT, "sqlSessionFactory", factory.getObject());

            ConfigServiceImpl config = mock(ConfigServiceImpl.class);
            when(config.getBuild()).thenReturn("1.3.5_9");
            SqlServiceImpl service = new SqlServiceImpl();
            ReflectionTestUtils.setField(service, "configService", config);
            ReflectionTestUtils.setField(service, "securitySchemaMigration", mock(SecuritySchemaMigration.class));
            ReflectionTestUtils.setField(service, "nowBuildVersion", "1.3.5_10");

            assertTrue(service.checkAndUpdateDatabase());
            assertNull(jdbc.queryForObject("select proxy_source_ips from vncnode where id = 1", String.class));
            jdbc.update("update vncnode set proxy_source_ips = ? where id = 1", "198.51.100.8/32");
            assertTrue(service.checkAndUpdateDatabase(), "ALTER 后构建号尚未写回时必须允许重试");
            assertEquals("198.51.100.8/32", jdbc.queryForObject(
                    "select proxy_source_ips from vncnode where id = 1", String.class));

            try (SqlSession session = factory(source).getObject().openSession(true)) {
                VncnodeDao mapper = session.getMapper(VncnodeDao.class);
                assertEquals("198.51.100.8/32", mapper.selectById(1).getProxySourceIps());
                Vncnode partial = new Vncnode();
                partial.setId(1);
                partial.setName("新名称");
                assertEquals(1, mapper.updateById(partial));
                assertEquals("198.51.100.8/32", mapper.selectById(1).getProxySourceIps());
                partial.setProxySourceIps("");
                assertEquals(1, mapper.updateById(partial));
                assertEquals("", mapper.selectById(1).getProxySourceIps());
            }
            assertEquals("192.0.2.1", jdbc.queryForObject("select host from vncnode where id = 1", String.class));
        } finally {
            jdbc.execute("drop all objects");
            ReflectionTestUtils.setField(SqlRunner.DEFAULT, "sqlSessionFactory", previousFactory);
            SqlHelper.FACTORY = previousGlobalFactory;
        }
    }

    @Test
    void freshInstallAndUpgradeDeclareTheSameProxySourceColumn() throws Exception {
        assertTrue(vncTableSql().contains("proxy_source_ips text"));
        assertTrue(new SqlUpdateLoader().getSqlUpdates("build", "1.3.5_10", 1).get(0)
                .contains("add proxy_source_ips text"));
        assertEquals(SqlUpdateLoader.readSqlScript().replace("\r\n", "\n"),
                Files.readString(Path.of("SQL/script.sql")).replace("\r\n", "\n"));
    }

    private void verifyRecoveryPath(String dbBuild) throws Exception {
        Object previousFactory = ReflectionTestUtils.getField(SqlRunner.DEFAULT, "sqlSessionFactory");
        var previousGlobalFactory = SqlHelper.FACTORY;
        DriverManagerDataSource source = new DriverManagerDataSource(
                "jdbc:h2:mem:public;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        try {
            jdbc.execute(vncTableSql().replaceAll("(?m)^.*proxy_source_ips.*\\R", ""));
            jdbc.update("insert into vncnode(id, name, host) values (?, ?, ?)", 1, "原有中转", "192.0.2.1");
            // 恢复分支也会执行旧版修复，仅需保留这些表供其幂等补列。
            jdbc.execute("create table master (id int primary key)");
            jdbc.execute("create table configuretemplate (id int primary key)");
            jdbc.execute("create table vpc_ip_binding (id int primary key)");
            MybatisSqlSessionFactoryBean factory = factory(source);
            ReflectionTestUtils.setField(SqlRunner.DEFAULT, "sqlSessionFactory", factory.getObject());

            ConfigServiceImpl config = mock(ConfigServiceImpl.class);
            when(config.getBuild()).thenReturn(dbBuild);
            SqlServiceImpl service = new SqlServiceImpl();
            ReflectionTestUtils.setField(service, "configService", config);
            ReflectionTestUtils.setField(service, "securitySchemaMigration", mock(SecuritySchemaMigration.class));
            ReflectionTestUtils.setField(service, "nowBuildVersion", "1.3.5_10");

            assertTrue(service.checkAndUpdateDatabase());
            assertNull(jdbc.queryForObject("select proxy_source_ips from vncnode where id = 1", String.class));
            jdbc.update("update vncnode set proxy_source_ips = ? where id = 1", "198.51.100.8/32");
            assertTrue(service.checkAndUpdateDatabase());
            assertEquals("198.51.100.8/32", jdbc.queryForObject(
                    "select proxy_source_ips from vncnode where id = 1", String.class));
            assertEquals("192.0.2.1", jdbc.queryForObject("select host from vncnode where id = 1", String.class));
        } finally {
            jdbc.execute("drop all objects");
            ReflectionTestUtils.setField(SqlRunner.DEFAULT, "sqlSessionFactory", previousFactory);
            SqlHelper.FACTORY = previousGlobalFactory;
        }
    }

    private String vncTableSql() {
        String sql = SqlUpdateLoader.readSqlScript();
        int start = sql.indexOf("create table vncnode");
        assertTrue(start >= 0);
        int end = sql.indexOf("\ncreate table ", start + 1);
        return sql.substring(start, end < 0 ? sql.length() : end).trim();
    }

    private MybatisSqlSessionFactoryBean factory(DriverManagerDataSource source) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        GlobalConfig globalConfig = GlobalConfigUtils.defaults();
        globalConfig.setEnableSqlRunner(true);
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        configuration.addMapper(VncnodeDao.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(configuration);
        factory.setGlobalConfig(globalConfig);
        return factory;
    }
}
