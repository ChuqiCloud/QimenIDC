package com.chuqiyun.proxmoxveams.dto;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.entity.Os;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import lombok.Data;

/**
 * @author mryunqi
 * @date 2023/8/24
 */
@Data
public class VmHostDto {
    private String nodeName;
    private String area;
    private Vmhost vmhost;
    private Os os;
    private JSONObject current;
    /**
     * 虚拟机累计运行时间，单位：秒；虚拟机关机或状态获取失败时为 null
     */
    private Long uptime;
    private JSONObject rrddata;
    private JSONObject diskUsage;
}
