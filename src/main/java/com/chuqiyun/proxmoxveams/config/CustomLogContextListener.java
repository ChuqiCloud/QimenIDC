package com.chuqiyun.proxmoxveams.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggerContextListener;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.spi.ContextAwareBase;
import ch.qos.logback.core.spi.LifeCycle;

/**
 * @version 1.0
 * @class: CustomLogContextListener
 * @author: mryunqi
 * @mail: 434658198@qq.com
 * @date: 2022/10/10 10:01
 * @description: 定义logback 日志监听器，指定日志文件存放目录
 */
public class CustomLogContextListener extends ContextAwareBase implements LoggerContextListener, LifeCycle {

    /** 存储日志路径标识 */
    public static final String LOG_PAHT_KEY = "LOG_PATH";
    /** 日志保留天数标识 */
    public static final String LOG_RETAIN_DAYS_KEY = "LOG_RETAIN_DAYS";
    /** 日志文件最大大小天数标识 */
    public static final String LOG_MAX_TOTAL_FILE_SIZE_KEY = "LOG_MAX_TOTAL_FILE_SIZE";
    /** 单日志文件最大大小标识 */
    public static final String LOG_MAX_FILE_SIZE_KEY = "LOG_MAX_FILE_SIZE";
    /** 指定日期删除标识 */
    public static final String LOG_CLEAN_HISTORY_ON_START_KEY = "LOG_CLEAN_HISTORY_ON_START";


    @Override
    public boolean isResetResistant() {
        return false;
    }

    @Override
    public void onStart(LoggerContext loggerContext) {

    }

    @Override
    public void onReset(LoggerContext loggerContext) {

    }

    @Override
    public void onStop(LoggerContext loggerContext) {

    }

    @Override
    public void onLevelChange(Logger logger, Level level) {

    }

    @Override
    public void start() {
        // "user.dir"是指用户当前工作目录
        String s = System.getProperty("user.dir");
        System.setProperty(LOG_PAHT_KEY, s);
        Context context = getContext();
        context.putProperty(LOG_PAHT_KEY,  s);
    }

    @Override
    public void stop() {

    }

    @Override
    public boolean isStarted() {
        return false;
    }
}