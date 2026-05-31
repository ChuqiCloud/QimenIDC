package com.chuqiyun.proxmoxveams.dto;

import lombok.Data;

/**
 * @author mryunqi
 * @date 2023/7/21
 */
@Data
public class OsNodeStatus {
    /**
     * 节点id
     */
    private Integer nodeId;
    /**
     * 节点名称
     */
    private String nodeName;
    /**
     * 下载进度
     */
    private Double schedule;
    /**
     * 状态
     * 0=未下载;1=下载中;2=已下载;3=下载失败
     */
    private Integer status;
    /**
     * 最后进度推进时间，用于判定下载是否卡死
     */
    private Long updateTime;
}
