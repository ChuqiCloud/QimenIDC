package com.chuqiyun.proxmoxveams.controller.admin;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.annotation.PublicSysApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.dto.VmIpParams;
import com.chuqiyun.proxmoxveams.dto.VmParams;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;

/**
 * @author mryunqi
 * @date 2023/8/31
 */
@RestController
@RequestMapping("/{adminPath}")
public class SysVmHostController {
    @Resource
    private VmhostService vmhostService;

    /**
    * @Author: mryunqi
    * @Description: pve虚拟机开关机等操作
    * @DateTime: 2023/8/31 20:15
    */
    @AdminApiCheck
    @RequestMapping(value = "/power/{hostId}/{action}",method = {RequestMethod.POST,RequestMethod.PUT})
    public Object power(@PathVariable("hostId") Integer hostId,
                        @PathVariable("action") String action,
                        @RequestBody(required = false) JSONObject data) throws UnauthorizedException {
        // 判断虚拟机是否存在
        if (vmhostService.getById(hostId) == null) {
            return ResponseResult.fail("虚拟机不存在");
        }
        // 判断action是否合法
        if (!"start".equals(action) && !"stop".equals(action) && !"shutdown".equals(action) && !"reboot".equals(action)
                && !"pause".equals(action) && !"unpause".equals(action) && !"suspend".equals(action) && !"resume".equals(action)) {
            return ResponseResult.fail("action不合法");
        }
        HashMap<String, Object> result = vmhostService.power(hostId, action, data);
        if (result == null) {
            return ResponseResult.fail("操作失败");
        }
        Boolean status = (Boolean) result.get("status");
        if (!status) {
            return ResponseResult.fail(result.get("msg").toString());
        }
        return ResponseResult.ok("操作成功");
    }

    /**
    * @Author: mryunqi
    * @Description: 重装系统
    * @DateTime: 2023/9/2 0:10
    */
    @AdminApiCheck
    @RequestMapping(value = "/reinstall",method = {RequestMethod.POST,RequestMethod.PUT})
    public Object reinstall(@RequestBody JSONObject params) throws UnauthorizedException {
        UnifiedResultDto<Object> resultDto = vmhostService.resetVmOs(params.getLong("hostId"), params.getString("os"), params.getString("newPassword") , params.getBoolean("resetDataDisk"), getInitScriptIds(params));
        if (resultDto.getResultCode().getCode() != UnifiedResultCode.SUCCESS.getCode()) {
            return ResponseResult.fail(resultDto.getResultCode().getCode(),resultDto.getMessage());
        }
        return ResponseResult.ok(resultDto.getResultCode().getMessage());
    }

    private List<Integer> getInitScriptIds(JSONObject params) {
        List<Integer> initScriptIds = new java.util.ArrayList<>();
        if (params == null) {
            return initScriptIds;
        }
        if (params.getJSONArray("initScriptIds") != null) {
            initScriptIds.addAll(params.getJSONArray("initScriptIds").toJavaList(Integer.class));
        }
        Integer initScriptId = params.getInteger("initScriptId");
        if (initScriptId != null && !initScriptIds.contains(initScriptId)) {
            initScriptIds.add(initScriptId);
        }
        return initScriptIds;
    }

    /**
    * @Author: mryunqi
    * @Description: 删除虚拟机
    * @DateTime: 2023/9/2 16:15
    */
    @AdminApiCheck
    @RequestMapping(value = "/delete/{hostId}",method = {RequestMethod.POST,RequestMethod.PUT,RequestMethod.DELETE})
    public Object delete(@PathVariable("hostId") Long hostId) throws UnauthorizedException {
        UnifiedResultDto<Object> resultDto = vmhostService.deleteVmToRecycle(hostId);
        if (resultDto.getResultCode().getCode() != UnifiedResultCode.SUCCESS.getCode()) {
            return ResponseResult.fail(resultDto.getResultCode().getCode(),resultDto.getResultCode().getMessage());
        }
        return ResponseResult.ok(resultDto.getResultCode().getMessage());
    }
    /**
     * @Author: 星禾
     * @Description: 恢复虚拟机
     * @DateTime: 2026/5/23 23:55
     */
    @AdminApiCheck
    @RequestMapping(value = "/unDelete/{hostId}",method = {RequestMethod.POST,RequestMethod.PUT,RequestMethod.DELETE})
    public Object unDelete(@PathVariable("hostId") Long hostId) throws UnauthorizedException {
        UnifiedResultDto<Object> resultDto = vmhostService.unDeleteVm(hostId);
        if (resultDto.getResultCode().getCode() != UnifiedResultCode.SUCCESS.getCode()) {
            return ResponseResult.fail(resultDto.getResultCode().getCode(),resultDto.getResultCode().getMessage());
        }
        return ResponseResult.ok(resultDto.getResultCode().getMessage());
    }
    /**
     * @Author: 星禾
     * @Description: 强制删除虚拟机接口 不进回收站
     * @DateTime: 2026/5/26 9:55
     */
    @AdminApiCheck
    @RequestMapping(value = "/forceDelete/{hostId}",method = {RequestMethod.POST,RequestMethod.PUT,RequestMethod.DELETE})
    public Object forceDelete(@PathVariable("hostId") Long hostId) throws UnauthorizedException {
        UnifiedResultDto<Object> resultDto = vmhostService.deleteVm(hostId);
        if (resultDto.getResultCode().getCode() != UnifiedResultCode.SUCCESS.getCode()) {
            return ResponseResult.fail(resultDto.getResultCode().getCode(),resultDto.getResultCode().getMessage());
        }
        return ResponseResult.ok(resultDto.getResultCode().getMessage());
    }
    /**
     * @Author: 星禾
     * @Description: pve虚拟机重置流量操作
     * @DateTime: 2025/11/21 17:15
     */
    @AdminApiCheck
    @RequestMapping(value = "/resetVmHostFlow/{hostId}",method = {RequestMethod.POST,RequestMethod.PUT})
    public Object resetVmHostFlow(@PathVariable("hostId") Integer hostId) throws UnauthorizedException {
        // 判断虚拟机是否存在
        if (vmhostService.getById(hostId) == null) {
            return ResponseResult.fail("虚拟机不存在");
        }

        Boolean result = vmhostService.resetVmHostFlow(hostId);
        if (result == null) {
            return ResponseResult.fail("操作失败");
        }
        return ResponseResult.ok("操作成功");
    }

    /**
     * @Author: 星禾
     * @Description: pve虚拟机重置状态
     * @DateTime: 2025/12/05 16:15
     */
    @AdminApiCheck
    @RequestMapping(value = "/resetVmHostStatus/{hostId}",method = {RequestMethod.POST,RequestMethod.PUT})
    public Object resetVmHostStatus(@PathVariable("hostId") Integer hostId) throws UnauthorizedException {
        // 判断虚拟机是否存在
        if (vmhostService.getById(hostId) == null) {
            return ResponseResult.fail("虚拟机不存在");
        }

        Boolean result = vmhostService.resetVmHostStatus(hostId);
        if (result == null) {
            return ResponseResult.fail("操作失败");
        }
        return ResponseResult.ok("操作成功");
    }

    /**
     * @Author: 星禾
     * @Description: pve虚拟机添加流量包操作
     * @DateTime: 2025/11/22 20:11
     */
    @AdminApiCheck
    @RequestMapping(value = "/addVmHostFlow/{hostId}",method = {RequestMethod.POST,RequestMethod.PUT})
    public Object addVmHostFlow(@PathVariable("hostId") Integer hostId,
                                  @RequestParam("flow") Long flow) throws UnauthorizedException {
        // 判断虚拟机是否存在
        if (vmhostService.getById(hostId) == null) {
            return ResponseResult.fail("虚拟机不存在");
        }

        if (flow < 0 || flow > 1000000){
            return ResponseResult.fail("流量包不能过大（1000000G）或小于0");
        }

        Boolean result = vmhostService.addVmHostFlow(hostId,flow);
        if (result == null) {
            return ResponseResult.fail("操作失败");
        }
        return ResponseResult.ok("操作成功");
    }
    /**
     * @Author: 星禾
     * @Description: pve虚拟机修改配置
     * @DateTime: 2025/11/22 20:11
     */
    @AdminApiCheck
    @RequestMapping(value = "/updateVm",method = {RequestMethod.POST,RequestMethod.PUT})
    public Object updateVm(@RequestBody VmParams vmParams) throws UnauthorizedException {
        UnifiedResultDto<Object> resultDto = vmhostService.updateVm(vmParams);
        if (resultDto.getResultCode().getCode() != UnifiedResultCode.SUCCESS.getCode()) {
            return ResponseResult.fail(resultDto.getResultCode().getCode(),resultDto.getResultCode().getMessage());
        }
        return ResponseResult.ok(resultDto.getResultCode().getMessage());
    }

    /**
     * @Author: 星禾
     * @Description: 修改虚拟机IP
     * @DateTime: 2026/6/4 20:14
    */
    @AdminApiCheck
    @RequestMapping(value = "/updateVmIp",method = {RequestMethod.POST,RequestMethod.PUT})
    public Object updateVmIp(@RequestBody(required = false) VmIpParams vmIpParams,
                             @RequestParam(name = "hostId", required = false) Integer hostId,
                             @RequestParam(name = "hostid", required = false) Integer hostid,
                             @RequestParam(name = "ip", required = false) String ip,
                             @RequestParam(name = "newIp", required = false) String newIp,
                             @RequestParam(name = "poolId", required = false) Integer poolId,
                             @RequestParam(name = "networkIndex", required = false) Integer networkIndex) throws UnauthorizedException {
        VmIpParams params = buildVmIpParams(vmIpParams, hostId, hostid, ip, newIp, poolId, networkIndex);
        UnifiedResultDto<Object> resultDto = vmhostService.updateVmIp(params);
        if (resultDto.getResultCode().getCode() != UnifiedResultCode.SUCCESS.getCode()) {
            return ResponseResult.fail(resultDto.getResultCode().getCode(),resultDto.getResultCode().getMessage());
        }
        return ResponseResult.ok(resultDto.getData());
    }

    /**
     * @Author: 星禾
     * @Description: 给虚拟机新增一个或多个IP
     * @DateTime: 2026/6/6 12:40
    */
    @AdminApiCheck
    @RequestMapping(value = "/addVmIp",method = {RequestMethod.POST,RequestMethod.PUT})
    public Object addVmIp(@RequestBody(required = false) VmIpParams vmIpParams,
                          @RequestParam(name = "hostId", required = false) Integer hostId,
                          @RequestParam(name = "hostid", required = false) Integer hostid,
                          @RequestParam(name = "ip", required = false) String ip,
                          @RequestParam(name = "newIp", required = false) String newIp,
                          @RequestParam(name = "ips", required = false) List<String> ips,
                          @RequestParam(name = "count", required = false) Integer count,
                          @RequestParam(name = "poolId", required = false) Integer poolId,
                          @RequestParam(name = "networkIndex", required = false) Integer networkIndex) throws UnauthorizedException {
        VmIpParams params = buildVmIpParams(vmIpParams, hostId, hostid, ip, newIp, ips, count, poolId, networkIndex);
        UnifiedResultDto<Object> resultDto = vmhostService.addVmIp(params);
        if (resultDto.getResultCode().getCode() != UnifiedResultCode.SUCCESS.getCode()) {
            return ResponseResult.fail(resultDto.getResultCode().getCode(),resultDto.getResultCode().getMessage());
        }
        return ResponseResult.ok(resultDto.getData());
    }

    /**
     * @Author: 星禾
     * @Description: 删除虚拟机单网卡中的一个或多个IP
     * @DateTime: 2026/6/6 12:40
    */
    @AdminApiCheck
    @RequestMapping(value = "/deleteVmIp",method = {RequestMethod.POST,RequestMethod.PUT,RequestMethod.DELETE})
    public Object deleteVmIp(@RequestBody(required = false) VmIpParams vmIpParams,
                             @RequestParam(name = "hostId", required = false) Integer hostId,
                             @RequestParam(name = "hostid", required = false) Integer hostid,
                             @RequestParam(name = "ip", required = false) String ip,
                             @RequestParam(name = "newIp", required = false) String newIp,
                             @RequestParam(name = "ips", required = false) List<String> ips,
                             @RequestParam(name = "poolId", required = false) Integer poolId,
                             @RequestParam(name = "networkIndex", required = false) Integer networkIndex) throws UnauthorizedException {
        VmIpParams params = buildVmIpParams(vmIpParams, hostId, hostid, ip, newIp, ips, null, poolId, networkIndex);
        UnifiedResultDto<Object> resultDto = vmhostService.deleteVmIp(params);
        if (resultDto.getResultCode().getCode() != UnifiedResultCode.SUCCESS.getCode()) {
            return ResponseResult.fail(resultDto.getResultCode().getCode(),resultDto.getResultCode().getMessage());
        }
        return ResponseResult.ok(resultDto.getData());
    }

    /**
     * @Author: 星禾
     * @Description: 同步节点下手动绑定但未写入vmhost的IP数据
     * @DateTime: 2026/6/6 20:55
    */
    @AdminApiCheck
    @RequestMapping(value = "/syncVmManualIp",method = {RequestMethod.POST,RequestMethod.PUT})
    public Object syncVmManualIp(@RequestBody(required = false) VmIpParams vmIpParams,
                                 @RequestParam(name = "nodeId", required = false) Integer nodeId,
                                 @RequestParam(name = "nodeid", required = false) Integer nodeid) throws UnauthorizedException {
        VmIpParams params = buildSyncVmIpParams(vmIpParams, nodeId, nodeid);
        UnifiedResultDto<Object> resultDto = vmhostService.syncVmManualIp(params);
        if (resultDto.getResultCode().getCode() != UnifiedResultCode.SUCCESS.getCode()) {
            return ResponseResult.fail(resultDto.getResultCode().getCode(),resultDto.getResultCode().getMessage());
        }
        return ResponseResult.ok(resultDto.getData());
    }

    /**
     * @Author: 星禾
     * @Description: 手动同步所有虚拟机防火墙和IP白名单
     * @DateTime: 2026/6/7 23:48
    */
    @AdminApiCheck
    @RequestMapping(value = "/syncVmFirewallProtection",method = {RequestMethod.POST,RequestMethod.PUT})
    public Object syncVmFirewallProtection(@RequestParam(name = "hostId", required = false) Integer hostId,
                                           @RequestParam(name = "hostid", required = false) Integer hostid) {
        try {
            Integer targetHostId = resolveHostId(hostId, hostid);
            boolean started = vmhostService.startSyncVmFirewallProtection(targetHostId);
            if (!started) {
                return ResponseResult.ok("同步任务正在执行中，请勿重复触发");
            }
            if (targetHostId == null) {
                return ResponseResult.ok("全量同步任务已开始执行，请稍后查看日志");
            }
            return ResponseResult.ok("单台虚拟机同步任务已开始执行，请稍后查看日志");
        } catch (Exception e) {
            return ResponseResult.fail("同步任务启动失败: " + e.getMessage());
        }
    }

    /**
     * @Author: 星禾
     * @Description: 回滚虚拟机防火墙和IP白名单同步配置
     * @DateTime: 2026/6/8 0:16
    */
    @AdminApiCheck
    @RequestMapping(value = "/rollbackVmFirewallProtection",method = {RequestMethod.POST,RequestMethod.PUT})
    public Object rollbackVmFirewallProtection(@RequestParam(name = "hostId", required = false) Integer hostId,
                                               @RequestParam(name = "hostid", required = false) Integer hostid) {
        try {
            Integer targetHostId = resolveHostId(hostId, hostid);
            boolean started = vmhostService.startRollbackVmFirewallProtection(targetHostId);
            if (!started) {
                return ResponseResult.ok("当前已有防火墙同步或回滚任务正在执行，请稍后再试");
            }
            if (targetHostId == null) {
                return ResponseResult.ok("全量回滚任务已开始执行，请稍后查看日志");
            }
            return ResponseResult.ok("单台虚拟机回滚任务已开始执行，请稍后查看日志");
        } catch (Exception e) {
            return ResponseResult.fail("回滚任务启动失败: " + e.getMessage());
        }
    }

    private Integer resolveHostId(Integer hostId, Integer hostid) {
        return hostId == null ? hostid : hostId;
    }

    private VmIpParams buildSyncVmIpParams(VmIpParams vmIpParams, Integer nodeId, Integer nodeid) {
        VmIpParams params = vmIpParams == null ? new VmIpParams() : vmIpParams;
        if (params.getNodeId() == null) {
            params.setNodeId(nodeId);
        }
        if (params.getNodeId() == null) {
            params.setNodeId(nodeid);
        }
        return params;
    }

    private VmIpParams buildVmIpParams(VmIpParams vmIpParams, Integer hostId, Integer hostid, String ip, String newIp, Integer poolId, Integer networkIndex) {
        return buildVmIpParams(vmIpParams, hostId, hostid, ip, newIp, null, null, poolId, networkIndex);
    }

    private VmIpParams buildVmIpParams(VmIpParams vmIpParams, Integer hostId, Integer hostid, String ip, String newIp, List<String> ips, Integer count, Integer poolId, Integer networkIndex) {
        VmIpParams params = vmIpParams == null ? new VmIpParams() : vmIpParams;
        if (params.getHostId() == null) {
            params.setHostId(hostId);
        }
        if (params.getHostId() == null) {
            params.setHostId(hostid);
        }
        if (params.getIp() == null) {
            params.setIp(ip);
        }
        if (params.getNewIp() == null) {
            params.setNewIp(newIp);
        }
        if (params.getIps() == null) {
            params.setIps(ips);
        }
        if (params.getCount() == null) {
            params.setCount(count);
        }
        if (params.getPoolId() == null) {
            params.setPoolId(poolId);
        }
        if (params.getNetworkIndex() == null) {
            params.setNetworkIndex(networkIndex);
        }
        return params;
    }
    /**
     * @Author: 星禾
     * @Description: pve虚拟机获取快照列表
     * @DateTime: 2026/5/24 18:11
     */
    @AdminApiCheck
    @GetMapping("/getVmSnapShot/{vmid}")
    public ResponseResult<Object> getVmSnapShot(@PathVariable Integer vmid) {
        Vmhost vmhost = vmhostService.getById(vmid);
        return ResponseResult.ok(vmhostService.getVmSnapShot(vmhost));
    }
    /**
     * @Author: 星禾
     * @Description: pve虚拟机创建快照
     * @DateTime: 2026/5/24 20:31
     */
    @AdminApiCheck
    @RequestMapping(value = "/addVmSnapShot/{hostId}",method = {RequestMethod.POST,RequestMethod.PUT})
    public Object addVmSnapShot(@PathVariable("hostId") Integer hostId,
                                @RequestParam("snapname") String snapname,@RequestParam("vmstate") Boolean vmstate,
                                @RequestParam("description") String  description) throws UnauthorizedException {
        // 判断虚拟机是否存在
        Vmhost vmhost = vmhostService.getById(hostId);
        if (vmhost == null) {
            return ResponseResult.fail("虚拟机不存在");
        }
        Boolean result = vmhostService.addVmSnapShot(vmhost,snapname,vmstate,description);
        if (result == null) {
            return ResponseResult.fail("操作失败");
        }
        return ResponseResult.ok("操作成功");
    }
    /**
     * @Author: 星禾
     * @Description: 删除虚拟机快照
     * @DateTime: 2026/5/24 20:41
     */
    @AdminApiCheck
    @RequestMapping(value = "/deleteVmSnapShot/{hostId}/{snapname}",method = {RequestMethod.POST,RequestMethod.PUT,RequestMethod.DELETE})
    public Object deleteVmSnapShot(@PathVariable("hostId") Long hostId,@PathVariable("snapname") String snapname) throws UnauthorizedException {
        // 判断虚拟机是否存在
        Vmhost vmhost = vmhostService.getById(hostId);
        if (vmhost == null) {
            return ResponseResult.fail("虚拟机不存在");
        }
        Boolean result = vmhostService.deleteVmSnapShot(vmhost,snapname);
        if (result == null) {
            return ResponseResult.fail("操作失败");
        }
        return ResponseResult.ok("操作成功");
    }
    /**
     * @Author: 星禾
     * @Description: pve虚拟机回滚快照
     * @DateTime: 2026/5/24 20:39
     */
    @AdminApiCheck
    @RequestMapping(value = "/rollbackVmSnapShot/{hostId}/{snapname}",method = {RequestMethod.POST,RequestMethod.PUT})
    public Object rollbackVmSnapShot(@PathVariable("hostId") Integer hostId,@PathVariable("snapname") String snapname) throws UnauthorizedException {
        // 判断虚拟机是否存在
        Vmhost vmhost = vmhostService.getById(hostId);
        if (vmhost == null) {
            return ResponseResult.fail("虚拟机不存在");
        }
        Boolean result = vmhostService.rollbackVmSnapShot(vmhost,snapname);
        if (result == null) {
            return ResponseResult.fail("操作失败");
        }
        return ResponseResult.ok("操作成功");
    }
    /**
     * @Author: 鏄熺
     * @Description: 获取指定虚拟机备份列表
     * @DateTime: 2026/5/29 23:03
     */
    @AdminApiCheck
    @GetMapping("/getVmBackup/{hostId}")
    public ResponseResult<Object> getVmBackup(@PathVariable Integer hostId) {
        Vmhost vmhost = vmhostService.getById(hostId);
        if (vmhost == null) {
            return ResponseResult.fail("虚拟机不存在");
        }
        return ResponseResult.ok(vmhostService.getVmBackup(vmhost));
    }

    /**
     * @Author: 鏄熺
     * @Description: 创建指定虚拟机备份
     * @DateTime: 2026/5/29 23:03
     */
    @AdminApiCheck
    @RequestMapping(value = "/addVmBackup/{hostId}", method = {RequestMethod.POST, RequestMethod.PUT})
    public Object addVmBackup(@PathVariable("hostId") Integer hostId,
                              @RequestParam(value = "mode", required = false) String mode,
                              @RequestParam(value = "compress", required = false) String compress,
                              @RequestParam(value = "notes", required = false) String notes) throws UnauthorizedException {
        Vmhost vmhost = vmhostService.getById(hostId);
        if (vmhost == null) {
            return ResponseResult.fail("虚拟机不存在");
        }
        JSONObject result = vmhostService.addVmBackup(vmhost, mode, compress, notes);
        if (result == null) {
            return ResponseResult.fail("操作失败");
        }
        return ResponseResult.ok(result);
    }

    /**
     * @Author: 鏄熺
     * @Description: 删除指定虚拟机备份
     * @DateTime: 2026/5/29 23:03
     */
    @AdminApiCheck
    @RequestMapping(value = "/deleteVmBackup/{hostId}", method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    public Object deleteVmBackup(@PathVariable("hostId") Integer hostId,
                                 @RequestParam("volid") String volid) throws UnauthorizedException {
        Vmhost vmhost = vmhostService.getById(hostId);
        if (vmhost == null) {
            return ResponseResult.fail("虚拟机不存在");
        }
        JSONObject result = vmhostService.deleteVmBackup(vmhost, volid);
        if (result == null) {
            return ResponseResult.fail("操作失败");
        }
        return ResponseResult.ok(result);
    }

    /**
     * @Author: 鏄熺
     * @Description: 还原指定虚拟机备份
     * @DateTime: 2026/5/29 23:03
     */
    @AdminApiCheck
    @RequestMapping(value = "/rollbackVmBackup/{hostId}", method = {RequestMethod.POST, RequestMethod.PUT})
    public Object rollbackVmBackup(@PathVariable("hostId") Integer hostId,
                                   @RequestParam("volid") String volid,
                                   @RequestParam(value = "force", required = false) Boolean force,
                                   @RequestParam(value = "start", required = false) Boolean start) throws UnauthorizedException {
        Vmhost vmhost = vmhostService.getById(hostId);
        if (vmhost == null) {
            return ResponseResult.fail("虚拟机不存在");
        }
        JSONObject result = vmhostService.rollbackVmBackup(vmhost, volid, force, start);
        if (result == null) {
            return ResponseResult.fail("操作失败");
        }
        return ResponseResult.ok(result);
    }
}

