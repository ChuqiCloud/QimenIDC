package com.chuqiyun.proxmoxveams.controller.api.v1;

import com.chuqiyun.proxmoxveams.annotation.PublicSysApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.entity.Vncinfo;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import com.chuqiyun.proxmoxveams.service.VncService;
import com.chuqiyun.proxmoxveams.service.VncinfoService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * @author mryunqi
 * @date 2023/11/25
 */
@RestController
@RequestMapping("/api/v1")
public class GetVnc {
    @Resource
    private VncService vncService;
    @Resource
    private VmhostService vmhostService;
    @Resource
    private VncinfoService vncinfoService;

    @PublicSysApiCheck
    @GetMapping(value = "/{node}/getVnc")
    public ResponseResult<Object> getVnc(@PathVariable(name = "node") String node,
                                         @RequestParam(name = "hostId") Long hostId,
                                         @RequestParam(name = "page",defaultValue = "1") Integer page,
                                         @RequestParam(name = "size",defaultValue = "5") Integer size) throws UnauthorizedException {
        if (node.equals("pve")){
            UnifiedResultDto<Object> resultDto = vncService.getVncInfo(hostId, page, size);
            if (resultDto.getResultCode().getCode() != UnifiedResultCode.SUCCESS.getCode()) {
                return ResponseResult.fail(resultDto.getResultCode().getCode(),resultDto.getResultCode().getMessage());
            }
            return ResponseResult.ok(resultDto.getData());
        }
        else {
            return ResponseResult.fail("暂不支持该节点");
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 修改指定虚拟机的VNC密码
    * @DateTime: 2023/11/26 16:27
    */
    @PublicSysApiCheck
    @RequestMapping(value = "/{node}/updateVncPassword",method = {RequestMethod.POST,RequestMethod.PUT})
    public ResponseResult<Object> updateVncPassword(@PathVariable(name = "node") String node,
                                                    @RequestBody Vncinfo vncinfo) throws UnauthorizedException {
        if (node.equals("pve")){
            // 判断hostId是否为空
            if (vncinfo.getHostId() == null){
                return ResponseResult.fail("hostId不能为空");
            }
            // 判断vncPassword是否为空
            if (vncinfo.getPassword() == null){
                return ResponseResult.fail("Password不能为空");
            }
            // 获取虚拟机信息
            Vmhost vmhost = vmhostService.getById(vncinfo.getHostId());
            // 如果虚拟机不存在
            if (vmhost == null){
                // 将vmHostId作为为vmid
                vmhost = vmhostService.getVmhostByVmId(Math.toIntExact(vncinfo.getHostId()));
            }
            // 如果虚拟机还是不存在
            if (vmhost == null){
                return ResponseResult.fail("虚拟机不存在");
            }
            Vncinfo currentVncinfo = vncinfoService.selectVncinfoByHostId(Long.valueOf(vmhost.getId()));
            // 如果虚拟机没有vnc信息
            if (currentVncinfo == null){
                return ResponseResult.fail("虚拟机没有VNC信息,请先获取VNC信息");
            }
            currentVncinfo.setPassword(vncinfo.getPassword());
            // 修改vnc密码
            if (vncinfoService.updateVncinfo(currentVncinfo)){
                return ResponseResult.ok("修改成功");
            }
            else {
                return ResponseResult.fail("修改失败");
            }
        }
        else {
            return ResponseResult.fail("暂不支持该节点");
        }
    }
}
