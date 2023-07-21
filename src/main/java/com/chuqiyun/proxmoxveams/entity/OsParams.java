package com.chuqiyun.proxmoxveams.entity;

import lombok.Data;

/**
 * @author mryunqi
 * @date 2023/7/21
 */
@Data
public class OsParams {
    /**
     * 镜像别名
     */
    private String name;
    /**
     * 镜像全名
     */
    private String fileName;
    /**
     * 镜像类型
     * win，linux
     */
    private String type;
    /**
     * 镜像架构
     * x86_64,arm64,arm64,armhf,ppc64el,riscv64,s390x,aarch64,armv7l
     */
    private String arch;
    /**
     * 镜像操作系统
     * centos,debian,ubuntu,alpine,fedora,opensuse,ubuntukylin,other
     */
    private String osType;
    /**
     * 操作类型
     * 0=url下载;1=手动上传
     */
    private Integer downType;
    /**
     * 镜像下载地址
     */
    private String url;
    /**
     * 下载目录
     */
    private String path;
    /**
     * 是否开启cloud-init
     */
    private Integer cloud;


}
