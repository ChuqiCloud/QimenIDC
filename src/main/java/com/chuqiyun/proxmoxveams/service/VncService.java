package com.chuqiyun.proxmoxveams.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.entity.Vncinfo;
import com.chuqiyun.proxmoxveams.entity.Vncnode;

import java.util.HashMap;

/**
 * @author mryunqi
 * @date 2023/11/24
 */
public interface VncService {
    UnifiedResultDto<Object> getVncInfo(Long hostId, Integer page, Integer limit);

    HashMap<String,String> getVncUrlMap(Vncinfo vncinfo, Page<Vncnode> vncnodePage);

    void syncVncInfo(Vncinfo vncinfo);

    Integer calculateVncPort(String host);
}
