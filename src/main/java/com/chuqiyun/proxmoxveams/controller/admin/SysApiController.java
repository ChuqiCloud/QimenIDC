package com.chuqiyun.proxmoxveams.controller.admin;

import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.entity.Sysapi;
import com.chuqiyun.proxmoxveams.service.SysapiService;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Objects;
import java.util.UUID;

/**
 * @author mryunqi
 * @date 2023/5/8
 */
@RestController
public class SysApiController {
    @Value("${config.admin_path}")
    private String ADMIN_PATH;

    @Resource
    private SysapiService sysapiService;

    /**
    * @Author: mryunqi
    * @Description: 添加API
    * @DateTime: 2023/5/8 17:19
    */
    @AdminApiCheck
    @PostMapping("/{adminPath}/insertApiKey")
    public ResponseResult<Object> insertApiKey(@PathVariable("adminPath") String adminPath,
                                       @RequestBody Sysapi params)throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
        String appId = String.valueOf(System.currentTimeMillis());
        String appKey = UUID.randomUUID().toString().replace("-", "") + Thread.currentThread().getId();
        Sysapi sysapi = new Sysapi();
        sysapi.setAppkey(appKey);
        sysapi.setAppid(appId);
        sysapi.setInfo(params.getInfo());
        sysapi.setStatus(0);
        sysapi.setCreateDate(System.currentTimeMillis());
        if (sysapiService.save(sysapi)){
            return ResponseResult.ok(sysapi);
        }
        return ResponseResult.fail("添加失败");
    }
    
    /**
    * @Author: mryunqi
    * @Description: 分页获取API信息
    * @DateTime: 2023/7/24 22:11
    */
    @AdminApiCheck
    @GetMapping("/{adminPath}/selectApiByPage")
    public ResponseResult<Object> selectApiByPage(@PathVariable("adminPath") String adminPath,
                                                  @RequestParam(name = "page",defaultValue = "1") Integer page,
                                                  @RequestParam(name = "size", defaultValue = "20") Integer size) throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
        return ResponseResult.ok(sysapiService.selectSysapiPage(page,size));
    }

    /**
    * @Author: mryunqi
    * @Description: 删除API
    * @DateTime: 2023/7/24 22:27
    */
    @AdminApiCheck
    @RequestMapping(value = "/{adminPath}/deleteApi",method = {RequestMethod.POST,RequestMethod.DELETE})
    public ResponseResult<Object> deleteApi(@PathVariable("adminPath") String adminPath,
                                            @RequestParam(name = "id") Integer id) throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
        if (Objects.isNull(id)){
            return ResponseResult.fail("id不能为空");
        }
        if (sysapiService.deleteSysapiById(id)){
            return ResponseResult.ok("删除成功");
        }
        return ResponseResult.fail("删除失败");
    }

    /**
    * @Author: mryunqi
    * @Description: 停用指定API
    * @DateTime: 2023/8/16 23:05
    * @Return
    */
    @AdminApiCheck
    @RequestMapping(value = "/{adminPath}/disableApi/{id}",method = {RequestMethod.POST,RequestMethod.PUT})
    public ResponseResult<Object> disableApi(@PathVariable("adminPath") String adminPath,
                                             @PathVariable("id") Integer id) throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
        // 判断id是否为空
        if (Objects.isNull(id)){
            return ResponseResult.fail("id不能为空");
        }
        // 判断是否存在该id
        Sysapi sysapi = sysapiService.getById(id);
        if (Objects.isNull(sysapi)){
            return ResponseResult.fail("不存在该API");
        }
        // 判断是否已经停用
        if (sysapi.getStatus() == 1){
            return ResponseResult.fail("该API已经停用");
        }
        // 停用API
        sysapi.setStatus(1);
        if (sysapiService.updateById(sysapi)){
            return ResponseResult.ok("停用成功");
        }
        return ResponseResult.fail("停用失败");
    }

}
