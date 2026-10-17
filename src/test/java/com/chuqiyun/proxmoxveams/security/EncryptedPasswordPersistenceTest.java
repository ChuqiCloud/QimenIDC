package com.chuqiyun.proxmoxveams.security;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.chuqiyun.proxmoxveams.dao.MasterDao;
import com.chuqiyun.proxmoxveams.dao.VmhostDao;
import com.chuqiyun.proxmoxveams.dao.VncinfoDao;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.entity.Vncinfo;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncryptedPasswordPersistenceTest {
    private SqlSession session;
    private JdbcTemplate jdbc;

    @BeforeEach
    void openDatabase() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:credentials_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(MasterDao.class);
        configuration.addMapper(VmhostDao.class);
        configuration.addMapper(VncinfoDao.class);
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        session = factoryBean.getObject().openSession(true);
        jdbc = new JdbcTemplate(dataSource);
        createTable(Master.class);
        createTable(Vmhost.class);
        createTable(Vncinfo.class);
    }

    @AfterEach
    void closeDatabase() {
        if (session != null) {
            session.close();
        }
    }

    @Test
    void masterCredentialsUseEncryptedColumnMappingForInsertReadAndUpdate() {
        MasterDao mapper = session.getMapper(MasterDao.class);
        Master node = new Master();
        node.setName("node");
        node.setPassword("node-password");
        node.setCsrfToken("csrf-secret");
        node.setTicket("ticket-secret");

        assertEquals(1, mapper.insert(node));
        String passwordBefore = assertEncrypted("master", "password", node.getId(), "node-password");
        assertEncrypted("master", "csrf_token", node.getId(), "csrf-secret");
        assertEncrypted("master", "ticket", node.getId(), "ticket-secret");

        Master loaded = mapper.selectById(node.getId());
        assertEquals("node-password", loaded.getPassword());
        assertEquals("csrf-secret", loaded.getCsrfToken());
        assertEquals("ticket-secret", loaded.getTicket());
        loaded.setName("renamed");
        assertEquals(1, mapper.updateById(loaded));
        assertNotEquals(passwordBefore, assertEncrypted("master", "password", node.getId(), "node-password"));
        assertEquals("node-password", mapper.selectById(node.getId()).getPassword());
    }

    @Test
    void vmPasswordRemainsEncryptedWhenUpdatingLoadedEntity() {
        VmhostDao mapper = session.getMapper(VmhostDao.class);
        Vmhost vm = new Vmhost();
        vm.setHostname("vm");
        vm.setPassword("vm-password");

        assertEquals(1, mapper.insert(vm));
        String before = assertEncrypted("vmhost", "password", vm.getId(), "vm-password");
        Vmhost loaded = mapper.selectById(vm.getId());
        assertEquals("vm-password", loaded.getPassword());
        loaded.setHostname("renamed");
        assertEquals(1, mapper.updateById(loaded));
        assertNotEquals(before, assertEncrypted("vmhost", "password", vm.getId(), "vm-password"));
        assertEquals("vm-password", mapper.selectById(vm.getId()).getPassword());
    }

    @Test
    void copiedVncPasswordAlsoUsesEncryptedPersistence() {
        VncinfoDao mapper = session.getMapper(VncinfoDao.class);
        Vncinfo vnc = new Vncinfo();
        vnc.setUsername("vnc");
        vnc.setPassword("vm-password");

        assertEquals(1, mapper.insert(vnc));
        String before = assertEncrypted("vncinfo", "password", vnc.getId(), "vm-password");
        Vncinfo loaded = mapper.selectById(vnc.getId());
        assertEquals("vm-password", loaded.getPassword());
        loaded.setUsername("renamed");
        assertEquals(1, mapper.updateById(loaded));
        assertNotEquals(before, assertEncrypted("vncinfo", "password", vnc.getId(), "vm-password"));
        assertEquals("vm-password", mapper.selectById(vnc.getId()).getPassword());
    }

    @Test
    void legacyValuesRemainReadableAndBecomeEncryptedOnNextWrite() {
        jdbc.update("insert into master (id, password) values (?, ?)", 500, "legacy-password");
        MasterDao mapper = session.getMapper(MasterDao.class);
        Master node = mapper.selectById(500);

        assertEquals("legacy-password", node.getPassword());
        node.setName("legacy-node");
        mapper.updateById(node);
        assertEncrypted("master", "password", 500, "legacy-password");
    }

    @Test
    void partialNodeUpdateWithoutCredentialsPreservesCiphertext() {
        MasterDao mapper = session.getMapper(MasterDao.class);
        Master node = new Master();
        node.setPassword("node-password");
        node.setCsrfToken("csrf-secret");
        node.setTicket("ticket-secret");
        mapper.insert(node);
        String before = assertEncrypted("master", "password", node.getId(), "node-password");
        Master partial = new Master();
        partial.setId(node.getId());
        partial.setName("renamed");

        assertEquals(1, mapper.updateById(partial));

        assertEquals(before, assertEncrypted("master", "password", node.getId(), "node-password"));
        Master loaded = mapper.selectById(node.getId());
        assertEquals("node-password", loaded.getPassword());
        assertEquals("csrf-secret", loaded.getCsrfToken());
        assertEquals("ticket-secret", loaded.getTicket());
    }

    @Test
    void corruptedEncryptedValuesCannotBeUsedAsPlaintextPasswords() {
        jdbc.update("insert into master (id, password) values (?, ?)", 501, "QIDENC:1:damaged");

        assertThrows(RuntimeException.class, () -> session.getMapper(MasterDao.class).selectById(501));
    }

    private String assertEncrypted(String table, String column, int id, String plainText) {
        String raw = jdbc.queryForObject("select `" + column + "` from `" + table + "` where id = ?",
                String.class, id);
        assertTrue(StoredPasswordCipher.isEncrypted(raw));
        assertNotEquals(plainText, raw);
        assertEquals(plainText, StoredPasswordCipher.decrypt(raw));
        return raw;
    }

    private void createTable(Class<?> entityClass) {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(entityClass);
        String columns = tableInfo.getFieldList().stream()
                .map(field -> "`" + field.getColumn() + "` varchar(8192)")
                .collect(Collectors.joining(", "));
        jdbc.execute("create table `" + tableInfo.getTableName() + "` (`"
                + tableInfo.getKeyColumn() + "` int generated by default as identity primary key, "
                + columns + ")");
    }
}
