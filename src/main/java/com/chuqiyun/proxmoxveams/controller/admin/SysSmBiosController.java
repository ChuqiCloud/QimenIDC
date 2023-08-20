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
public class SysSmBiosController {
    @Value("${config.admin_path}")
    private String ADMIN_PATH;
    @Resource
    private SmbiosService smbiosService;

    /**
    * @Author: mryunqi
    * @Description: 新增smbios信息模型
    * @DateTime: 2023/8/20 14:51
    */
    @AdminApiCheck
    @PostMapping("/{adminPath}/addSmbiosInfo")
    public ResponseResult<Object> addSmbiosInfo(@PathVariable("adminPath") String adminPath,
                                                @RequestBody Smbios smbios)
            throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
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
    @RequestMapping(value = "/{adminPath}/deleteSmbiosInfo",method = {RequestMethod.POST,RequestMethod.DELETE})
    public ResponseResult<String> deleteSmbiosInfo(@PathVariable("adminPath") String adminPath,
                                                   @RequestBody Smbios smbios)
            throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
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
    @RequestMapping(value = "/{adminPath}/updateSmbiosInfo",method = {RequestMethod.POST,RequestMethod.PUT})
    public ResponseResult<String> updateSmbiosInfo(@PathVariable("adminPath") String adminPath,
                                                   @RequestBody Smbios smbios)
            throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
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
    @GetMapping("/{adminPath}/getSmbiosInfo")
    public ResponseResult<Smbios> getSmbiosInfo(@PathVariable("adminPath") String adminPath,
                                                 @RequestParam("id") Long id)
            throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
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
    @GetMapping("/{adminPath}/getSmbiosInfoList")
    public ResponseResult<Object> getSmbiosInfoList(@PathVariable("adminPath") String adminPath,
                                                     @RequestParam(name = "page",defaultValue = "1") Integer page,
                                                     @RequestParam(name = "limit",defaultValue = "20") Integer limit)
            throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
        return ResponseResult.ok(smbiosService.selectSmbiosInfoPage(page,limit));
    }
}
