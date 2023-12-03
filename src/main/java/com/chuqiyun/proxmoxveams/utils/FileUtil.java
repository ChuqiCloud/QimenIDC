package com.chuqiyun.proxmoxveams.utils;

import java.io.File;

/**
 * @author mryunqi
 * @date 2023/12/3
 */
public class FileUtil {
    /**
    * @Author: mryunqi
    * @Description: 判断运行目录下/config/install.lock文件是否存在
    * @DateTime: 2023/12/3 15:54
    * @Return boolean true:存在  false:不存在
    */
    public static boolean isInstall(){
        // 获取运行目录
        String currentDir = System.getProperty("user.dir");
        // 拼接文件路径
        String filePath = currentDir + File.separator + "config" + File.separator + "install.lock";
        // 创建文件对象
        File installLockFile = new File(filePath);
        // 判断文件是否存在
        return installLockFile.exists();
    }

    /**
    * @Author: mryunqi
    * @Description: 创建install.lock文件
    * @DateTime: 2023/12/3 15:57
    * @Return boolean true:创建成功  false:创建失败
    */
    public static boolean createInstallLock(){
        // 获取运行目录
        String currentDir = System.getProperty("user.dir");
        // 拼接文件路径
        String filePath = currentDir + File.separator + "config" + File.separator + "install.lock";
        // 创建文件对象
        File installLockFile = new File(filePath);
        // 判断文件是否存在
        if(installLockFile.exists()){
            return true;
        }
        // 创建文件
        try {
            return installLockFile.createNewFile();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
