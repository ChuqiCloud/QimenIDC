package com.chuqiyun.proxmoxveams.cron;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.dto.NatOperationResult;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.ConfigService;
import com.chuqiyun.proxmoxveams.service.IppoolService;
import com.chuqiyun.proxmoxveams.service.NatForwardSyncService;
import com.chuqiyun.proxmoxveams.service.SecurityGroupBusinessService;
import com.chuqiyun.proxmoxveams.service.SubnetpoolService;
import com.chuqiyun.proxmoxveams.service.TaskService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeleteVmCronTest {
    private NatForwardSyncService natForwardSyncService;
    private VmhostService vmhostService;
    private ConfigService configService;
    private IppoolService ippoolService;
    private SubnetpoolService subnetpoolService;
    private SecurityGroupBusinessService securityGroupBusinessService;
    private TaskService taskService;
    private List<String> operations;
    private TestDeleteVmCron cron;

    @BeforeEach
    void setUp() {
        natForwardSyncService = mock(NatForwardSyncService.class);
        vmhostService = mock(VmhostService.class);
        configService = mock(ConfigService.class);
        ippoolService = mock(IppoolService.class);
        subnetpoolService = mock(SubnetpoolService.class);
        securityGroupBusinessService = mock(SecurityGroupBusinessService.class);
        taskService = mock(TaskService.class);
        operations = new ArrayList<>();
        cron = new TestDeleteVmCron(operations);
        ReflectionTestUtils.setField(cron, "natForwardSyncService", natForwardSyncService);
        ReflectionTestUtils.setField(cron, "vmhostService", vmhostService);
        ReflectionTestUtils.setField(cron, "configService", configService);
        ReflectionTestUtils.setField(cron, "ippoolService", ippoolService);
        ReflectionTestUtils.setField(cron, "subnetpoolService", subnetpoolService);
        ReflectionTestUtils.setField(cron, "securityGroupBusinessService", securityGroupBusinessService);
        ReflectionTestUtils.setField(cron, "taskService", taskService);
    }

    @Test
    void deletesRemoteNatBeforeRemovingLocalRule() throws Exception {
        Task task = new Task();
        task.setHostid(17);
        Master node = new Master();
        node.setId(3);
        when(natForwardSyncService.getPortRulesByHost(17, 1, 500)).thenReturn(portRules(17));
        when(natForwardSyncService.getPortRulesByHost(17, 2, 500)).thenReturn(new JSONArray());
        when(natForwardSyncService.deletePortRule(3, "203.0.113.8", 8443, "tcp")).thenAnswer(invocation -> {
            operations.add("local");
            return true;
        });

        invokePrivate("deletePortNatForwards", new Class<?>[]{Task.class, Master.class}, task, node);

        assertEquals(List.of("remote", "local"), operations);
    }

    @Test
    void keepsLocalNatRuleWhenRemoteDeletionFails() throws Exception {
        Task task = new Task();
        task.setHostid(17);
        Master node = new Master();
        node.setId(3);
        when(natForwardSyncService.getPortRulesByHost(17, 1, 500)).thenReturn(portRules(17));
        cron.remoteDeleteSucceeds = false;

        InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                () -> invokePrivate("deletePortNatForwards", new Class<?>[]{Task.class, Master.class}, task, node));

        assertEquals(IllegalStateException.class, thrown.getCause().getClass());
        assertEquals(List.of("remote"), operations);
        verify(natForwardSyncService, never()).deletePortRule(3, "203.0.113.8", 8443, "tcp");
    }

    @Test
    void refusesVpcCleanupWhenResponseIsUnsuccessful() throws Exception {
        Vmhost vmhost = new Vmhost();
        vmhost.setId(17);
        vmhost.setNetworkType("vpc");
        when(vmhostService.getVmhostVpcIpForward(17)).thenReturn(ResponseResult.fail("unavailable"));

        InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                () -> invokePrivate("deleteVpcIpForwards", new Class<?>[]{Vmhost.class}, vmhost));

        assertEquals(IllegalStateException.class, thrown.getCause().getClass());
        verify(vmhostService, never()).delVmhostVpcIpForward(17, "198.51.100.10", "10.0.0.17");
    }

    @Test
    void includesVpcCleanupFailureReasonInDeleteError() throws Exception {
        Vmhost vmhost = vpcVmhost();
        when(vmhostService.getVmhostVpcIpForward(17)).thenReturn(ResponseResult.ok(List.of(
                Map.of("publicIp", "198.51.100.10", "privateIp", "10.0.0.17"))));
        when(vmhostService.delVmhostVpcIpForwardWithResult(17, "198.51.100.10", "10.0.0.17"))
                .thenReturn(NatOperationResult.failure("QAgent 删除IP转发失败（code=500）：iptables 链不存在"));

        InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                () -> invokePrivate("deleteVpcIpForwards", new Class<?>[]{Vmhost.class}, vmhost));

        assertEquals("删除虚拟机 VPC 转发失败，停止删除以保留 IP 分配: 198.51.100.10；"
                + "QAgent 删除IP转发失败（code=500）：iptables 链不存在", thrown.getCause().getMessage());
    }

    @Test
    void clearsVpcAllocationsBeforeReleasingPublicIps() throws Exception {
        Task task = new Task();
        Vmhost vmhost = vpcVmhost();
        when(subnetpoolService.count(any())).thenReturn(1L, 0L);
        when(subnetpoolService.update(any())).thenAnswer(invocation -> {
            operations.add("subnet-release");
            return true;
        });
        when(vmhostService.clearVmhostVpcIpBinding(17)).thenAnswer(invocation -> {
            operations.add("vpc-binding-clear");
            return true;
        });
        when(ippoolService.releaseIppoolByNodeIdAndVmId(3, 101, null)).thenAnswer(invocation -> {
            operations.add("public-ip-release");
            return 1;
        });
        when(securityGroupBusinessService.deleteVmSecurityGroups(17)).thenReturn(true);

        invokePrivate("finishDeletedVm", new Class<?>[]{Task.class, Vmhost.class}, task, vmhost);

        assertEquals(List.of("subnet-release", "vpc-binding-clear", "public-ip-release"), operations);
    }

    @Test
    void doesNotReleasePublicIpsWhenSubnetCleanupFails() throws Exception {
        Task task = new Task();
        Vmhost vmhost = vpcVmhost();
        when(subnetpoolService.count(any())).thenReturn(1L, 1L);
        when(subnetpoolService.update(any())).thenReturn(false);

        InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                () -> invokePrivate("finishDeletedVm", new Class<?>[]{Task.class, Vmhost.class}, task, vmhost));

        assertEquals(IllegalStateException.class, thrown.getCause().getClass());
        verify(ippoolService, never()).releaseIppoolByNodeIdAndVmId(3, 101, null);
    }

    @Test
    void doesNotReleasePublicIpsWhenVpcBindingCleanupFails() throws Exception {
        Task task = new Task();
        Vmhost vmhost = vpcVmhost();
        when(subnetpoolService.count(any())).thenReturn(0L);
        when(vmhostService.clearVmhostVpcIpBinding(17)).thenReturn(false);

        InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                () -> invokePrivate("finishDeletedVm", new Class<?>[]{Task.class, Vmhost.class}, task, vmhost));

        assertEquals(IllegalStateException.class, thrown.getCause().getClass());
        verify(ippoolService, never()).releaseIppoolByNodeIdAndVmId(3, 101, null);
    }

    private Vmhost vpcVmhost() {
        Vmhost vmhost = new Vmhost();
        vmhost.setId(17);
        vmhost.setNodeid(3);
        vmhost.setVmid(101);
        vmhost.setNetworkType("vpc");
        // VPC 的 ipList 保存私网地址；公网地址来自节点 ippool，不能拿这个值过滤公网池。
        vmhost.setIpList(List.of("172.16.0.5"));
        return vmhost;
    }

    private JSONArray portRules(Integer hostId) {
        JSONObject rule = new JSONObject();
        rule.put("vm", hostId);
        rule.put("source_ip", "203.0.113.8");
        rule.put("source_port", 8443);
        rule.put("destination_ip", "10.0.0.17");
        rule.put("destination_port", 443);
        rule.put("protocol", "tcp");
        JSONArray rules = new JSONArray();
        rules.add(rule);
        return rules;
    }

    private Object invokePrivate(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = DeleteVmCron.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(cron, args);
    }

    private static class TestDeleteVmCron extends DeleteVmCron {
        private final List<String> operations;
        private boolean remoteDeleteSucceeds = true;

        private TestDeleteVmCron(List<String> operations) {
            this.operations = operations;
        }

        @Override
        protected boolean deletePortForwardOnNode(Master node, String token, Integer vm, String sourceIp,
                                                  Integer sourcePort, String destinationIp, Integer destinationPort,
                                                  String protocol) {
            operations.add("remote");
            return remoteDeleteSucceeds;
        }
    }
}
