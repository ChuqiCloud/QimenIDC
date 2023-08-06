package com.chuqiyun.proxmoxveams.controller.admin;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Os;
import com.chuqiyun.proxmoxveams.dto.OsParams;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.OsService;
import com.chuqiyun.proxmoxveams.utils.ClientApiUtil;
import com.chuqiyun.proxmoxveams.utils.ModUtil;
import com.chuqiyun.proxmoxveams.utils.OsTypeUtil;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;

/**
 * @author mryunqi
 * @date 2023/7/9
 */
@Slf4j
@RestController
public class SysOsController {
    @Value("${config.admin_path}")
    private String ADMIN_PATH;

    @Value("${config.secret}")
    private String secret;
    @Value("${config.os_url}")
    private String osUrl;
    @Resource
    private OsService osService;
    @Resource
    private MasterService masterService;
    /**
     * 获取所有在线os
     * @param adminPath 后台路径 page 页数 size 每页大小
     * @throws UnauthorizedException
     */
    @AdminApiCheck
    @GetMapping("/{adminPath}/selectOsByOnline")
    public ResponseResult<JSONObject> selectOsByOnline(@PathVariable("adminPath") String adminPath,
                                           @RequestParam(name = "page",defaultValue = "1") Integer page,
                                           @RequestParam(name = "size", defaultValue = "20") Integer size) throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
        JSONObject osOnlineResult = ClientApiUtil.getNetOs(osUrl);
        // 分页，获取分页数据
        osOnlineResult = ModUtil.jsonObjectPage(osOnlineResult,page,size);
        JSONArray jsonArray = osOnlineResult.getJSONArray("data");
        for (int i = 0; i < jsonArray.size(); i++){
            // 将其转换为json对象
            JSONObject jsonObject = (JSONObject) jsonArray.get(i);
            // 获取第一个key
            String key = jsonObject.keySet().iterator().next();
            JSONObject value = jsonObject.getJSONObject(key);
            JSONArray nodeData = osService.selectOsByOsName(key);
            value.put("nodeData",nodeData);
            jsonObject.put(key, value);
            // 更新jsonArray
            jsonArray.set(i,jsonObject);
        }
        osOnlineResult.put("data",jsonArray);
        return ResponseResult.ok(osOnlineResult);
    }
    /**
     * 插入新的OS
     * @param adminPath 后台路径 osParams os参数
     * @throws UnauthorizedException
     */
    @AdminApiCheck
    @PostMapping("/{adminPath}/insertOs")
    public ResponseResult<Object> downloadOs(@PathVariable("adminPath") String adminPath,
                                     @RequestBody OsParams osParams) throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
        HashMap<String,Object> result = osService.insertOs(osParams);
        if (result.get("code").equals(0)){
            return ResponseResult.ok(result.get("msg"));
        }
        return ResponseResult.fail((String) result.get("msg"));

    }

    /**
     * 分页获取已添加os
     * @param adminPath 后台路径 page 页数 size 每页大小
     * @throws UnauthorizedException
     */
    @AdminApiCheck
    @GetMapping("/{adminPath}/selectOsByPage")
    public ResponseResult<Object> selectOsByPage(@PathVariable("adminPath") String adminPath,
                                           @RequestParam(name = "page",defaultValue = "1") Integer page,
                                           @RequestParam(name = "size", defaultValue = "20") Integer size) throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
        Page<Os> osPage = osService.selectOsByPage(page,size);
        return ResponseResult.ok(osPage);
    }
    /**
     * 分页带条件获取已添加os
     * @param adminPath 后台路径 page 页数 size 每页大小
     * @throws UnauthorizedException
     */
    @AdminApiCheck
    @GetMapping("/{adminPath}/selectOsByPageAndCondition")
    public ResponseResult<Object> selectOsByPageAndCondition(@PathVariable("adminPath") String adminPath,
                                           @RequestParam(name = "page",defaultValue = "1") Integer page,
                                           @RequestParam(name = "size", defaultValue = "20") Integer size,
                                           @RequestParam(name = "param") String param,
                                           @RequestParam(name = "value") String value) throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
        QueryWrapper<Os> queryWrapper = new QueryWrapper<>();
        param = OsTypeUtil.getOsEntityDbName(param);
        queryWrapper.like(param,value);
        Page<Os> osPage = osService.selectOsByPage(page,size,queryWrapper);
        return ResponseResult.ok(osPage);
    }

    /**
     * 下载os到节点
     * @param adminPath 后台路径 osId osId
     * @throws UnauthorizedException
     */
    @AdminApiCheck
    @PostMapping("/{adminPath}/downloadOs")
    public ResponseResult<Object> downloadOs(@PathVariable("adminPath") String adminPath,
                                             @RequestBody JSONObject params) throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
        Integer osId = params.getInteger("osId");
        Integer nodeId = params.getInteger("nodeId");
        Os os = osService.getById(osId);
        if (os == null){
            return ResponseResult.fail("os不存在");
        }
        Master node = masterService.getById(nodeId);
        if (node == null){
            return ResponseResult.fail("节点不存在");
        }
        boolean result = osService.downloadOs(osId,nodeId);
        if (result){
            return ResponseResult.ok("开始下载");
        }
        return ResponseResult.fail("下载失败");

    }

}
