package com.chuqiyun.proxmoxveams.utils;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlUpdateLoaderTest {
    @Test
    void loadsNatMigrationForBuild131Iteration3() throws IOException {
        List<String> sqlList = new SqlUpdateLoader().getSqlUpdates("build", "1.3.1", 3);

        assertEquals(2, sqlList.size());
        assertTrue(sqlList.get(0).contains("nat_forward_rule"));
        assertTrue(sqlList.get(1).contains("nat_sync_state"));
        assertTrue(SqlUpdateLoader.compareBuildVersion("1.3.1_3", "1.3.1_2") > 0);
    }
}
