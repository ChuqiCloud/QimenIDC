package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.chuqiyun.proxmoxveams.dto.VmParams;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Subnet;
import com.chuqiyun.proxmoxveams.service.IpstatusService;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.SubnetService;
import com.chuqiyun.proxmoxveams.service.SubnetpoolService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CreateVmVpcSubnetResolutionTest {
    private static final int NODE_ID = 13;

    private final SubnetService subnetService = mock(SubnetService.class);
    private final MasterService masterService = mock(MasterService.class);
    private final IpstatusService ipstatusService = mock(IpstatusService.class);
    private final SubnetpoolService subnetpoolService = mock(SubnetpoolService.class);
    private CreateVmServiceImpl createVmService;

    @BeforeEach
    void setUp() {
        createVmService = new CreateVmServiceImpl();
        ReflectionTestUtils.setField(createVmService, "subnetService", subnetService);
        ReflectionTestUtils.setField(createVmService, "masterService", masterService);
        ReflectionTestUtils.setField(createVmService, "ipstatusService", ipstatusService);
        ReflectionTestUtils.setField(createVmService, "subnetpoolService", subnetpoolService);

        Master node = new Master();
        node.setId(NODE_ID);
        node.setNatbridge("nat13");
        when(masterService.getById(NODE_ID)).thenReturn(node);
    }

    @Test
    void automaticallyPrefersCurrentNodesVpcNamedSdnSubnet() {
        Subnet ordinary = subnet(3, NODE_ID, "sdn-edge");
        Subnet vpc = subnet(8, NODE_ID, "SDN-VPC-PROD");
        Subnet nat = subnet(1, NODE_ID, "nat13");
        stubSubnets(nat, ordinary, vpc);

        Subnet selected = resolve(new VmParams());

        assertEquals(vpc.getId(), selected.getId());
    }

    @Test
    void fallsBackToFirstNonNatSdnSubnetWhenNoVpcNameExists() {
        Subnet later = subnet(9, NODE_ID, "sdn-later");
        Subnet nat = subnet(2, NODE_ID, "nat13");
        Subnet first = subnet(4, NODE_ID, "sdn-first");
        stubSubnets(later, nat, first);

        Subnet selected = resolve(new VmParams());

        assertEquals(first.getId(), selected.getId());
    }

    @Test
    void doesNotSelectAnotherNodesVpcSubnetAndReportsMissingLocalSdn() {
        Subnet otherNodeVpc = subnet(1, 99, "vpc-other-node");
        Subnet currentNodeNat = subnet(2, NODE_ID, "nat13");
        stubSubnets(otherNodeVpc, currentNodeNat);
        VmParams params = new VmParams();
        params.setNodeid(NODE_ID);

        assertNull(resolve(params));
        String message = ReflectionTestUtils.invokeMethod(createVmService,
                "buildVpcSubnetInvalidMessage", params);
        assertTrue(message.contains("当前节点未找到可用于VPC的SDN网络"));
        assertTrue(message.contains("nodeid=" + NODE_ID));
    }

    @Test
    void explicitSubnetIdRemainsPreferredOverAutomaticSelection() {
        Subnet explicit = subnet(7, NODE_ID, "sdn-explicit");
        when(subnetService.getById(explicit.getId())).thenReturn(explicit);
        stubSubnets(subnet(8, NODE_ID, "vpc-auto"));
        VmParams params = new VmParams();
        params.setNodeid(NODE_ID);
        params.setVpcSubnetId(explicit.getId());

        Subnet selected = resolve(params);

        assertEquals(explicit.getId(), selected.getId());
    }

    @Test
    void explicitBridgeRemainsSupported() {
        Subnet explicit = subnet(5, NODE_ID, "sdn-requested");
        stubSubnets(explicit);
        VmParams params = new VmParams();
        params.setNodeid(NODE_ID);
        params.setBridge("sdn-requested");

        Subnet selected = resolve(params);

        assertEquals(explicit.getId(), selected.getId());
    }

    @Test
    void acceptsVnetAsAnAliasForBridgeInTheApiPayload() throws Exception {
        VmParams params = new ObjectMapper().readValue("{\"vnet\":\"sdn-requested\"}", VmParams.class);

        assertEquals("sdn-requested", params.getBridge());
    }

    private Subnet resolve(VmParams params) {
        if (params.getNodeid() == null) {
            params.setNodeid(NODE_ID);
        }
        return ReflectionTestUtils.invokeMethod(createVmService, "resolveVpcSubnet", params);
    }

    @SafeVarargs
    private final void stubSubnets(Subnet... subnets) {
        List<Subnet> result = subnets.length == 0
                ? Collections.emptyList()
                : Arrays.asList(subnets);
        when(subnetService.list(any(Wrapper.class))).thenReturn(result);
    }

    private Subnet subnet(int id, int nodeId, String vnet) {
        Subnet subnet = new Subnet();
        subnet.setId(id);
        subnet.setNodeid(nodeId);
        subnet.setVnet(vnet);
        return subnet;
    }
}
