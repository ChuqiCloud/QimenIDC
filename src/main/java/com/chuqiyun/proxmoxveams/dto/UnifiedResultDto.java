package com.chuqiyun.proxmoxveams.dto;

import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author mryunqi
 * @date 2023/8/7
 */
@Data
@NoArgsConstructor
public class UnifiedResultDto<T> {
    private UnifiedResultCode resultCode;
    private T data;

    private String message;

    public UnifiedResultDto(UnifiedResultCode resultCode, T data) {
        this.resultCode = resultCode;
        this.data = data;
        this.message = resultCode == null ? null : resultCode.getMessage();
    }

    public UnifiedResultDto(UnifiedResultCode resultCode, T data, String message) {
        this.resultCode = resultCode;
        this.data = data;
        this.message = message;
    }

    public String getMessage() {
        if (message != null && !message.trim().isEmpty()) {
            return message;
        }
        return resultCode == null ? null : resultCode.getMessage();
    }
}
