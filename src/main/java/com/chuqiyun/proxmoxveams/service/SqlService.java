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
}
