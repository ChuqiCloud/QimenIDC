package com.chuqiyun.proxmoxveams.cron;

import com.chuqiyun.proxmoxveams.common.UnifiedLogger;
import com.chuqiyun.proxmoxveams.entity.Config;
import com.chuqiyun.proxmoxveams.entity.Sysuser;
import com.chuqiyun.proxmoxveams.manager.TrustedNetworkManager;
import com.chuqiyun.proxmoxveams.service.ConfigService;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.SqlService;
import com.chuqiyun.proxmoxveams.service.SysuserService;
import com.chuqiyun.proxmoxveams.security.StoredCredentialMigration;
import com.chuqiyun.proxmoxveams.utils.FileUtil;
import com.chuqiyun.proxmoxveams.utils.UUIDUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Objects;

/**
 * @author mryunqi
 * @date 2023/6/18
 */
@Slf4j
@Component
public class StartUp {
    @Resource
    private ConfigService configService;
    @Resource
    private MasterService masterService;
    @Resource
    private SysuserService sysuserService;
    @Resource
    private SqlService sqlService;
    @Resource
    private StoredCredentialMigration storedCredentialMigration;
    @Resource
    private TrustedNetworkManager trustedNetworkManager;
    /**
     * 内部版号
     */
    @Value("${info.application.build.version}")
    private String nowBuildVersion;
    @PostConstruct
    public void init() {
        // 判断数据库连接是否正常
        if (!sqlService.testConnection()){
            UnifiedLogger.error(UnifiedLogger.LogType.SYSTEM, "数据库连接异常，请检查数据库连接配置");
            System.exit(0); // 退出程序
        }

        // 判断config文件夹是否存在
        if (!FileUtil.isConfigDir()){
            UnifiedLogger.error(UnifiedLogger.LogType.SYSTEM, "config文件夹不存在，请检查安装是否正确");
            System.exit(0); // 退出程序
        }

        boolean isInstalled = FileUtil.isInstall();// 文件锁
        /*boolean isInstalledDb = false;//configService.getInstalled(); // 数据库锁
        // 二者必须都为false，才能进行安装
        if (!isInstalled && !isInstalledDb){
            UnifiedLogger.log(UnifiedLogger.LogType.SYSTEM, "检测到未初始化数据库，正在初始化");
            // 安装数据库
            boolean install = sqlService.installDbSql();
            if (install){
                UnifiedLogger.log(UnifiedLogger.LogType.SYSTEM, "数据库初始化成功");
                // 设置已经安装数据库
                configService.setInstalled(true);
            }else {
                UnifiedLogger.error(UnifiedLogger.LogType.SYSTEM, "数据库初始化失败");
                System.exit(0); // 退出程序
            }
            // 设置内部版号
            if (!configService.setBuild(nowBuildVersion)){
                UnifiedLogger.error(UnifiedLogger.LogType.SYSTEM, "内部版号设置失败");
                System.exit(0); // 退出程序
            }
        }*/
        /*else {
            // 再次同步数据库锁和文件锁
            configService.setInstalled(true);
            FileUtil.createInstallLock();
        }*/

        // 判断是否第一次启动。文件锁可能丢（换机、重装、容器卷重建），
        // 直接 initConfig 会往 config 表重复插 id=1 而报主键冲突，所以先看数据行在不在。
        if (!isInstalled){
            if (sqlService.hasRow("config", "id", 1)){
                UnifiedLogger.warn(UnifiedLogger.LogType.SYSTEM, "文件锁丢失但配置表已存在，跳过初始化");
            } else {
                configService.initConfig(); // 初始化配置
            }
            FileUtil.createInstallLock(); // 创建文件锁
        }

        // 检测并更新数据库
        UnifiedLogger.log(UnifiedLogger.LogType.SYSTEM, "正在检测数据库是否需要更新");
        if (!sqlService.checkAndUpdateDatabase()){
            UnifiedLogger.error(UnifiedLogger.LogType.SYSTEM, "数据库更新失败");
            System.exit(0); // 退出程序
        }else{
            // 先迁移历史凭据，再记录新版本，失败会中止启动和节点预热。
            storedCredentialMigration.migrate();
            String dbBuildVersion = configService.getBuild();
            if (!Objects.equals(dbBuildVersion, nowBuildVersion) && !configService.setBuild(nowBuildVersion)){
                UnifiedLogger.error(UnifiedLogger.LogType.SYSTEM, "内部版号设置失败");
                System.exit(0); // 退出程序
            }
        }

        // 判断是否没有管理员账号
        if (sysuserService.count() == 0){
            UnifiedLogger.log(UnifiedLogger.LogType.SYSTEM, "检测到没有管理员账号，正在初始化");
            Sysuser user = sysuserService.insertInitSysuser();
            UnifiedLogger.log(UnifiedLogger.LogType.SYSTEM, "初始化管理员账号成功: username: {}, password: {}", user.getUsername(), user.getPassword());
        }
        // 初始化受控端token
        Config config = configService.getById(1);
        String token = UUIDUtil.getUUIDByThread();
        if (config == null){
            Config newConfig = new Config();
            newConfig.setId(1);
            newConfig.setToken(token);
            configService.save(newConfig);
        }
        // 刷新数据
        config = configService.getById(1);
        assert config != null;
        if (Objects.equals(config.getToken(), "0")){
            config.setToken(token);
            configService.updateById(config);
        }
        //log.info("[System] 集群预热开始");
        UnifiedLogger.log(UnifiedLogger.LogType.SYSTEM, "集群预热开始");
        // 预热所有节点cookie
        masterService.updateAllNodeCookie();
        //log.info("[System] 集群预热结束");
        UnifiedLogger.log(UnifiedLogger.LogType.SYSTEM, "集群预热结束");
        // 被控端 nginx 按管理源白名单放行，启动时先把集群内所有主控出口下发一遍，
        // 否则换了出口IP的主控会一直卡在连接超时。失败不影响启动，由心跳周期补偿。
        try {
            trustedNetworkManager.initialSync();
        } catch (Exception e) {
            UnifiedLogger.warn(UnifiedLogger.LogType.SYSTEM, "可信白名单启动同步失败: {}", e.getMessage());
        }
    }
}
