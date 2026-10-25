package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import com.baomidou.mybatisplus.extension.toolkit.SqlRunner;
import com.chuqiyun.proxmoxveams.security.SecuritySchemaMigration;
import com.chuqiyun.proxmoxveams.utils.SqlUpdateLoader;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SqlServiceImplSchemaMigrationTest {
    @Test
    void buildEightMigrationAddsMissingColumnsAndSkipsExistingColumns() throws Exception {
        Object previousFactory = ReflectionTestUtils.getField(SqlRunner.DEFAULT, "sqlSessionFactory");
        var previousGlobalFactory = SqlHelper.FACTORY;
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:public;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        jdbc.execute("create table master (id int primary key, public_ip_mode varchar(20) default 'legacy' not null)");
        jdbc.execute("create table configuretemplate (id int primary key, vga varchar(20))");
        jdbc.execute("create table vpc_ip_binding (id int primary key, delivery_mode varchar(20) default 'legacy' not null, last_error varchar(1024))");

        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(new MybatisConfiguration());
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setEnableSqlRunner(true);
        factory.setGlobalConfig(globalConfig);

        try {
            ReflectionTestUtils.setField(SqlRunner.DEFAULT, "sqlSessionFactory", factory.getObject());

            ConfigServiceImpl configService = mock(ConfigServiceImpl.class);
            when(configService.getBuild()).thenReturn("1.3.4_8");
            SqlServiceImpl service = new SqlServiceImpl();
            ReflectionTestUtils.setField(service, "configService", configService);
            ReflectionTestUtils.setField(service, "securitySchemaMigration", mock(SecuritySchemaMigration.class));
            ReflectionTestUtils.setField(service, "nowBuildVersion", "1.3.4_9");

            assertTrue(service.checkAndUpdateDatabase());

            assertColumnsExist(jdbc, "master", List.of(
                    "node_public_key", "public_ip_mode", "public_ip_interface"));
            assertColumnsExist(jdbc, "configuretemplate", List.of("vga"));
            assertColumnsExist(jdbc, "vpc_ip_binding", List.of(
                    "delivery_mode", "interface_name", "address_installed",
                    "route_installed", "last_check_time", "last_error"));
        } finally {
            jdbc.execute("drop all objects");
            ReflectionTestUtils.setField(SqlRunner.DEFAULT, "sqlSessionFactory", previousFactory);
            SqlHelper.FACTORY = previousGlobalFactory;
        }
    }

    @Test
    void buildNineSqlUpdateDeclaresAllSchemaFields() throws Exception {
        List<String> updates = new SqlUpdateLoader().getSqlUpdates("build", "1.3.4_9", 1);
        String sql = String.join("\n", updates).toLowerCase();

        assertEquals(10, updates.size());
        assertTrue(sql.contains("add vga varchar(20)"));
        assertTrue(sql.contains("add node_public_key text"));
        assertTrue(sql.contains("add public_ip_mode varchar(20)"));
        assertTrue(sql.contains("add public_ip_interface varchar(64)"));
        assertTrue(sql.contains("add delivery_mode varchar(20)"));
        assertTrue(sql.contains("add interface_name varchar(64)"));
        assertTrue(sql.contains("add address_installed int"));
        assertTrue(sql.contains("add route_installed int"));
        assertTrue(sql.contains("add last_check_time bigint"));
        assertTrue(sql.contains("add last_error varchar(1024)"));
    }

    @Test
    void freshInstallSqlContainsTheSameSchemaFields() {
        String sql = SqlUpdateLoader.readSqlScript().toLowerCase();

        assertTrue(createTableSection(sql, "configuretemplate").contains("vga"));
        String master = createTableSection(sql, "master");
        assertTrue(master.contains("node_public_key"));
        assertTrue(master.contains("public_ip_mode"));
        assertTrue(master.contains("public_ip_interface"));
        String binding = createTableSection(sql, "vpc_ip_binding");
        assertTrue(binding.contains("delivery_mode"));
        assertTrue(binding.contains("interface_name"));
        assertTrue(binding.contains("address_installed"));
        assertTrue(binding.contains("route_installed"));
        assertTrue(binding.contains("last_check_time"));
        assertTrue(binding.contains("last_error"));
    }

    private String createTableSection(String sql, String tableName) {
        String startMarker = "create table " + tableName;
        int start = sql.indexOf(startMarker);
        assertTrue(start >= 0, "missing table definition: " + tableName);
        int end = sql.indexOf("\ncreate table ", start + startMarker.length());
        return sql.substring(start, end < 0 ? sql.length() : end);
    }

    private void assertColumnsExist(JdbcTemplate jdbc, String tableName, List<String> columns) {
        for (String column : columns) {
            jdbc.queryForList("select " + column + " from " + tableName + " where 1 = 0");
        }
    }
}
