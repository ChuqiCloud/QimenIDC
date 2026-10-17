package com.chuqiyun.proxmoxveams.service;

/**
 * @author mryunqi
 * @date 2023/11/26
 */
public interface SqlService {
    boolean executeSql(String sql);

    boolean testConnection();

    boolean installDbSql();

    boolean checkAndUpdateDatabase();

    /**
     * 主键所在一行是否存在（例如 config 表是否已有 id=1 的记录）。
     *
     * @param tableName      表名
     * @param primaryKeyName 主键列名
     * @param primaryKey     主键值
     * @return true 存在
     */
    boolean hasRow(String tableName, String primaryKeyName, Object primaryKey);

    /**
     * 补列：列已存在时跳过，列缺失则按 alter table add column 建列。
     * <p>
     * 用 information_schema 判断列是否存在，执行器与 MySQL/MariaDB 无关，
     * 便于对历史库做「缺什么补什么」的幂等修复，不必依赖数据库里记录的 build 版本号。
     *
     * @param tableName  表名
     * @param columnName 列名
     * @param definition 列定义（不含列名），例如 {@code text null comment '节点公钥'}
     * @return 列已存在或补列成功返回 true
     */
    boolean ensureColumn(String tableName, String columnName, String definition);
}
