package com.chuqiyun.proxmoxveams.service;

import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.dto.VmParams;

/**
 * @author mryunqi
 * @date 2023/8/6
 */
public interface CreateVmService {
    UnifiedResultDto<Object> createPveVmToParams(VmParams vmParams,boolean isCreateVm);

    Integer createPveVm(VmParams vmParams, Integer vmid);
}
