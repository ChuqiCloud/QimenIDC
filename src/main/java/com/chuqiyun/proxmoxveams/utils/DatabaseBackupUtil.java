package com.chuqiyun.proxmoxveams.utils;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author mryunqi
 * @date 2023/11/26
 */
public class DatabaseBackupUtil {
    /**
    * @Author: mryunqi
    * @Description: 备份数据库
    * @DateTime: 2023/11/26 23:26
    * @Params: String username 数据库用户名
     * @Params: String password 数据库密码
     * @Params: String host 数据库主机地址
     * @Params: int port 数据库端口
     * @Params: String databaseName 数据库名
     * @Params: String backupDirectory 备份文件存储路径
    * @Return boolean 备份结果
    */
    public static boolean backupDatabase(String username, String password, String host, int port, String databaseName, String backupDirectory) {
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String sqlFileName = String.format("%s_%s.sql", databaseName, timestamp);

        String command = String.format(
                "mysqldump -u%s -p%s -h%s -P%d %s > %s/%s",
                username, password, host, port, databaseName, backupDirectory, sqlFileName
        );

        try {
            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                System.out.println("数据库备份成功，文件名：" + sqlFileName);
                return true;
            } else {
                System.out.println("数据库备份失败！");
                return false;
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    // 示例用法
    public static void main(String[] args) {
        // 示例用法
        String username = "your_username";
        String password = "your_password";
        String host = "your_host";
        int port = 3306;
        String databaseName = "your_database";
        String backupDirectory = "/path/to/backup/directory";

        boolean backupResult = backupDatabase(username, password, host, port, databaseName, backupDirectory);
        System.out.println("备份结果: " + backupResult);
    }
}
