package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.extension.toolkit.SqlRunner;
import com.chuqiyun.proxmoxveams.common.UnifiedLogger;
import com.chuqiyun.proxmoxveams.service.SqlService;
import com.chuqiyun.proxmoxveams.utils.ModUtil;
import com.chuqiyun.proxmoxveams.utils.SqlUpdateLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
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
        // 如果数据库中的版本号为空，直接返回false
        if (dbBuildVersion == null) {
            return false;
        }
        // 如果数据库中的版本号与当前版本号相同，直接返回true
        if (dbBuildVersion.equals(nowBuildVersion)) {
            return true;
        }
        // 1.0.7_3
        String[] dbBuildVersionArray = dbBuildVersion.split("_");
        // 1.0.7
        String dbVersion = dbBuildVersionArray[0];
        int dbVersionInt = Integer.parseInt(dbVersion.replace(".", ""));
        // 3
        int dbIteration = Integer.parseInt(dbBuildVersionArray[1]);
        // 当前版号1.0.7_10
        String[] nowBuildVersionArray = nowBuildVersion.split("_");
        // 1.0.7
        String nowVersion = nowBuildVersionArray[0];
        int nowVersionInt = Integer.parseInt(nowVersion.replace(".", ""));
        // 10
        int nowIteration = Integer.parseInt(nowBuildVersionArray[1]);
        ApplicationContext context = new AnnotationConfigApplicationContext();
        SqlUpdateLoader sqlUpdateLoader = new SqlUpdateLoader(context);
        // 对比大版本号
        if (!dbVersion.equals(nowVersion)) {
            // 判断当前版本号是否大于数据库中的版本号
            if (nowVersionInt > dbVersionInt) {
                // 如果当前版本号大于数据库中的版本号，计算出相差的迭代号，规则满10进1
                int versionDifference = nowVersionInt - dbVersionInt;
                // 如果数据库中的迭代号dbIteration为9，则大版号dbVersionInt进1，迭代号dbIteration重置为0
                if (dbIteration == 9) {
                    dbVersionInt++;
                    dbIteration = 0;
                    // versionDifference减1
                    versionDifference--;
                }
                // 如果nowVersionInt等于当前dbVersionInt，先判断迭代号，如果迭代号相等，直接返回true
                if (nowVersionInt == dbVersionInt) {
                    if (nowIteration == dbIteration) {
                        // 相同则获取当前版号的SQL语句，执行并返回结果
                        try {
                            List<String> sqlUpdates = sqlUpdateLoader.getSqlUpdates("build",nowVersion, nowIteration);
                            // 判空
                            if (sqlUpdates == null) {
                                return true;
                            }
                            for (String sqlUpdate : sqlUpdates) {
                                this.executeSql(sqlUpdate);
                            }
                        } catch (IOException e) {
                            UnifiedLogger.error(UnifiedLogger.LogType.SYSTEM, "获取SQL更新语句失败:{}", e);
                            return false;
                        }
                    }
                    // 如果迭代号不相等，计算出相差的迭代号，规则满10进1
                    int iterationDifference = nowIteration - dbIteration;
                    // 循环迭代号差值，获取SQL语句，执行并返回结果
                    for (int i = 0; i <= iterationDifference; i++) {
                        try {
                            List<String> sqlUpdates = sqlUpdateLoader.getSqlUpdates("build",nowVersion, dbIteration + i + 1);
                            // 判空
                            if (sqlUpdates == null) {
                                continue;
                            }
                            for (String sqlUpdate : sqlUpdates) {
                                this.executeSql(sqlUpdate);
                            }
                        } catch (IOException e) {
                            UnifiedLogger.error(UnifiedLogger.LogType.SYSTEM, "获取SQL更新语句失败:{}", e);
                            return false;
                        }
                    }
                    return true; // 执行完毕，返回true
                }
                // 如果nowVersionInt大于当前dbVersionInt，说明更新版本跨度大，需要逐级迭代
                if (nowVersionInt > dbVersionInt) {
                    // 循环版本号差值，获取SQL语句，执行并返回结果
                    for (int i = 0; i <= versionDifference; i++) {
                        // 如果i等于0，说明是第一次循环，需要从当前数据库中的迭代号开始，循环到9，大版号进1，迭代号重置为0
                        if (i == 0) {
                            // 第一次循环，从当前数据库中的迭代号开始，循环到9
                            for (int j = dbIteration; j < 10; j++) {
                                // 判断当前版号是否等于当前数据库中的版号，如果等于，则跳过
                                if ((ModUtil.versionToString(dbVersionInt + i)+"_"+j).equals(dbBuildVersion)) {
                                    continue;
                                }
                                try {
                                    List<String> sqlUpdates = sqlUpdateLoader.getSqlUpdates("build", ModUtil.versionToString(dbVersionInt + i), j);
                                    // 判空
                                    if (sqlUpdates == null) {
                                        continue;
                                    }
                                    for (String sqlUpdate : sqlUpdates) {
                                        this.executeSql(sqlUpdate);
                                    }
                                } catch (IOException e) {
                                    UnifiedLogger.error(UnifiedLogger.LogType.SYSTEM, "获取SQL更新语句失败:{}", e);
                                    return false;
                                }
                            }
                            // 迭代号重置为0
                            dbIteration = 0;
                        }
                        else {
                            // 要判断大版号是否等于当前版本号，如果等于，循环到当前版本号的迭代号
                            if (dbVersionInt+i == nowVersionInt) {
                                // 循环到当前版本号的迭代号
                                for (int j = 0; j <= nowIteration; j++) {
                                    try {
                                        List<String> sqlUpdates = sqlUpdateLoader.getSqlUpdates("build", ModUtil.versionToString(dbVersionInt + i), j);
                                        // 判空
                                        if (sqlUpdates == null) {
                                            continue;
                                        }
                                        for (String sqlUpdate : sqlUpdates) {
                                            this.executeSql(sqlUpdate);
                                        }
                                    } catch (IOException e) {
                                        UnifiedLogger.error(UnifiedLogger.LogType.SYSTEM, "获取SQL更新语句失败:{}", e);
                                        return false;
                                    }
                                }
                            }
                            else {
                                // 循环到9
                                for (int j = 0; j < 10; j++) {
                                    try {
                                        List<String> sqlUpdates = sqlUpdateLoader.getSqlUpdates("build", ModUtil.versionToString(dbVersionInt + i), j);
                                        // 判空
                                        if (sqlUpdates == null) {
                                            continue;
                                        }
                                        for (String sqlUpdate : sqlUpdates) {
                                            this.executeSql(sqlUpdate);
                                        }
                                    } catch (IOException e) {
                                        UnifiedLogger.error(UnifiedLogger.LogType.SYSTEM, "获取SQL更新语句失败:{}", e);
                                        return false;
                                    }
                                }
                            }
                        }
                    }
                    return true; // 执行完毕，返回true
                }else {
                    // 如果nowVersionInt小于当前dbVersionInt，直接返回false
                    UnifiedLogger.error(UnifiedLogger.LogType.SYSTEM, "版本号异常，当前版本号小于数据库中的版本号");
                    return false;
                }
            } else {
                // 如果当前版本号小于数据库中的版本号，直接返回false
                return false;
            }
        }
        // 大版号相同
        else {
            // 判断迭代号是否相同
            if (nowIteration == dbIteration) {
                // 如果迭代号相同，直接返回false
                return true;
            }
            // 判断迭代号是否小于数据库中的迭代号
            if (nowIteration < dbIteration) {
                // 如果迭代号小于数据库中的迭代号，直接返回false
                UnifiedLogger.error(UnifiedLogger.LogType.SYSTEM, "版本号异常，当前版本号小于数据库中的版本号");
                return false;
            }
            // 如果迭代号不相同，计算出相差的迭代号，规则满10进1
            int iterationDifference = nowIteration - dbIteration;
            // 循环迭代号差值，获取SQL语句，执行并返回结果
            for (int i = 0; i < iterationDifference; i++) {
                try {
                    List<String> sqlUpdates = sqlUpdateLoader.getSqlUpdates("build",nowVersion, dbIteration + i + 1);
                    // 判空
                    if (sqlUpdates == null) {
                        continue;
                    }
                    for (String sqlUpdate : sqlUpdates) {
                        this.executeSql(sqlUpdate);
                    }
                } catch (IOException e) {
                    UnifiedLogger.error(UnifiedLogger.LogType.SYSTEM, "获取SQL更新语句失败:{}", e);
                    return false;
                }
            }
        }
        return true;

    }
}
