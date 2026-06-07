package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.extension.toolkit.SqlRunner;
import com.chuqiyun.proxmoxveams.common.UnifiedLogger;
import com.chuqiyun.proxmoxveams.service.SqlService;
import com.chuqiyun.proxmoxveams.utils.SqlUpdateLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.List;

/**
 * @author mryunqi
 * @date 2023/11/26
 */
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
        // 如果数据库中的版本号为空，说明旧数据没有记录build，交给启动流程写入当前build
        if (dbBuildVersion == null) {
            return true;
        }
        // 如果数据库中的版本号与当前版本号相同，直接返回true
        if (dbBuildVersion.equals(nowBuildVersion)) {
            return true;
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
                String stepBuildVersion = buildSqlStep.getBuildVersion() + "_" + buildSqlStep.getIteration();
                if (SqlUpdateLoader.compareBuildVersion(stepBuildVersion, dbBuildVersion) <= 0
                        || SqlUpdateLoader.compareBuildVersion(stepBuildVersion, nowBuildVersion) > 0) {
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
        return true;
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
