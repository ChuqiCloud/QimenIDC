package com.chuqiyun.proxmoxveams.controller.admin;

import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.dto.pvesdn.ZonesParams;
import com.chuqiyun.proxmoxveams.entity.Subnet;
import com.chuqiyun.proxmoxveams.entity.Vnets;
import com.chuqiyun.proxmoxveams.service.PveSdnService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * @author mryunqi
 * @date 2024/1/21
 */
@RestController
@RequestMapping("/{adminPath}")
public class SysSdnController {
    @Resource
    private PveSdnService pveSdnService;

    /**
    * @Author: mryunqi
    * @Description: 添加sdn区域
    * @DateTime: 2024/1/21 15:31
    */
    @AdminApiCheck
    @PostMapping("/sdn/addZone")
    public ResponseResult<Object> addZone(@RequestBody ZonesParams zonesParams) throws UnauthorizedException {
        UnifiedResultDto<Object> resultDto = pveSdnService.addZone(zonesParams);
        if (resultDto.getResultCode().getCode() != UnifiedResultCode.SUCCESS.getCode()) {
            return ResponseResult.fail(resultDto.getResultCode().getCode(),resultDto.getResultCode().getMessage());
        }
        return ResponseResult.ok(resultDto.getResultCode().getMessage());
    }

    /**
    * @Author: mryunqi
    * @Description: 根据id删除sdn区域
    * @DateTime: 2024/1/21 16:44
    */
    @AdminApiCheck
    @RequestMapping(value = "/sdn/deleteZoneById/{id}",method = {RequestMethod.POST,RequestMethod.DELETE})
    public ResponseResult<Object> deleteZoneById(@PathVariable Integer id) throws UnauthorizedException {
        UnifiedResultDto<Object> resultDto = pveSdnService.deleteZoneById(id);
        if (resultDto.getResultCode().getCode() != UnifiedResultCode.SUCCESS.getCode()) {
            return ResponseResult.fail(resultDto.getResultCode().getCode(),resultDto.getResultCode().getMessage());
        }
        return ResponseResult.ok(resultDto.getResultCode().getMessage());
    }

    /**
    * @Author: mryunqi
    * @Description: 根据标识zone删除sdn区域
    * @DateTime: 2024/1/21 16:58
    */
    @AdminApiCheck
    @RequestMapping(value = "/sdn/deleteZoneByZone/{zone}",method = {RequestMethod.POST,RequestMethod.DELETE})
    public ResponseResult<Object> deleteZoneByZone(@PathVariable String zone) throws UnauthorizedException {
        UnifiedResultDto<Object> resultDto = pveSdnService.deleteZoneByZone(zone);
        if (resultDto.getResultCode().getCode() != UnifiedResultCode.SUCCESS.getCode()) {
            return ResponseResult.fail(resultDto.getResultCode().getCode(),resultDto.getResultCode().getMessage());
        }
        return ResponseResult.ok(resultDto.getResultCode().getMessage());
    }

    /**
    * @Author: mryunqi
    * @Description: 查询sdn区域列表
    * @DateTime: 2024/1/21 17:11
    */
    @AdminApiCheck
    @GetMapping("/sdn/getZonesByPage")
    public ResponseResult<Object> getZonesByPage(@RequestParam(name = "page",defaultValue = "1") Integer page,
                                                 @RequestParam(name = "size",defaultValue = "20") Integer size) throws UnauthorizedException {
        return ResponseResult.ok(pveSdnService.getZonesByPage(page,size));
    }

    /**
    * @Author: mryunqi
    * @Description: 添加vnet区域
    * @DateTime: 2024/1/24 23:25
    */
    @AdminApiCheck
    @PostMapping("/sdn/addVnet")
    public ResponseResult<Object> addVnet(@RequestBody Vnets vnets) throws UnauthorizedException {
        UnifiedResultDto<Object> resultDto = pveSdnService.addVnet(vnets);
        if (resultDto.getResultCode().getCode() != UnifiedResultCode.SUCCESS.getCode()) {
            return ResponseResult.fail(resultDto.getResultCode().getCode(),resultDto.getResultCode().getMessage());
        }
        return ResponseResult.ok(resultDto.getResultCode().getMessage());
    }

    /**
    * @Author: mryunqi
    * @Description: 查询vnet列表
    * @DateTime: 2024/1/24 23:26
    */
    @AdminApiCheck
    @GetMapping("/sdn/getVnetsByPage")
    public ResponseResult<Object> getVnetsByPage(@RequestParam(name = "page",defaultValue = "1") Integer page,
                                                 @RequestParam(name = "size",defaultValue = "20") Integer size) throws UnauthorizedException {
        return ResponseResult.ok(pveSdnService.getVnetsByPage(page,size));
    }

    /**
    * @Author: mryunqi
    * @Description: 添加subnet子网
    * @DateTime: 2024/1/26 16:57
    */
    @AdminApiCheck
    @PostMapping("/sdn/addSubnet")
    public ResponseResult<Object> addSubnet(@RequestBody Subnet subnet) throws UnauthorizedException {
        UnifiedResultDto<Object> resultDto = pveSdnService.addSubnet(subnet);
        if (resultDto.getResultCode().getCode() != UnifiedResultCode.SUCCESS.getCode()) {
            return ResponseResult.fail(resultDto.getResultCode().getCode(),resultDto.getResultCode().getMessage());
        }
        return ResponseResult.ok(resultDto.getResultCode().getMessage());
    }

    /**
    * @Author: mryunqi
    * @Description: 根据vnet查询subnet列表
    * @DateTime: 2024/1/26 17:01
    */
    @AdminApiCheck
    @GetMapping("/sdn/{vnet}/getSubnets")
    public ResponseResult<Object> getSubnetsByVnet(@PathVariable String vnet,
                                                   @RequestParam(name = "page",defaultValue = "1") Integer page,
                                                   @RequestParam(name = "size",defaultValue = "20") Integer size) throws UnauthorizedException {
        return ResponseResult.ok(pveSdnService.getSubnetsByVnet(vnet,page,size));
    }
}
