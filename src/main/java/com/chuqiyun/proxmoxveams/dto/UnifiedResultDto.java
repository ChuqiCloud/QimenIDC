package com.chuqiyun.proxmoxveams.dto;

import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author mryunqi
 * @date 2023/8/7
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UnifiedResultDto<T> {
    private UnifiedResultCode resultCode;
    private T data;
}
