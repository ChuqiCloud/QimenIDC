package com.chuqiyun.proxmoxveams.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import com.chuqiyun.proxmoxveams.entity.Cpuinfo;
import com.chuqiyun.proxmoxveams.entity.Modelgroup;
import com.chuqiyun.proxmoxveams.entity.Smbios;
import com.chuqiyun.proxmoxveams.service.CpuinfoService;
import com.chuqiyun.proxmoxveams.service.ModelgroupService;
import com.chuqiyun.proxmoxveams.service.SmbiosService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * @author mryunqi
 * @date 2023/8/20
 */
@RestController
@RequestMapping("/{adminPath}")
public class SysModelGroupController {
    @Resource
    private ModelgroupService modelgroupService;
    @Resource
    private CpuinfoService cpuinfoService;
    @Resource
    private SmbiosService smbiosService;

    /**
    * @Author: mryunqi
    * @Description: 增加模型组
    * @DateTime: 2023/8/20 16:20
    */
    @AdminApiCheck
    @PostMapping("/addModelGroup")
    public ResponseResult<String> addModelGroup(@RequestBody Modelgroup modelgroup) throws UnauthorizedException {
        Long nowTime = System.currentTimeMillis();
        modelgroup.setCreateDate(nowTime);
        String args = null;
        // 判断cpuModel是否为空
        if (modelgroup.getCpuModel() != null){
            // 判断cpuModel是否存在
            Cpuinfo cpuinfo = cpuinfoService.getById(modelgroup.getCpuModel());
            if (cpuinfo == null){
                return ResponseResult.fail("CPU信息模型不存在");
            }
            args = cpuinfoService.cpuinfoToString(cpuinfo);
        }
        // 判断smbios是否为空
        if (modelgroup.getSmbiosModel() != null){
            // 以逗号分割字符串
            String[] smbiosIds = modelgroup.getSmbiosModel().split(",");
            StringBuilder stringBuilder = new StringBuilder();
            for (String smbiosId : smbiosIds) {
                Smbios smbios = smbiosService.getById(Long.parseLong(smbiosId));
                if (smbios == null){
                    return ResponseResult.fail("SmBios信息模型不存在");
                }
                stringBuilder.append(smbiosService.smbiosToStringArgs(smbios));
            }
            // 去掉最后一个逗号
            if (stringBuilder.length() > 0 && stringBuilder.charAt(stringBuilder.length() - 1) == ',') {
                stringBuilder.setLength(stringBuilder.length() - 1);
            }
            args = args + stringBuilder;
        }
        modelgroup.setArgs(args);
        if (modelgroupService.addModelgroupInfo(modelgroup)){
            return ResponseResult.ok("添加成功");
        }
        return ResponseResult.fail("添加失败");
    }

    /**
    * @Author: mryunqi
    * @Description: 删除模型组
    * @DateTime: 2023/8/20 18:34
    */
    @AdminApiCheck
    @RequestMapping(value = "/deleteModelGroup",method = {RequestMethod.DELETE,RequestMethod.POST})
    public ResponseResult<String> deleteModelGroup(@RequestBody Modelgroup modelgroup) throws UnauthorizedException {
        if (modelgroupService.removeById(modelgroup.getId())){
            return ResponseResult.ok("删除成功");
        }
        return ResponseResult.fail("删除失败");
    }

    /**
    * @Author: mryunqi
    * @Description: 修改模型组
    * @DateTime: 2023/8/20 18:54
    */
    @AdminApiCheck
    @RequestMapping(value = "/updateModelGroup",method = {RequestMethod.PUT,RequestMethod.POST})
    public ResponseResult<String> updateModelGroup(@RequestBody Modelgroup modelgroup) throws UnauthorizedException {
        String args = null;
        // 判断cpuModel是否为空
        if (modelgroup.getCpuModel() != null){
            // 判断cpuModel是否存在
            Cpuinfo cpuinfo = cpuinfoService.getById(modelgroup.getCpuModel());
            if (cpuinfo == null){
                return ResponseResult.fail("CPU信息模型不存在");
            }
            args = cpuinfoService.cpuinfoToString(cpuinfo);
        }
        // 判断smbios是否为空
        if (modelgroup.getSmbiosModel() != null){
            // 以逗号分割字符串
            String[] smbiosIds = modelgroup.getSmbiosModel().split(",");
            StringBuilder stringBuilder = new StringBuilder();
            for (String smbiosId : smbiosIds) {
                Smbios smbios = smbiosService.getById(Long.parseLong(smbiosId));
                if (smbios == null){
                    return ResponseResult.fail("SmBios信息模型不存在");
                }
                stringBuilder.append(smbiosService.smbiosToStringArgs(smbios));
            }
            // 去掉最后一个逗号
            if (stringBuilder.length() > 0 && stringBuilder.charAt(stringBuilder.length() - 1) == ',') {
                stringBuilder.setLength(stringBuilder.length() - 1);
            }
            args = args + stringBuilder;
        }
        modelgroup.setArgs(args);
        if (modelgroupService.updateById(modelgroup)){
            return ResponseResult.ok("修改成功");
        }
        return ResponseResult.fail("修改失败");
    }

    /**
    * @Author: mryunqi
    * @Description: 查询模型组
    * @DateTime: 2023/8/20 18:57
    */
    @AdminApiCheck
    @GetMapping("/getModelGroup")
    public ResponseResult<Modelgroup> getModelGroup(@RequestParam("modelGroupId") Long modelGroupId) throws UnauthorizedException {
        Modelgroup modelgroup = modelgroupService.getById(modelGroupId);
        if (modelgroup == null){
            return ResponseResult.fail("模型组不存在");
        }
        return ResponseResult.ok(modelgroup);
    }

    /**
    * @Author: mryunqi
    * @Description: 分页查询模型组
    * @DateTime: 2023/8/20 18:57
    */
    @AdminApiCheck
    @GetMapping("/getModelGroupPage")
    public ResponseResult<Object> getModelGroupPage(@RequestParam("page") Integer page,
                                                              @RequestParam("size") Integer size) throws UnauthorizedException {
        Page<Modelgroup> modelgroupPage = new Page<>(page, size);
        modelgroupPage = modelgroupService.page(modelgroupPage);
        return ResponseResult.ok(modelgroupPage);
    }
}
