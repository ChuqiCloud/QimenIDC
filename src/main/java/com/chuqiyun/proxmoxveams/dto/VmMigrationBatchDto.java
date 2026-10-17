package com.chuqiyun.proxmoxveams.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量迁移提交结果：逐台给出任务ID或拒绝原因。
 */
@Data
public class VmMigrationBatchDto {
    private String batchId;
    private Integer targetNodeId;
    private String targetStorage;
    private List<Item> items = new ArrayList<>();
    private int acceptedCount;
    private int rejectedCount;

    @Data
    public static class Item {
        private Integer hostId;
        private Integer taskId;
        private String status;
        private String message;

        public static Item accepted(Integer hostId, Integer taskId) {
            Item item = new Item();
            item.hostId = hostId;
            item.taskId = taskId;
            item.status = "QUEUED";
            return item;
        }

        public static Item rejected(Integer hostId, String message) {
            Item item = new Item();
            item.hostId = hostId;
            item.status = "REJECTED";
            item.message = message;
            return item;
        }
    }
}
