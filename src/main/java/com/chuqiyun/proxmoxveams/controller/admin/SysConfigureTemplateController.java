package com.chuqiyun.proxmoxveams.controller.admin;

import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import com.chuqiyun.proxmoxveams.entity.Configuretemplate;
import com.chuqiyun.proxmoxveams.service.ConfiguretemplateService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * @author mryunqi
 * @date 2023/9/21
 */
@RestController
@RequestMapping("/{adminPath}")
public class SysConfigureTemplateController {
    @Resource
    private ConfiguretemplateService configuretemplateService;

    /**
    * @Author: mryunqi
    * @Description: 增加配置模板
    * @DateTime: 2023/9/21 22:59
    */
    @AdminApiCheck
    @PostMapping("/addConfiguretemplate")
    public ResponseResult<String> addConfiguretemplate(@RequestBody Configuretemplate configuretemplate)
            throws UnauthorizedException {
        if (configuretemplateService.addConfiguretemplate(configuretemplate)){
            return ResponseResult.ok("增加配置模板成功");
        }
        return ResponseResult.fail("增加配置模板失败");
    }

    /**
    * @Author: mryunqi
    * @Description: 删除配置模板
    * @DateTime: 2023/9/21 23:08
    */
    @AdminApiCheck
    @DeleteMapping(value = "/deleteConfiguretemplate/{id}")
    public ResponseResult<String> deleteConfiguretemplate(@PathVariable("id") Integer id)
            throws UnauthorizedException {
        if (configuretemplateService.deleteConfiguretemplateById(id)){
            return ResponseResult.ok("删除配置模板成功");
        }
        return ResponseResult.fail("删除配置模板失败");
    }

    /**
    * @Author: mryunqi
    * @Description: 修改配置模板
    * @DateTime: 2023/9/21 23:09
    */
    @AdminApiCheck
    @RequestMapping(value = "/updateConfiguretemplate",method = {RequestMethod.PUT,RequestMethod.POST})
    public ResponseResult<String> updateConfiguretemplate(@RequestBody Configuretemplate configuretemplate)
            throws UnauthorizedException {
        if (configuretemplateService.updateConfiguretemplate(configuretemplate)){
            return ResponseResult.ok("修改配置模板成功");
        }
        return ResponseResult.fail("修改配置模板失败");
    }

    /**
    * @Author: mryunqi
    * @Description: 分页查询配置模板
    * @DateTime: 2023/9/21 23:10
    */
    @AdminApiCheck
    @GetMapping("/getConfiguretemplateByPage")
    public ResponseResult<Object> getConfiguretemplateByPage(@RequestParam("page") Integer page,
                                                             @RequestParam("limit") Integer limit)
            throws UnauthorizedException {
        return ResponseResult.ok(configuretemplateService.selectConfiguretemplatePage(page,limit));
    }
}
