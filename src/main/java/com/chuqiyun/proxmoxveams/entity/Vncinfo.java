package com.chuqiyun.proxmoxveams.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * (Vncinfo)表实体类
 *
 * @author mryunqi
 * @since 2023-11-22 13:42:50
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "vncinfo")
public class Vncinfo extends Model<Vncinfo> {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    
    private Long hostId;
    
    private Long vmid;
    //控制器连接地址
    private String host;
    //vnc端口
    private Integer port;
    private String username;
    //vnc密码
    private String password;


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

