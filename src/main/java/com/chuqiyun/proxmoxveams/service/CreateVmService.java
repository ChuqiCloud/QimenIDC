package com.chuqiyun.proxmoxveams.service;

import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.dto.VmParams;

/**
 * @author mryunqi
 * @date 2023/8/6
 */
public interface CreateVmService {
    UnifiedResultDto<?> createPveVm(VmParams vmParams);
}
