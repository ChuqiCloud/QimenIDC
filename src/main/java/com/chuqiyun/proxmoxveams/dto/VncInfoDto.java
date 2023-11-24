package com.chuqiyun.proxmoxveams.dto;

import lombok.Data;

/**
 * @author mryunqi
 * @date 2023/11/24
 */
@Data
public class VncInfoDto {
    // total
    private Long total;
    // current
    private Long current;
    // pages
    private Long pages;
    // size
    private Long size;
    // records
    private Object records;
}
