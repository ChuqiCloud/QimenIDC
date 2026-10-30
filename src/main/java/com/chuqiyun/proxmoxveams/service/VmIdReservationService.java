package com.chuqiyun.proxmoxveams.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @Author: 星禾
 * @Description: 创建和迁移共用的数据库级 VMID 预留，失败任务保留资源直到确认清理。
 * @DateTime: 2026/10/30 11:44
 */
@Service
public class VmIdReservationService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final MasterService masterService;

    public VmIdReservationService(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                                  MasterService masterService) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
        this.masterService = masterService;
    }

    public int reserve(Integer nodeId, Integer requestedVmid, String owner) {
        return reserve(nodeId, requestedVmid, owner, null);
    }

    public int reserve(Integer nodeId, Integer requestedVmid, String owner, Integer hostId) {
        if (nodeId == null || nodeId <= 0 || (hostId != null && hostId <= 0)
                || owner == null || owner.isBlank() || owner.length() > 100
                || (requestedVmid != null && (requestedVmid < 100 || requestedVmid > 999999999))) {
            throw new IllegalStateException("VMID预留参数无效，VMID必须为100至999999999");
        }
        // 查询失败时必须停止，不能只凭主控数据库推断整个 PVE 集群空闲。
        ClusterState cluster = loadClusterState(nodeId);
        return Objects.requireNonNull(transaction.execute(status -> {
            Integer lock = jdbc.queryForObject("select id from vm_id_allocation_lock where id = 1 for update", Integer.class);
            if (!Integer.valueOf(1).equals(lock)) {
                throw new IllegalStateException("VMID分配锁不存在，请完成数据库升级");
            }
            Integer sourceNodeId = null;
            if (hostId != null) {
                List<Map<String, Object>> hosts = jdbc.queryForList(
                        "select id, nodeid, delete_state from vmhost where id = ? for update", hostId);
                if (hosts.isEmpty() || (integer(hosts.get(0).get("delete_state")) != null
                        && integer(hosts.get(0).get("delete_state")) != 0)) {
                    throw new IllegalStateException("源虚拟机不存在或已进入回收站，不能迁移");
                }
                sourceNodeId = integer(hosts.get(0).get("nodeid"));
            }
            List<Map<String, Object>> existing = jdbc.queryForList(
                    "select node_id, vmid, host_id from vm_id_reservation where owner = ?", owner);
            if (!existing.isEmpty()) {
                Map<String, Object> row = existing.get(0);
                int vmid = ((Number) row.get("vmid")).intValue();
                if (((Number) row.get("node_id")).intValue() != nodeId
                        || (requestedVmid != null && requestedVmid != vmid)
                        || !Objects.equals(hostId, integer(row.get("host_id")))) {
                    throw new IllegalStateException("任务的VMID预留与本次参数不一致");
                }
                return vmid;
            }
            if (hostId != null && jdbc.queryForObject(
                    "select count(*) from vm_id_reservation where host_id = ?", Integer.class, hostId) > 0) {
                throw new IllegalStateException("该虚拟机已有迁移任务预留资源，失败任务请从原任务继续");
            }
            if (hostId != null && jdbc.queryForObject(
                    "select count(*) from task where hostid = ? and status in (0, 1, 4)", Integer.class, hostId) > 0) {
                throw new IllegalStateException("该虚拟机还有未完成的操作任务，请等待任务结束后再迁移");
            }
            Set<Integer> nodeIds = managedClusterNodeIds(nodeId, cluster.nodeNames);
            Set<Integer> used = new HashSet<>(cluster.vmids);
            for (Map<String, Object> row : jdbc.queryForList(
                    "select node_id, vmid from vm_id_reservation")) {
                if (nodeIds.contains(integer(row.get("node_id")))) {
                    used.add(integer(row.get("vmid")));
                }
            }
            // 回收站里的 VM 仍可能存在于 PVE，只有已完成删除的记录可忽略。
            for (Map<String, Object> row : jdbc.queryForList(
                    "select nodeid, vmid from vmhost where delete_state is null or delete_state <> 2")) {
                if (nodeIds.contains(integer(row.get("nodeid"))) && integer(row.get("vmid")) != null) {
                    used.add(integer(row.get("vmid")));
                }
            }
            int vmid;
            if (requestedVmid != null) {
                vmid = requestedVmid;
                if (used.contains(vmid)) {
                    throw new IllegalStateException("目标VMID已存在或已被其他创建/迁移任务预留: " + vmid);
                }
            } else {
                int maximum = used.stream().filter(Objects::nonNull).mapToInt(Integer::intValue).max().orElse(100);
                if (maximum >= 999999999) {
                    throw new IllegalStateException("PVE集群没有可自动分配的VMID，请指定空闲VMID");
                }
                vmid = Math.max(100, maximum) + 1;
            }
            jdbc.update("insert into vm_id_reservation(node_id, vmid, owner, host_id, source_node_id, create_time) values (?, ?, ?, ?, ?, ?)",
                    nodeId, vmid, owner, hostId, sourceNodeId, System.currentTimeMillis());
            return vmid;
        }));
    }

    /** 重试时仅校验任务归属；目标已有本任务恢复的 VM 属于正常情况。 */
    public void assertOwned(Integer nodeId, Integer vmid, String owner) {
        if (nodeId == null || vmid == null || owner == null || jdbc.queryForObject(
                "select count(*) from vm_id_reservation where node_id = ? and vmid = ? and owner = ?",
                Integer.class, nodeId, vmid, owner) != 1) {
            throw new IllegalStateException("任务缺少有效的目标VMID预留，请勿复用旧迁移任务");
        }
    }

    public boolean isHostReserved(Number hostId) {
        return hostId != null && jdbc.queryForObject("select count(*) from vm_id_reservation where host_id = ?",
                Integer.class, hostId.intValue()) > 0;
    }

    public boolean isNodeInMigration(Integer nodeId) {
        return nodeId != null && jdbc.queryForObject("select count(*) from vm_id_reservation "
                        + "where host_id is not null and (node_id = ? or source_node_id = ?)",
                Integer.class, nodeId, nodeId) > 0;
    }

    /** 与迁移入队握手：已有节点同步结束后才能提交迁移，已提交迁移则跳过节点同步。 */
    public void withAllocationLock(Runnable action) {
        transaction.executeWithoutResult(status -> {
            jdbc.queryForObject("select id from vm_id_allocation_lock where id = 1 for update", Integer.class);
            action.run();
        });
    }

    /** 锁定源虚拟机行，与迁移预留互斥，不持有全局VMID分配锁。 */
    public boolean lockAndIsHostReserved(Number hostId) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("虚拟机变更必须在事务中检查迁移占用");
        }
        jdbc.queryForList("select id from vmhost where id = ? for update", Integer.class,
                hostId == null ? null : hostId.intValue());
        return !jdbc.queryForList("select owner from vm_id_reservation where host_id = ? for update",
                String.class, hostId == null ? null : hostId.intValue()).isEmpty();
    }

    /** 仅在成功提交到 vmhost 或尚未执行的任务安全取消后释放。 */
    public void release(String owner) {
        if (owner != null) {
            jdbc.update("delete from vm_id_reservation where owner = ?", owner);
        }
    }

    private Set<Integer> managedClusterNodeIds(Integer nodeId, Set<String> nodeNames) {
        Set<Integer> ids = new LinkedHashSet<>();
        ids.add(nodeId);
        for (Map<String, Object> row : jdbc.queryForList("select id, node_name from master")) {
            if (nodeNames.contains(String.valueOf(row.get("node_name")))) {
                ids.add(integer(row.get("id")));
            }
        }
        return ids;
    }

    protected ClusterState loadClusterState(Integer nodeId) {
        Master node = masterService.getById(nodeId);
        if (node == null || node.getNodeName() == null || node.getNodeName().isBlank()) {
            throw new IllegalStateException("目标PVE节点不存在或节点名称为空");
        }
        ProxmoxApiUtil api = new ProxmoxApiUtil();
        HashMap<String, String> cookies = masterService.getMasterCookieMap(nodeId);
        JSONObject resourceResponse = api.getNodeApi(node, cookies, "/cluster/resources", new HashMap<>());
        JSONObject nodeResponse = api.getNodeApi(node, cookies, "/nodes", new HashMap<>());
        JSONArray resources = resourceResponse == null ? null : resourceResponse.getJSONArray("data");
        JSONArray nodes = nodeResponse == null ? null : nodeResponse.getJSONArray("data");
        if (resources == null || nodes == null || nodes.isEmpty()) {
            throw new IllegalStateException("无法查询PVE集群资源，已拒绝分配VMID");
        }
        Set<String> nodeNames = new LinkedHashSet<>();
        Set<Integer> vmids = new HashSet<>();
        for (int i = 0; i < nodes.size(); i++) {
            String name = nodes.getJSONObject(i).getString("node");
            if (name == null || name.isBlank()) {
                throw new IllegalStateException("PVE集群节点信息不完整，已拒绝分配VMID");
            }
            nodeNames.add(name);
        }
        if (!nodeNames.contains(node.getNodeName())) {
            throw new IllegalStateException("PVE集群节点信息与主控不一致，已拒绝分配VMID");
        }
        for (int i = 0; i < resources.size(); i++) {
            JSONObject resource = resources.getJSONObject(i);
            if ("qemu".equals(resource.getString("type")) || "lxc".equals(resource.getString("type"))) {
                Integer vmid = resource.getInteger("vmid");
                if (vmid == null || vmid < 100) {
                    throw new IllegalStateException("PVE集群VMID信息不完整，已拒绝分配VMID");
                }
                vmids.add(vmid);
            }
        }
        return new ClusterState(nodeNames, vmids);
    }

    private static Integer integer(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    protected static class ClusterState {
        private final Set<String> nodeNames;
        private final Set<Integer> vmids;

        protected ClusterState(Set<String> nodeNames, Set<Integer> vmids) {
            this.nodeNames = nodeNames;
            this.vmids = vmids;
        }
    }
}
