package com.chuqiyun.proxmoxveams.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Map;

/**
 * (Os)表实体类
 *
 * @author mryunqi
 * @since 2023-07-08 15:58:22
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "os",autoResultMap = true)
public class Os extends Model<Os> {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 系统名称（别称）
     */
    private String name;
    /**
     * 镜像全名
     */
    private String fileName;
    /**
     * 镜像类型
     * windows，linux
     */
    private String type;
    /**
     * 镜像架构
     * x86_64,arm64,arm,ppc64le,sparc64,i386,ppc,ppc64,sparc,mips,mipsel,mips64,mips64el,s390x,aarch64,sh4,sh4eb,xtensa,xtensaeb,ppcemb,armeb,armv7l
     */
    private String arch;
    /**
     * 镜像操作系统
     * centos,debian,ubuntu,alpine,fedora,opensuse,archlinux,gentoo,coreos,clearlinux,openeuler,manjaro,raspbian,freebsd,openbsd,netbsd,smartos,other
     */
    private String osType;
    /**
     * 已安装镜像的节点
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String,Object> nodeStatus;
    /**
     * 0=url下载;1=手动上传
     */
    private Integer downType;
    private String url;
    private String size;
    private String path;
    /**
     * cloud-init
     * 0=不使用;1=使用
     */
    private Integer cloud;
    /**
     * 0:正常 1:停用 2:异常
     */
    private Integer status;
    /**
     * 异常原因
     */
    private String reason;
    /**
     * 创建时间
     */
    private Long createTime;


    /**
     * 获取主键值
     *
     * @return 主键值
     */
    @Override
    public Serializable pkVal() {
        return this.id;
    }
    }

