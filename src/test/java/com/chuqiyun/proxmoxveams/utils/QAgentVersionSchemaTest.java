package com.chuqiyun.proxmoxveams.utils;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import com.baomidou.mybatisplus.extension.toolkit.SqlRunner;
import com.chuqiyun.proxmoxveams.dao.MasterDao;
import com.chuqiyun.proxmoxveams.entity.Master;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 节点 QAgent 版本的升级、新装结构与实际 MyBatis 读写验证。
 *
 * @author 星禾
 * @date 2026/10/30
 */
class QAgentVersionSchemaTest {
    @Test
    void startupCanRepeatUpgradeBeforeBuildVersionIsSaved() throws Exception {
        Object previousFactory = ReflectionTestUtils.getField(SqlRunner.DEFAULT, "sqlSessionFactory");
        var previousGlobalFactory = SqlHelper.FACTORY;
        // H2 的 database() 返回数据库名；使用 public 使查询与默认表 schema 一致。
        DriverManagerDataSource source = new DriverManagerDataSource(
                "jdbc:h2:mem:public;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        try {
            jdbc.execute(masterTableSql().replaceAll("(?m)^.*qagent_version.*\\R", ""));
            jdbc.update("insert into master(id, name, host, username, password) values (?, ?, ?, ?, ?)",
                    13, "已有节点", "192.0.2.13", "root", "legacy-password");
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(source);
            factory.setConfiguration(new MybatisConfiguration());
            GlobalConfig globalConfig = new GlobalConfig();
            globalConfig.setEnableSqlRunner(true);
            factory.setGlobalConfig(globalConfig);
            ReflectionTestUtils.setField(SqlRunner.DEFAULT, "sqlSessionFactory", factory.getObject());

            ConfigServiceImpl config = mock(ConfigServiceImpl.class);
            // 模拟上次启动在写回构建号之前中断，重试时库内版本仍是旧值。
            when(config.getBuild()).thenReturn("1.3.5_2");
            SecuritySchemaMigration security = mock(SecuritySchemaMigration.class);
            SqlServiceImpl service = new SqlServiceImpl();
            ReflectionTestUtils.setField(service, "configService", config);
            ReflectionTestUtils.setField(service, "securitySchemaMigration", security);
            ReflectionTestUtils.setField(service, "nowBuildVersion", "1.3.5_3");

            assertTrue(service.checkAndUpdateDatabase());
            assertNull(jdbc.queryForObject("select qagent_version from master where id = 13", String.class));
            jdbc.update("update master set qagent_version = ? where id = ?", "1.0.9", 13);
            assertTrue(service.checkAndUpdateDatabase());
            assertEquals("1.0.9", jdbc.queryForObject(
                    "select qagent_version from master where id = 13", String.class));
            assertEquals("已有节点", jdbc.queryForObject("select name from master where id = 13", String.class));
            assertEquals("legacy-password", jdbc.queryForObject(
                    "select password from master where id = 13", String.class));
            verify(security, times(2)).migrate();
        } finally {
            jdbc.execute("drop all objects");
            ReflectionTestUtils.setField(SqlRunner.DEFAULT, "sqlSessionFactory", previousFactory);
            SqlHelper.FACTORY = previousGlobalFactory;
        }
    }

    @Test
    void upgradePreservesExistingNodesAndMapsReportedVersion() throws Exception {
        DriverManagerDataSource source = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(source);
        try {
            // 从新装结构去掉新增列，保留其余真实节点字段，模拟升级前数据库。
            jdbc.execute(masterTableSql().replaceAll("(?m)^.*qagent_version.*\\R", ""));
            jdbc.update("insert into master(id, name, host, username, password) values (?, ?, ?, ?, ?)",
                    13, "已有节点", "192.0.2.13", "root", "legacy-password");

            List<String> updates = new SqlUpdateLoader().getSqlUpdates("build", "1.3.5_3", 1);
            assertEquals(1, updates.size());
            updates.forEach(jdbc::execute);
            assertTrue(SqlUpdateLoader.compareBuildVersion("1.3.5_3", "1.3.5_2") > 0);
            assertNull(jdbc.queryForObject("select qagent_version from master where id = 13", String.class));
            assertEquals("已有节点", jdbc.queryForObject("select name from master where id = 13", String.class));

            try (SqlSession session = session(source)) {
                MasterDao mapper = session.getMapper(MasterDao.class);
                Master node = mapper.selectById(13);
                assertNull(node.getQagentVersion());
                assertEquals(1, mapper.update(null, new UpdateWrapper<Master>()
                        .eq("id", 13).set("qagent_version", "1.0.9")));

                Master saved = mapper.selectById(13);
                assertEquals("1.0.9", saved.getQagentVersion());
                assertEquals("已有节点", saved.getName());
                assertEquals("192.0.2.13", saved.getHost());
                assertEquals("legacy-password", saved.getPassword());
                assertEquals("1.0.9", jdbc.queryForObject(
                        "select qagent_version from master where id = 13", String.class));

                // 普通节点修改即使携带旧版本，也不得覆盖巡检刚更新的实际版本。
                Master stale = new Master();
                stale.setId(13);
                stale.setName("节点新名称");
                stale.setQagentVersion("1.0.8");
                assertEquals(1, mapper.updateById(stale));
                Master current = mapper.selectById(13);
                assertEquals("节点新名称", current.getName());
                assertEquals("1.0.9", current.getQagentVersion());
            }
        } finally {
            jdbc.execute("drop all objects");
        }
    }

    @Test
    void freshInstallScriptsMatchAndLeaveUnreportedVersionUnknown() throws Exception {
        String bundled = SqlUpdateLoader.readSqlScript();
        assertEquals(bundled.replace("\r\n", "\n"),
                Files.readString(Path.of("SQL/script.sql")).replace("\r\n", "\n"));
        DriverManagerDataSource source = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(source);
        try {
            jdbc.execute(masterTableSql());
            jdbc.update("insert into master(id, host, username, password) values (?, ?, ?, ?)",
                    1, "192.0.2.1", "root", "legacy-password");
            try (SqlSession session = session(source)) {
                assertNull(session.getMapper(MasterDao.class).selectById(1).getQagentVersion());
            }
        } finally {
            jdbc.execute("drop all objects");
        }
    }

    private String masterTableSql() {
        String script = SqlUpdateLoader.readSqlScript();
        int start = script.indexOf("create table master");
        assertTrue(start >= 0);
        int end = script.indexOf(";", start);
        assertTrue(end > start);
        return script.substring(start, end + 1);
    }

    private DriverManagerDataSource dataSource() {
        return new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    private SqlSession session(DriverManagerDataSource source) throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(MasterDao.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(configuration);
        return factory.getObject().openSession(true);
    }
}
