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
public class SysConfigureTemplateController {
    @Value("${config.admin_path}")
    private String ADMIN_PATH;
    @Resource
    private ConfiguretemplateService configuretemplateService;

    /**
    * @Author: mryunqi
    * @Description: 增加配置模板
    * @DateTime: 2023/9/21 22:59
    */
    @AdminApiCheck
    @PostMapping("/{adminPath}/addConfiguretemplate")
    public ResponseResult<String> addConfiguretemplate(@PathVariable("adminPath") String adminPath,
                                                       @RequestBody Configuretemplate configuretemplate)
            throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
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
    @DeleteMapping(value = "/{adminPath}/deleteConfiguretemplate/{id}")
    public ResponseResult<String> deleteConfiguretemplate(@PathVariable("adminPath") String adminPath,
                                                          @PathVariable("id") Integer id)
            throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
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
    @RequestMapping(value = "/{adminPath}/updateConfiguretemplate",method = {RequestMethod.PUT,RequestMethod.POST})
    public ResponseResult<String> updateConfiguretemplate(@PathVariable("adminPath") String adminPath,
                                                          @RequestBody Configuretemplate configuretemplate)
            throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
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
    @GetMapping("/{adminPath}/getConfiguretemplateByPage")
    public ResponseResult<Object> getConfiguretemplateByPage(@PathVariable("adminPath") String adminPath,
                                                                        @RequestParam("page") Integer page,
                                                                        @RequestParam("limit") Integer limit)
            throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
        return ResponseResult.ok(configuretemplateService.selectConfiguretemplatePage(page,limit));
    }
}
