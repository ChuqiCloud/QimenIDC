package com.chuqiyun.proxmoxveams.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.dto.pvesdn.ZonesParams;
import com.chuqiyun.proxmoxveams.entity.*;
import com.chuqiyun.proxmoxveams.service.*;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import com.chuqiyun.proxmoxveams.utils.CertificateUtil;
import com.chuqiyun.proxmoxveams.utils.ClientApiUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author mryunqi
 * @date 2023/6/18
 */
@RestController
@RequestMapping("/{adminPath}")
public class SysNodeMasterController {
    @Resource
    private MasterService masterService;
    @Resource
    private VmhostService vmhostService;
    @Resource
    private VncnodeService vncnodeService;
    @Resource
    private ConfigService configService;
    @Resource
    private PveSdnService pveSdnService;
    @AdminApiCheck
    @PostMapping("/insertNodeMaster")
    public ResponseResult<String> insertNodeMaster(@RequestBody Master master) throws UnauthorizedException {
        // 将master信息存入数据库
        if (masterService.save(master)) {
            // 更新该master的csrfToken与ticket
            masterService.updateNodeCookie(master.getId());
            // 添加VNC控制器
            vncnodeService.addHostVncnode(master);
            return ResponseResult.ok("添加成功！");
        } else {
            return ResponseResult.fail("添加失败！");
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 分页获取集群节点信息
    * @DateTime: 2023/7/22 22:10
    */
    @AdminApiCheck
    @GetMapping("/selectNodeByPage")
    public ResponseResult<Object> selectNodeByPage(@RequestParam(name = "page",defaultValue = "1") Integer page,
                                                   @RequestParam(name = "size", defaultValue = "20") Integer size) throws UnauthorizedException {
        Page<Master> masterPage = masterService.getMasterList(page,size);
        return ResponseResult.ok(masterPage);
    }

    /**
    * @Author: mryunqi
    * @Description: 修改节点信息
    * @DateTime: 2023/7/24 22:45
    */
    @AdminApiCheck
    @RequestMapping(value = "/updateNodeInfo",method = {RequestMethod.POST,RequestMethod.PUT})
    public ResponseResult<String> updateNodeInfo(@RequestBody Master master) throws UnauthorizedException {
        // 节点公钥要能清空：传空串就是解绑，传 null 才表示不改
        if (master.getNodePublicKey() != null) {
            try {
                master.setNodePublicKey(CertificateUtil.normalize(master.getNodePublicKey()));
            } catch (IllegalArgumentException e) {
                return ResponseResult.fail(e.getMessage());
            }
        }
        // 将master信息存入数据库
        if (masterService.updateById(master)) {
            return ResponseResult.ok("修改成功！");
        } else {
            return ResponseResult.fail("修改失败！");
        }
    }

    /**
     * @Description: 预览节点公钥证书的有效期，不落库
     * 节点是自签证书，没有 CA 可查，只能拿公钥自己算过期时间，
     * 保存前先预览一次，避免把过期证书或粘错的串写进库。
     * 未保存的 id 一并透传，前端可以顺手对比「库里存的」和「刚粘的」是否同一个证书。
     */
    @AdminApiCheck
    @PostMapping("/previewNodeCertificate")
    public ResponseResult<Object> previewNodeCertificate(@RequestBody Map<String, Object> body) throws UnauthorizedException {
        Object rawKey = body == null ? null : body.get("nodePublicKey");
        String publicKey = rawKey == null ? null : String.valueOf(rawKey);
        if (publicKey == null || publicKey.trim().isEmpty()) {
            return ResponseResult.fail("请提供节点公钥");
        }
        if (!CertificateUtil.isPem(publicKey)) {
            return ResponseResult.fail("节点公钥必须是 PEM 格式的证书（BEGIN CERTIFICATE）");
        }
        Map<String, Object> data = new LinkedHashMap<>(CertificateUtil.describe(publicKey));
        Object savedId = body.get("id");
        if (savedId != null) {
            Master saved;
            try {
                saved = masterService.getById(Integer.valueOf(String.valueOf(savedId)));
            } catch (NumberFormatException e) {
                return ResponseResult.fail("节点ID格式不正确");
            }
            if (saved == null) {
                return ResponseResult.fail("节点不存在");
            }
            Map<String, Object> stored = saved.getNodePublicKey() == null
                    ? null : CertificateUtil.describe(saved.getNodePublicKey());
            data.put("nodeId", saved.getId());
            data.put("stored", stored);
            data.put("changed", stored == null
                    || !String.valueOf(stored.get("fingerprint")).equals(String.valueOf(data.get("fingerprint"))));
        }
        return ResponseResult.ok(data);
    }

    /**
    * @Author: mryunqi
    * @Description: 删除指定id节点
    * @DateTime: 2023/10/22 22:54
    */
    @AdminApiCheck
    @DeleteMapping("/deleteNodeById")
    public ResponseResult<Object> deleteNodeById(@RequestParam("nodeId") Integer nodeId) throws UnauthorizedException {
        UnifiedResultDto<Object> resultDto = masterService.deleteNode(vmhostService,nodeId);
        if (resultDto.getResultCode().getCode() != UnifiedResultCode.SUCCESS.getCode()) {
            return ResponseResult.fail(resultDto.getResultCode().getCode(),resultDto.getResultCode().getMessage());
        }
        return ResponseResult.ok(resultDto.getData());
    }
    @AdminApiCheck
    @PostMapping("/addNodeMasterNat")
    public ResponseResult<String> addNodeMasterNat(@RequestBody CNat nat) throws UnauthorizedException {
        Master master = masterService.getById(nat.getId());
        //查看是否已经启用NAT
        if (master.getNaton() == 1)
        {
            return ResponseResult.fail("该节点已经启用了NAT！");
        }
        //创建IP池并获取IP池ID
        Integer poolid = masterService.addNodeNatIpPool(nat.getId(),nat.getNataddr(),nat.getDns1(),nat.getDns2());
        if (poolid != 0)
        {
            //创建SDN区域
            ZonesParams zonesParams = new ZonesParams();
            zonesParams.setNodeId(nat.getId());
            zonesParams.setType("simple");
            zonesParams.setZone(nat.getNatbridge());
            UnifiedResultDto<Object> resultDto = pveSdnService.addZone(zonesParams);
            if (resultDto.getResultCode().getCode() != UnifiedResultCode.SUCCESS.getCode()) {
                return ResponseResult.fail(resultDto.getResultCode().getCode(),resultDto.getResultCode().getMessage());
            } else {
                //创建Vnets
                Vnets vnets = new Vnets();
                vnets.setVnet(nat.getNatbridge());
                vnets.setZone(nat.getNatbridge());
                resultDto = pveSdnService.addVnet(vnets);
                if (resultDto.getResultCode().getCode() != UnifiedResultCode.SUCCESS.getCode()) {
                    return ResponseResult.fail(resultDto.getResultCode().getCode(),resultDto.getResultCode().getMessage());
                } else {
                    //创建子网
                    Subnet subnet = new Subnet();
                    subnet.setNodeid(nat.getId());
                    subnet.setVnet(nat.getNatbridge());
                    String[] parts = nat.getNataddr().split("/");
                    subnet.setSubnet(parts[0]);
                    subnet.setGateway(parts[0]);
                    subnet.setMask(Integer.valueOf(parts[1]));
                    subnet.setSnat(1);
                    subnet.setDns(nat.getDns1());
                    resultDto = pveSdnService.addSubnet(subnet);
                    if (resultDto.getResultCode().getCode() != UnifiedResultCode.SUCCESS.getCode()) {
                        return ResponseResult.fail(resultDto.getResultCode().getCode(),resultDto.getResultCode().getMessage());
                    }   else {
                        pveSdnService.applySdn(nat.getId());
                        master.setNataddr(nat.getAddrdomain());
                        master.setNatippool(poolid);
                        master.setNaton(1);
                        master.setNatbridge(nat.getNatbridge());
                        //更新被控IP池ID、naton、网口等相关信息
                        masterService.updateById(master);
                        return ResponseResult.ok("添加成功！");
                    }
                }
            }
        } else {
            return ResponseResult.fail("添加失败！");
        }
    }
}
