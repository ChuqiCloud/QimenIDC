package com.chuqiyun.proxmoxveams.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.dto.IpParams;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.entity.Ippool;
import com.chuqiyun.proxmoxveams.entity.Ipstatus;
import com.chuqiyun.proxmoxveams.service.IppoolService;
import com.chuqiyun.proxmoxveams.service.IpstatusService;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.utils.IpUtil;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * @author mryunqi
 * @date 2023/7/2
 */
@RestController
@RequestMapping("/{adminPath}")
public class SysIpPoolController {
    @Resource
    private MasterService masterService;
    @Resource
    private IppoolService ippoolService;
    @Resource
    private IpstatusService ipstatusService;

    /**
     * 根据掩码位批量添加IP池
     * @param ipParams IP池参数
     * @return ResponseResult<String>
     * @throws UnauthorizedException
     */
    @AdminApiCheck
    @PostMapping("/insertIpPoolByMask")
    public ResponseResult<String> insertIpPool(@RequestBody IpParams ipParams) throws UnauthorizedException {
        // 创建所有掩码位列表
        List<Integer> maskList = List.of(8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32);
        // 判断掩码位是否合法
        if (!maskList.contains(ipParams.getMask())){
            return ResponseResult.fail("掩码位不合法！");
        }
        // 判断是否有空参数
        if(ipParams.getPoolName() == null || ipParams.getNodeId() == null || ipParams.getGateway() == null || ipParams.getMask() == null
        || ipParams.getDns1() == null || ipParams.getDns2() == null){
            return ResponseResult.fail("参数不能为空！");
        }
        // 判断节点是否存在
        if (masterService.getById(ipParams.getNodeId()) == null){
            return ResponseResult.fail("节点不存在！");
        }
        // 判断网关是否合法
        if (!ipParams.getGateway().matches("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$")){
            return ResponseResult.fail("网关不合法！");
        }
        // 判断dns是否合法
        if (!ipParams.getDns1().matches("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$") ||
                !ipParams.getDns2().matches("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$")){
            return ResponseResult.fail("dns不合法！");
        }
        // 判断网关是否在ip池中
        /*if (ippoolService.isGatewayInIppool(ipParams.getGateway())){
            return ResponseResult.fail("网关已存在！");
        }*/
        int statusId = ipstatusService.insertIpstatus(ipParams);
        if (statusId <=0){
            return ResponseResult.fail("IP池信息添加失败！");
        }
        ipParams.setPoolId(statusId);
        List<Ippool> ippoolList = IpUtil.getIpList(ipParams);
        // 批量插入ip池
        if (ippoolService.insertIppoolList(ippoolList)){
            return ResponseResult.ok("插入成功！");
        }else {
            return ResponseResult.fail("插入失败！");
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 根据起始IP与结束IP批量添加IP池
    * @DateTime: 2023/7/3 23:25
    */
    @AdminApiCheck
    @PostMapping("/insertIpPoolByRange")
    public ResponseResult<String> insertIpPoolByRange(@RequestBody IpParams ipParams) throws UnauthorizedException{
        // 判断是否有空参数
        if(ipParams.getPoolId() == null || ipParams.getDns1() == null
                || ipParams.getDns2() == null || ipParams.getStartIp() == null || ipParams.getEndIp() == null){
            return ResponseResult.fail("参数不能为空！");
        }
        // 判断起始IP与结束IP是否合法
        if (!ipParams.getStartIp().matches("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$") ||
                !ipParams.getEndIp().matches("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$")){
            return ResponseResult.fail("IP不合法！");
        }
        // 判断DNS是否合法
        if (!ipParams.getDns1().matches("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$") ||
                !ipParams.getDns2().matches("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$")){
            return ResponseResult.fail("dns不合法！");
        }
        // 判断poolId是否存在
        if (ipstatusService.getById(ipParams.getPoolId()) == null){
            return ResponseResult.fail("IP池不存在！");
        }
        // 获取该网关下IP池中的所有IP
        List<String> ipList = ippoolService.getIpListByPoolId(ipParams.getPoolId());
        // 获取该范围内的所有IP
        List<String> allIpList = IpUtil.getAllIPsInRange(ipParams.getStartIp(),ipParams.getEndIp());
        // 删除allIpList中存在于ipList中的IP地址
        allIpList.removeAll(ipList);
        // 获取IpStatus对象
        Ipstatus ipstatus = ipstatusService.getById(ipParams.getPoolId());
        // 循环创建Ippool对象
        List<Ippool> ippoolList = new ArrayList<>();
        for (String ip : allIpList){
            Ippool ippool = new Ippool();
            ippool.setPoolId(ipParams.getPoolId());
            ippool.setNodeId(ipstatus.getNodeid());
            ippool.setGateway(ipstatus.getGateway());
            ippool.setSubnetMask(IpUtil.getMaskToString(ipstatus.getMask()));
            ippool.setIp(ip);
            ippool.setDns1(ipParams.getDns1());
            ippool.setDns2(ipParams.getDns2());
            ippoolList.add(ippool);
        }
        // 批量插入ip池
        if (ippoolService.insertIppoolList(ippoolList)){
            return ResponseResult.ok("插入成功！");
        }else {
            return ResponseResult.fail("插入失败！");
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 查询IP池列表
    * @DateTime: 2023/7/4 16:20
    */
    @AdminApiCheck
    @GetMapping("/selectIpPoolList")
    public ResponseResult<Page<Ipstatus>> selectIpPoolList(@RequestParam(name = "page",defaultValue = "1") Integer page,
                                                           @RequestParam(name = "size",defaultValue = "20") Integer size) throws UnauthorizedException{
        Page<Ipstatus> ipstatusPage = ipstatusService.getIpstatusPage(page,size);
        return ResponseResult.ok(ipstatusPage);
    }
    /**
    * @Author: mryunqi
    * @Description: 查询指定IP池下的IP列表
    * @DateTime: 2023/7/4 16:26
    */
    @AdminApiCheck
    @GetMapping("/selectIpListByPoolId")
    public ResponseResult<Page<Ippool>> selectIpListByPoolId(@RequestParam(name = "poolid") Integer poolid,
                                               @RequestParam(name = "page",defaultValue = "1") Integer page,
                                               @RequestParam(name = "size",defaultValue = "20") Integer size) throws UnauthorizedException{
        // 判断poolId是否存在
        if (ipstatusService.getById(poolid) == null){
            return ResponseResult.fail("IP池不存在！");
        }
        Page<Ippool> ipListPage = ippoolService.getIppoolListByPoolId(poolid,page,size);
        return ResponseResult.ok(ipListPage);
    }

    /**
    * @Author: mryunqi
    * @Description: 修改IP池
    * @DateTime: 2023/7/4 16:39
    */
    @AdminApiCheck
    @PutMapping("/updateIpPool")
    @PostMapping("/updateIpPool")
    public ResponseResult<String> updateIpPool(@RequestBody Ipstatus ipstatus) throws UnauthorizedException{
        // 判断pool是否存在
        if (ipstatusService.getById(ipstatus.getId()) == null){
            return ResponseResult.fail("IP池不存在！");
        }
        if (!ipstatusService.updateIpStatus(ipstatus)){
            return ResponseResult.fail("修改失败！");
        }
        // 获取该IP池下的所有IP
        List<Ippool> ipList = ippoolService.getIppoolListByPoolId(ipstatus.getId());
        // 修改IP池下的所有IP
        for (Ippool ippool : ipList){
            // 参数为null时，不修改
            if (ipstatus.getGateway() != null){
                ippool.setGateway(ipstatus.getGateway());
            }
            if (ipstatus.getMask() != null){
                ippool.setSubnetMask(IpUtil.getMaskToString(ipstatus.getMask()));
            }
            if (ipstatus.getDns1() != null){
                ippool.setDns1(ipstatus.getDns1());
            }
            if (ipstatus.getDns2() != null){
                ippool.setDns2(ipstatus.getDns2());
            }
            if (ipstatus.getNodeid() != null){
                ippool.setNodeId(ipstatus.getNodeid());
            }
        }
        if (ippoolService.updateBatchById(ipList)){
            return ResponseResult.ok("修改成功！");
        }else {
            return ResponseResult.fail("修改失败！");
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 修改单个IP信息
    * @DateTime: 2023/7/4 17:04
    */
    @AdminApiCheck
    @PutMapping("/updateIp")
    @PostMapping("/updateIp")
    public ResponseResult<String> updateIp(@RequestBody List<Ippool> ipList) throws UnauthorizedException {
        if (ippoolService.updateBatchById(ipList)) {
            return ResponseResult.ok("修改成功！");
        } else {
            return ResponseResult.fail("修改失败！");
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 删除指定IP池
    * @DateTime: 2023/10/31 22:43
    */
    @AdminApiCheck
    @DeleteMapping("/deleteIpPool/{poolId}")
    public ResponseResult<Object> deleteIpPool(@PathVariable("poolId") Long poolId) throws UnauthorizedException {
        UnifiedResultDto<Object> resultDto = ipstatusService.deleteIppoolById(poolId);
        if (resultDto.getResultCode().getCode() != UnifiedResultCode.SUCCESS.getCode()) {
            return ResponseResult.fail(resultDto.getResultCode().getCode(),resultDto.getResultCode().getMessage());
        }
        return ResponseResult.ok(resultDto.getData());
    }

}
