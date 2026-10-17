package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.extension.toolkit.SqlRunner;
import com.chuqiyun.proxmoxveams.common.UnifiedLogger;
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

/**
 * @author mryunqi
 * @date 2023/11/26
 */
@Slf4j
@Service("sqlService")
public class SqlServiceImpl implements SqlService {
    @Value("${info.application.version}")
    private String nowVersion;
    /**
     * 内部版号
     */
    @Value("${info.application.build.version}")
    private String nowBuildVersion;

    @Resource
    private ConfigServiceImpl configService;

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
        // 版本号为空或与当前一致时，历史上是直接跳过。但 build 号只说明「程序曾经写过这一版」，
        // 不代表这一版的表结构真的落到库里了：回滚过、手动改过库、多实例并发启动失败重试等情况，
        // 都会留下「build 号已经是新版、列却不存在」的库，启动后在实体查询里报 Unknown column。
        // 所以这里不再提前返回，而是照常进入下面的幂等修复。
        if (dbBuildVersion == null) {
            UnifiedLogger.warn(UnifiedLogger.LogType.SYSTEM,
                    "数据库未记录构建号，将按当前版本执行结构校验与修复: 当前版本={}", nowBuildVersion);
        } else if (dbBuildVersion.equals(nowBuildVersion)) {
            UnifiedLogger.log(UnifiedLogger.LogType.SYSTEM,
                    "数据库构建号与当前版本一致({})，执行结构校验", dbBuildVersion);
        } else if (SqlUpdateLoader.compareBuildVersion(nowBuildVersion, dbBuildVersion) < 0) {
            UnifiedLogger.error(UnifiedLogger.LogType.SYSTEM,
                    "版本号异常，当前版本号小于数据库中的版本号: 当前版本={}, 数据库版本={}", nowBuildVersion, dbBuildVersion);
            return false;
        }

        SqlUpdateLoader sqlUpdateLoader = new SqlUpdateLoader();
        try {
            List<SqlUpdateLoader.BuildSqlStep> buildSqlStepList = sqlUpdateLoader.getOrderedBuildSqlSteps();
            for (SqlUpdateLoader.BuildSqlStep buildSqlStep : buildSqlStepList) {
                String stepBuildVersion = buildSqlStep.getBuildVersion() + "_" + buildSqlStep.getIteration();
                if (dbBuildVersion != null
                        && SqlUpdateLoader.compareBuildVersion(stepBuildVersion, dbBuildVersion) <= 0) {
                    continue;
                }
                if (SqlUpdateLoader.compareBuildVersion(stepBuildVersion, nowBuildVersion) > 0) {
                    continue;
                }
                UnifiedLogger.log(UnifiedLogger.LogType.SYSTEM, "执行数据库升级SQL: {}", stepBuildVersion);
                if (!executeSqlList(buildSqlStep.getSqlList())) {
                    UnifiedLogger.error(UnifiedLogger.LogType.SYSTEM, "数据库升级SQL执行失败: {}", stepBuildVersion);
                    return false;
                }
            }
        } catch (IOException e) {
            UnifiedLogger.error(UnifiedLogger.LogType.SYSTEM, "获取SQL更新语句失败:{}", e);
            return false;
        }
        // 列级结构校验：升级 SQL 在历史库上可能因为「半套结构 + build 号已推进」而被整体跳过，
        // 这里按列补一次，保证实体查询用到的列一定存在。
        return ensureRequiredColumns();
    }

    /**
     * 启动必需的列。缺列时实体查询会直接抛 Unknown column，整个应用起不来，
     * 所以这些列在启动阶段无条件校验并补齐。
     * <p>
     * 只收录「缺失就等于起不来」的列，避免把结构修复扩散成一整套重复的迁移脚本。
     */
    private static final List<RequiredColumn> REQUIRED_COLUMNS = List.of(
            new RequiredColumn("master", "node_public_key",
                    "text null comment '节点公钥(PEM证书)，用于核对节点证书与有效期'")
    );

    private boolean ensureRequiredColumns() {
        for (RequiredColumn column : REQUIRED_COLUMNS) {
            if (!ensureColumn(column.tableName, column.columnName, column.definition)) {
                UnifiedLogger.error(UnifiedLogger.LogType.SYSTEM,
                        "数据库结构修复失败: {}.{}", column.tableName, column.columnName);
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean hasRow(String tableName, String primaryKeyName, Object primaryKey) {
        if (StringUtils.isAnyBlank(tableName, primaryKeyName) || primaryKey == null) {
            return false;
        }
        String sql = "select 1 from " + tableName + " where " + primaryKeyName + " = #{primaryKey} limit 1";
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
        if (Boolean.TRUE.equals(columnExists(tableName, columnName))) {
            return true;
        }
        if (Boolean.FALSE.equals(tableExists(tableName))) {
            UnifiedLogger.error(UnifiedLogger.LogType.SYSTEM, "补列失败，表不存在: {}", tableName);
            return false;
        }
        if (columnExists(tableName, columnName) == null && tableExists(tableName) == null) {
            // 两次元数据查询都失败：执行器不是 MySQL/MariaDB，或账号读不到 information_schema。
            // 此时补列语句的「列是否已存在」无从判断，宁可放过也不要在启动路径上盲写。
            UnifiedLogger.warn(UnifiedLogger.LogType.SYSTEM,
                    "无法确认列是否存在，跳过补列: {}.{}", tableName, columnName);
            return true;
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
                            + "where table_schema = database() and table_name = #{tableName} and column_name = #{columnName}",
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
                            + "where table_schema = database() and table_name = #{tableName}",
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

    private static final class RequiredColumn {
        private final String tableName;
        private final String columnName;
        private final String definition;

        private RequiredColumn(String tableName, String columnName, String definition) {
            this.tableName = tableName;
            this.columnName = columnName;
            this.definition = definition;
        }
    }
}
