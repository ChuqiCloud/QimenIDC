package com.chuqiyun.proxmoxveams.utils;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlUpdateLoaderTest {
    @Test
    void loadsCredentialColumnExpansionBeforeStartupMigration() throws IOException {
        List<String> sqlList = new SqlUpdateLoader().getSqlUpdates("build", "1.3.4", 3);

        assertEquals(3, sqlList.size());
        assertTrue(sqlList.get(0).contains("modify password text not null"));
        assertTrue(sqlList.get(0).contains("modify csrf_token text null"));
        assertTrue(sqlList.get(1).contains("alter table vmhost"));
        assertTrue(sqlList.get(2).contains("alter table vncinfo"));
    }

    @Test
    void loadsMfaAndPasskeyMigrationBeforeStartup() throws IOException {
        List<String> alterSql = new SqlUpdateLoader().getSqlUpdates("build", "1.3.4_4", 1);
        List<String> createSql = new SqlUpdateLoader().getSqlUpdates("build", "1.3.4_4", 2);

        assertEquals(2, alterSql.size());
        assertTrue(alterSql.get(0).contains("alter table sysuser"));
        assertTrue(alterSql.get(0).contains("add mfa_type"));
        assertTrue(alterSql.get(0).contains("add totp_secret"));
        assertTrue(alterSql.get(0).contains("add passkey_enabled"));
        assertEquals(1, createSql.size());
        assertTrue(createSql.get(0).contains("create table if not exists sys_passkey"));
        assertTrue(SqlUpdateLoader.compareBuildVersion("1.3.4_4", "1.3.4_3") > 0);
    }

    @Test
    void loadsAgentTrustTableForWhitelistSync() throws IOException {
        List<String> sqlList = new SqlUpdateLoader().getSqlUpdates("build", "1.3.4_5", 1);

        assertEquals(1, sqlList.size());
        assertTrue(sqlList.get(0).contains("create table if not exists agent_trust"));
        assertTrue(sqlList.get(0).contains("uk_agent_trust_cluster_addr"));
        assertTrue(SqlUpdateLoader.compareBuildVersion("1.3.4_5", "1.3.4_4") > 0);
    }

    @Test
    void loadsNodePublicKeyColumnForCertificatePreview() throws IOException {
        List<String> sqlList = new SqlUpdateLoader().getSqlUpdates("build", "1.3.4_6", 1);

        assertEquals(1, sqlList.size());
        assertTrue(sqlList.get(0).contains("alter table master"));
        assertTrue(sqlList.get(0).contains("add node_public_key"));
        assertTrue(SqlUpdateLoader.compareBuildVersion("1.3.4_6", "1.3.4_5") > 0);
    }

    @Test
    void loadsNatMigrationForBuild131Iteration3() throws IOException {
        List<String> sqlList = new SqlUpdateLoader().getSqlUpdates("build", "1.3.1", 3);

        assertEquals(2, sqlList.size());
        assertTrue(sqlList.get(0).contains("nat_forward_rule"));
        assertTrue(sqlList.get(1).contains("nat_sync_state"));
        assertTrue(SqlUpdateLoader.compareBuildVersion("1.3.1_3", "1.3.1_2") > 0);
    }
}
