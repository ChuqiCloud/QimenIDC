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
    void loadsNatMigrationForBuild131Iteration3() throws IOException {
        List<String> sqlList = new SqlUpdateLoader().getSqlUpdates("build", "1.3.1", 3);

        assertEquals(2, sqlList.size());
        assertTrue(sqlList.get(0).contains("nat_forward_rule"));
        assertTrue(sqlList.get(1).contains("nat_sync_state"));
        assertTrue(SqlUpdateLoader.compareBuildVersion("1.3.1_3", "1.3.1_2") > 0);
    }
}
