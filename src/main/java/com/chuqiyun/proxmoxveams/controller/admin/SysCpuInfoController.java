package com.chuqiyun.proxmoxveams.controller.admin;

import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import com.chuqiyun.proxmoxveams.entity.Cpuinfo;
import com.chuqiyun.proxmoxveams.service.CpuinfoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * @author mryunqi
 * @date 2023/8/19
 */
@RestController
@RequestMapping("/{adminPath}")
public class SysCpuInfoController {
    @Resource
    private CpuinfoService cpuinfoService;

    /**
    * @Author: mryunqi
    * @Description: 新增cpu信息模型
    * @DateTime: 2023/8/19 23:10
    */
    @AdminApiCheck
    @PostMapping("/addCpuInfo")
    public ResponseResult<String> addCpuInfo(@RequestBody Cpuinfo cpuinfo)
            throws UnauthorizedException {
        Long nowTime = System.currentTimeMillis();
        cpuinfo.setCreateDate(nowTime);
        if (cpuinfoService.addCpuInfo(cpuinfo)){
            return ResponseResult.ok("添加成功");
        }
        return ResponseResult.fail("添加失败");
    }

    /**
    * @Author: mryunqi
    * @Description: 删除cpu信息模型
    * @DateTime: 2023/8/19 23:16
    */
    @AdminApiCheck
    @RequestMapping(value = "/deleteCpuInfo",method = {RequestMethod.POST,RequestMethod.DELETE})
    public ResponseResult<String> deleteCpuInfo(@RequestBody Cpuinfo cpuinfo)
            throws UnauthorizedException {
        if (cpuinfoService.removeById(cpuinfo.getId())){
            return ResponseResult.ok("删除成功");
        }
        return ResponseResult.fail("删除失败");
    }

    /**
    * @Author: mryunqi
    * @Description: 修改cpu信息模型
    * @DateTime: 2023/8/19 23:20
    */
    @AdminApiCheck
    @RequestMapping(value = "/updateCpuInfo",method = {RequestMethod.POST,RequestMethod.PUT})
    public ResponseResult<String> updateCpuInfo(@RequestBody Cpuinfo cpuinfo)
            throws UnauthorizedException {
        if (cpuinfoService.updateById(cpuinfo)){
            return ResponseResult.ok("修改成功");
        }
        return ResponseResult.fail("修改失败");
    }

    /**
    * @Author: mryunqi
    * @Description: 查询cpu信息模型
    * @DateTime: 2023/8/20 15:26
    */
    @AdminApiCheck
    @GetMapping("/selectCpuInfo")
    public ResponseResult<Object> selectCpuInfo(@RequestParam("id") Integer id)
            throws UnauthorizedException {
        return ResponseResult.ok(cpuinfoService.getById(id));
    }

    /**
    * @Author: mryunqi
    * @Description: 分页查询cpu信息模型
    * @DateTime: 2023/8/19 23:23
    */
    @AdminApiCheck
    @GetMapping("/selectCpuInfoPage")
    public ResponseResult<Object> selectCpuInfoPage(@RequestParam(name = "page",defaultValue = "1") Integer page,
                                                     @RequestParam(name = "limit",defaultValue = "20") Integer limit)
            throws UnauthorizedException {
        return ResponseResult.ok(cpuinfoService.selectCpuInfoPage(page,limit));
    }
}
