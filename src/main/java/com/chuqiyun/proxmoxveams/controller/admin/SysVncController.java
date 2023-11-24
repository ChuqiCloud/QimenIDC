package com.chuqiyun.proxmoxveams.controller.admin;

import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.entity.Vncnode;
import com.chuqiyun.proxmoxveams.service.VncService;
import com.chuqiyun.proxmoxveams.service.VncnodeService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * @author mryunqi
 * @date 2023/11/22
 */
@RestController
@RequestMapping("/{adminPath}")
public class SysVncController {
    @Resource
    private VncnodeService vncnodeService;
    @Resource
    private VncService vncService;
    /**
    * @Author: mryunqi
    * @Description: 增加vnc控制器节点
    * @DateTime: 2023/11/23 15:21
    */
    @AdminApiCheck
    @PostMapping(value = "/addVncNode")
    public ResponseResult<Object> addVncNode(@RequestBody Vncnode vncnode) throws UnauthorizedException {
        long createTime = System.currentTimeMillis();
        vncnode.setCreateDate(createTime);
        if (vncnodeService.addVncnode(vncnode)) {
            return ResponseResult.ok("添加成功");
        }
        else {
            return ResponseResult.fail("添加失败");
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 删除vnc控制器节点
    * @DateTime: 2023/11/23 16:40
    */
    @AdminApiCheck
    @DeleteMapping(value = "/deleteVncNode")
    public ResponseResult<Object> deleteVncNode(@RequestParam(name="id") Long id) throws UnauthorizedException {
        if (vncnodeService.deleteVncnode(id)) {
            return ResponseResult.ok("删除成功");
        }
        else {
            return ResponseResult.fail("删除失败");
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 修改vnc控制器节点
    * @DateTime: 2023/11/23 16:41
    */
    @AdminApiCheck
    @PostMapping(value = "/updateVncNode")
    @PutMapping(value = "/updateVncNode")
    public ResponseResult<Object> updateVncNode(@RequestBody Vncnode vncnode) throws UnauthorizedException {
        if (vncnodeService.updateVncnode(vncnode)) {
            return ResponseResult.ok("修改成功");
        }
        else {
            return ResponseResult.fail("修改失败");
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 分页查询vnc控制器节点
    * @DateTime: 2023/11/23 16:42
    */
    @AdminApiCheck
    @GetMapping(value = "/selectVncNodePage")
    public ResponseResult<Object> selectVncNodePage(@RequestParam(name = "page",defaultValue = "1") Integer page,
                                                    @RequestParam(name = "size",defaultValue = "20") Integer size) throws UnauthorizedException {
        return ResponseResult.ok(vncnodeService.selectVncnodePage(page, size));
    }

    /**
    * @Author: mryunqi
    * @Description: 获取指定虚拟机的vnc地址
    * @DateTime: 2023/11/24 23:01
    */
    @AdminApiCheck
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

}
