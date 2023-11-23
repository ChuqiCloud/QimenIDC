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
@RequestMapping("/{adminPath}")
public class SysApiController {
    @Resource
    private SysapiService sysapiService;

    /**
    * @Author: mryunqi
    * @Description: 添加API
    * @DateTime: 2023/5/8 17:19
    */
    @AdminApiCheck
    @PostMapping("/insertApiKey")
    public ResponseResult<Object> insertApiKey(@RequestBody Sysapi params)throws UnauthorizedException {
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
    @GetMapping("/selectApiByPage")
    public ResponseResult<Object> selectApiByPage(@RequestParam(name = "page",defaultValue = "1") Integer page,
                                                  @RequestParam(name = "size", defaultValue = "20") Integer size) throws UnauthorizedException {
        return ResponseResult.ok(sysapiService.selectSysapiPage(page,size));
    }

    /**
    * @Author: mryunqi
    * @Description: 删除API
    * @DateTime: 2023/7/24 22:27
    */
    @AdminApiCheck
    @RequestMapping(value = "/deleteApi",method = {RequestMethod.POST,RequestMethod.DELETE})
    public ResponseResult<Object> deleteApi(@RequestParam(name = "id") Integer id) throws UnauthorizedException {
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
    @RequestMapping(value = "/disableApi/{id}",method = {RequestMethod.POST,RequestMethod.PUT})
    public ResponseResult<Object> disableApi(@PathVariable("id") Integer id) throws UnauthorizedException {
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

    /**
    * @Author: mryunqi
    * @Description: 启用指定API
    * @DateTime: 2023/10/18 22:42
    */
    @AdminApiCheck
    @RequestMapping(value = "/enableApi/{id}",method = {RequestMethod.POST,RequestMethod.PUT})
    public ResponseResult<Object> enableApi(@PathVariable("id") Integer id) throws UnauthorizedException {
        // 判断id是否为空
        if (Objects.isNull(id)){
            return ResponseResult.fail("id不能为空");
        }
        // 判断是否存在该id
        Sysapi sysapi = sysapiService.getById(id);
        if (Objects.isNull(sysapi)){
            return ResponseResult.fail("不存在该API");
        }
        // 判断是否已经启用
        if (sysapi.getStatus() == 0){
            return ResponseResult.fail("该API已经启用");
        }
        // 启用API
        sysapi.setStatus(0);
        if (sysapiService.updateById(sysapi)){
            return ResponseResult.ok("启用成功");
        }
        return ResponseResult.fail("启用失败");
    }

}
