package com.chuqiyun.proxmoxveams.controller.admin;

import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import com.chuqiyun.proxmoxveams.entity.Smbios;
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
public class SysSmBiosController {
    @Resource
    private SmbiosService smbiosService;

    /**
    * @Author: mryunqi
    * @Description: 新增smbios信息模型
    * @DateTime: 2023/8/20 14:51
    */
    @AdminApiCheck
    @PostMapping("/addSmbiosInfo")
    public ResponseResult<Object> addSmbiosInfo(@RequestBody Smbios smbios)
            throws UnauthorizedException {
        Long nowTime = System.currentTimeMillis();
        // 判断type是否合规，小于128
        if (smbios.getType() > 128){
            return ResponseResult.fail("type值不合规");
        }
        smbios.setCreateDate(nowTime);
        if (smbiosService.addSmbiosInfo(smbios)){
            return ResponseResult.ok("添加成功");
        }
        return ResponseResult.fail("添加失败");
    }

    /**
    * @Author: mryunqi
    * @Description: 删除smbios信息模型
    * @DateTime: 2023/8/20 15:18
    */
    @AdminApiCheck
    @RequestMapping(value = "/deleteSmbiosInfo",method = {RequestMethod.POST,RequestMethod.DELETE})
    public ResponseResult<String> deleteSmbiosInfo(@RequestBody Smbios smbios)
            throws UnauthorizedException {
        if (smbiosService.removeById(smbios.getId())){
            return ResponseResult.ok("删除成功");
        }
        return ResponseResult.fail("删除失败");
    }

    /**
    * @Author: mryunqi
    * @Description: 修改smbios信息模型
    * @DateTime: 2023/8/20 15:19
    */
    @AdminApiCheck
    @RequestMapping(value = "/updateSmbiosInfo",method = {RequestMethod.POST,RequestMethod.PUT})
    public ResponseResult<String> updateSmbiosInfo(@RequestBody Smbios smbios)
            throws UnauthorizedException {
        if (smbiosService.updateById(smbios)){
            return ResponseResult.ok("修改成功");
        }
        return ResponseResult.fail("修改失败");
    }

    /**
    * @Author: mryunqi
    * @Description: 查询smbios信息模型
    * @DateTime: 2023/8/20 15:20
    */
    @AdminApiCheck
    @GetMapping("/getSmbiosInfo")
    public ResponseResult<Smbios> getSmbiosInfo(@RequestParam("id") Long id)
            throws UnauthorizedException {
        Smbios smbios = smbiosService.getById(id);
        if (smbios != null){
            return ResponseResult.ok(smbios);
        }
        return ResponseResult.fail("查询失败");
    }

    /**
    * @Author: mryunqi
    * @Description: 查询smbios信息模型列表
    * @DateTime: 2023/8/20 15:20
    */
    @AdminApiCheck
    @GetMapping("/getSmbiosInfoList")
    public ResponseResult<Object> getSmbiosInfoList(@RequestParam(name = "page",defaultValue = "1") Integer page,
                                                     @RequestParam(name = "limit",defaultValue = "20") Integer limit)
            throws UnauthorizedException {
        return ResponseResult.ok(smbiosService.selectSmbiosInfoPage(page,limit));
    }
}
