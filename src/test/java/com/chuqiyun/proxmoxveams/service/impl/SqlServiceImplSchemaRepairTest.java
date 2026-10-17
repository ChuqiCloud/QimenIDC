package com.chuqiyun.proxmoxveams.service.impl;

import com.chuqiyun.proxmoxveams.utils.SqlUpdateLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @Author: 星禾
 * @Description: 启动结构修复的入参保护与列定义一致性；information_schema 查询依赖真实数据库
 * @DateTime: 2026/10/18 00:20
 */
class SqlServiceImplSchemaRepairTest {
    private final SqlServiceImpl service = new SqlServiceImpl();

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
        List<String> sqlList = new SqlUpdateLoader().getSqlUpdates("build", "1.3.4_6", 1);
        String upgradeSql = sqlList.get(0);
        assertTrue(upgradeSql.contains("add node_public_key text null"));
        assertTrue(upgradeSql.contains("comment '节点公钥(PEM证书)，用于核对节点证书与有效期'"));
    }
}
