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
    private JSONObject rrddata;
}
