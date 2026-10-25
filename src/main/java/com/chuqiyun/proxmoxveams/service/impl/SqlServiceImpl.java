package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.extension.toolkit.SqlRunner;
import com.chuqiyun.proxmoxveams.common.UnifiedLogger;
import com.chuqiyun.proxmoxveams.security.SecuritySchemaMigration;
import com.chuqiyun.proxmoxveams.service.SqlService;
import com.chuqiyun.proxmoxveams.utils.SqlUpdateLoader;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author mryunqi
 * @date 2023/11/26
 */
@Slf4j
@Service("sqlService")
public class SqlServiceImpl implements SqlService {
    private static final String SCHEMA_REPAIR_BUILD = "1.3.4_9";
    private static final Pattern SINGLE_COLUMN_ADD = Pattern.compile(
            "(?is)^alter\\s+table\\s+([a-z0-9_]+)\\s+add\\s+([a-z0-9_]+)\\s+(.+)$");
    @Value("${info.application.version}")
    private String nowVersion;
    /**
     * 内部版号
     */
    @Value("${info.application.build.version}")
    private String nowBuildVersion;

    @Resource
    private ConfigServiceImpl configService;
    @Resource
    private SecuritySchemaMigration securitySchemaMigration;

    /**
    * @Author: mryunqi
    * @Description: 执行指定SQL语句
    * @DateTime: 2023/11/26 23:03
    * @Params: String sql SQL语句
    * @Return boolean 执行结果
    */
    @Override
    public boolean executeSql(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return true;
        }
        try {
            SqlRunner.db().update(sql);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * @Author: mryunqi
     * @Description: 测试数据库连接是否正常
     * @DateTime: 2023/12/3 15:45
     * @Return boolean true:正常  false:异常
     */
    @Override
    public boolean testConnection(){
        // 使用 SqlRunner 进行查询
        try {
            SqlRunner.db().selectList("select 1");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 导入数据库
    * @DateTime: 2023/12/3 16:54
    * @Return boolean 导入结果
    */
    @Override
    public boolean installDbSql(){
        List<String> sqlList = SqlUpdateLoader.splitSqlScript();
        if (sqlList == null || sqlList.size() == 0) {
            return false;
        }
        for (String sql : sqlList) {
            if (!executeSql(sql)) {
                return false;
            }
        }
        return true;
    }
    
    /**
    * @Author: mryunqi
    * @Description: 检测并更新数据库
    * @DateTime: 2023/12/2 16:08
    * @Return  boolean 是否更新成功
    */
    @Override
    public boolean checkAndUpdateDatabase() {
        // 获取数据库中的版本号
        String dbBuildVersion = configService.getBuild();
        // 未知版本不重放历史 SQL。当前版本重复启动时仍按最新结构修复迁移校验列。
        if (dbBuildVersion == null) {
            return migrateSecuritySchema() && applySchemaRepairMigration();
        }
        if (dbBuildVersion.equals(nowBuildVersion)) {
            return migrateSecuritySchema() && applySchemaRepairMigration();
        }

        if (SqlUpdateLoader.compareBuildVersion(nowBuildVersion, dbBuildVersion) < 0) {
            UnifiedLogger.error(UnifiedLogger.LogType.SYSTEM,
                    "版本号异常，当前版本号小于数据库中的版本号: 当前版本={}, 数据库版本={}", nowBuildVersion, dbBuildVersion);
            return false;
        }

        SqlUpdateLoader sqlUpdateLoader = new SqlUpdateLoader();
        try {
            List<SqlUpdateLoader.BuildSqlStep> buildSqlStepList = sqlUpdateLoader.getOrderedBuildSqlSteps();
            for (SqlUpdateLoader.BuildSqlStep buildSqlStep : buildSqlStepList) {
                String stepBuildVersion = buildSqlStep.getStepBuildVersion();
                if (isSecuritySchemaStep(stepBuildVersion)) {
                    continue;
                }
                if (SqlUpdateLoader.compareBuildVersion(stepBuildVersion, dbBuildVersion) <= 0
                        || SqlUpdateLoader.compareBuildVersion(stepBuildVersion, nowBuildVersion) > 0) {
                    continue;
                }
                UnifiedLogger.log(UnifiedLogger.LogType.SYSTEM, "执行数据库升级SQL: {}", stepBuildVersion);
                boolean migrationSucceeded = SCHEMA_REPAIR_BUILD.equals(stepBuildVersion)
                        ? executeSchemaRepairSqlList(buildSqlStep.getSqlList())
                        : executeSqlList(buildSqlStep.getSqlList());
                if (!migrationSucceeded) {
                    UnifiedLogger.error(UnifiedLogger.LogType.SYSTEM, "数据库升级SQL执行失败: {}", stepBuildVersion);
                    return false;
                }
            }
        } catch (IOException e) {
            UnifiedLogger.error(UnifiedLogger.LogType.SYSTEM, "获取SQL更新语句失败:{}", e);
            return false;
        }
        return migrateSecuritySchema();
    }

    private boolean migrateSecuritySchema() {
        try {
            securitySchemaMigration.migrate();
            return true;
        } catch (RuntimeException e) {
            UnifiedLogger.error(UnifiedLogger.LogType.SYSTEM, "安全数据库结构升级失败，停止启动");
            return false;
        }
    }

    private boolean isSecuritySchemaStep(String version) {
        return version.equals("1.3.4_4") || version.equals("1.3.4_5") || version.equals("1.3.4_6");
    }

    private boolean applySchemaRepairMigration() {
        try {
            List<String> sqlList = new SqlUpdateLoader().getSqlUpdates("build", SCHEMA_REPAIR_BUILD, 1);
            if (sqlList == null || sqlList.isEmpty()) {
                UnifiedLogger.error(UnifiedLogger.LogType.SYSTEM,
                        "找不到数据库结构修复迁移: {}", SCHEMA_REPAIR_BUILD);
                return false;
            }
            return executeSchemaRepairSqlList(sqlList);
        } catch (IOException e) {
            UnifiedLogger.error(UnifiedLogger.LogType.SYSTEM,
                    "读取数据库结构修复迁移失败: {}", e);
            return false;
        }
    }

    private boolean executeSchemaRepairSqlList(List<String> sqlList) {
        for (String sql : sqlList) {
            if (!executeIdempotentColumnAdd(sql)) {
                UnifiedLogger.error(UnifiedLogger.LogType.SYSTEM,
                        "数据库结构修复迁移执行失败: {}", sql);
                return false;
            }
        }
        return true;
    }

    private boolean executeIdempotentColumnAdd(String sql) {
        String normalizedSql = sql == null ? "" : sql.trim().replaceFirst(";\\s*$", "");
        Matcher matcher = SINGLE_COLUMN_ADD.matcher(normalizedSql);
        if (!matcher.matches()) {
            return false;
        }
        return ensureColumn(matcher.group(1), matcher.group(2), matcher.group(3));
    }

    @Override
    public boolean hasRow(String tableName, String primaryKeyName, Object primaryKey) {
        if (StringUtils.isAnyBlank(tableName, primaryKeyName) || primaryKey == null) {
            return false;
        }
        String sql = "select 1 from " + tableName + " where " + primaryKeyName + " = {0} limit 1";
        try {
            List<Map<String, Object>> rows = SqlRunner.db().selectList(sql, primaryKey);
            return rows != null && !rows.isEmpty();
        } catch (Exception e) {
            // 表不存在等情况视为没有这一行，交由调用方的初始化逻辑处理。
            log.debug("检查数据行是否存在失败: {}.{}={}", tableName, primaryKeyName, primaryKey, e);
            return false;
        }
    }

    @Override
    public boolean ensureColumn(String tableName, String columnName, String definition) {
        if (StringUtils.isAnyBlank(tableName, columnName, definition)) {
            return false;
        }
        Boolean columnPresent = columnExists(tableName, columnName);
        if (Boolean.TRUE.equals(columnPresent)) {
            return true;
        }
        Boolean tablePresent = tableExists(tableName);
        if (Boolean.FALSE.equals(tablePresent)) {
            UnifiedLogger.error(UnifiedLogger.LogType.SYSTEM, "补列失败，表不存在: {}", tableName);
            return false;
        }
        if (columnPresent == null || tablePresent == null) {
            UnifiedLogger.error(UnifiedLogger.LogType.SYSTEM,
                    "无法确认表或列是否存在，补列迁移失败: {}.{}", tableName, columnName);
            return false;
        }
        UnifiedLogger.warn(UnifiedLogger.LogType.SYSTEM,
                "检测到缺失的列，正在补列: {}.{}", tableName, columnName);
        return executeSql("alter table " + tableName + " add " + columnName + " " + definition);
    }

    /**
     * 列是否存在。查不到元数据（非 MySQL 系/无权限）时返回 null，由调用方决定如何处理。
     */
    private Boolean columnExists(String tableName, String columnName) {
        try {
            List<Map<String, Object>> rows = SqlRunner.db().selectList(
                    "select 1 from information_schema.columns "
                            + "where table_schema = database() and table_name = {0} and column_name = {1}",
                    tableName, columnName);
            return rows != null && !rows.isEmpty();
        } catch (Exception e) {
            log.warn("查询列元数据失败: {}.{}", tableName, columnName, e);
            return null;
        }
    }

    private Boolean tableExists(String tableName) {
        try {
            List<Map<String, Object>> rows = SqlRunner.db().selectList(
                    "select 1 from information_schema.tables "
                            + "where table_schema = database() and table_name = {0}",
                    tableName);
            return rows != null && !rows.isEmpty();
        } catch (Exception e) {
            log.warn("查询表元数据失败: {}", tableName, e);
            return null;
        }
    }

    /**
     * @Author: 星禾
     * @Description: 顺序执行SQL列表
     * @DateTime: 2026/6/7 10:49
     * @Params: List<String> sqlList SQL语句列表
     * @Return boolean 是否执行成功
     */
    private boolean executeSqlList(List<String> sqlList) {
        if (sqlList == null || sqlList.isEmpty()) {
            return true;
        }
        for (String sql : sqlList) {
            if (!executeSql(sql)) {
                return false;
            }
        }
        return true;
    }

}
