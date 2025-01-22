package com.chuqiyun.proxmoxveams.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.service.ConfigService;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import com.chuqiyun.proxmoxveams.service.VncnodeService;
import com.chuqiyun.proxmoxveams.utils.ClientApiUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

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
        // 将每个master的csrfToken与ticket加***处理
        /*for (Master master : masterPage.getRecords()) {
            master.setPassword("**********");
            master.setCsrfToken("**********");
            master.setTicket("**********");
            master.setSshPassword("**********");
        }*/
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
        // 将master信息存入数据库
        if (masterService.updateById(master)) {
            return ResponseResult.ok("修改成功！");
        } else {
            return ResponseResult.fail("修改失败！");
        }
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
    public ResponseResult<String> addNodeMasterNat(@RequestBody Integer id,String nataddr,String natbridge) throws UnauthorizedException {
        //创建IP池并获取IP池ID
        Integer poolid = masterService.addNodeNatIpPool(id,nataddr);
        if (poolid != 0)
        {
            //提交添加请求
            String token = configService.getToken();
            Master node = masterService.getById(id);
            if (ClientApiUtil.addNatBridge(node.getHost(), token, node.getControllerPort(), nataddr, natbridge)) {
                // 将master信息存入数据库
                Master master = masterService.getById(id);
                master.setNataddr(nataddr);
                master.setNatippool(poolid);
                master.setNaton(1);
                master.setNatbridge(natbridge);
                //更新被控IP池ID、naton、网口等相关信息
                masterService.updateById(master);
                return ResponseResult.ok("添加成功！");
            } else {
                return ResponseResult.fail("添加失败！");
            }
        } else {
            return ResponseResult.fail("添加失败！");
        }
    }
}
