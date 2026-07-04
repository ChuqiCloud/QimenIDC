package com.chuqiyun.proxmoxveams.controller.admin;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.entity.VmInitScript;
import com.chuqiyun.proxmoxveams.service.VmInitScriptBusinessService;
import com.chuqiyun.proxmoxveams.service.VmInitScriptService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author: 鏄熺
 * @Description: 后台虚拟机初始化脚本接口
 * @DateTime: 2026/7/3 20:47
 */
@RestController
@RequestMapping("/{adminPath}")
public class SysVmInitScriptController {
    @Resource
    private VmInitScriptService vmInitScriptService;
    @Resource
    private VmInitScriptBusinessService vmInitScriptBusinessService;

    @AdminApiCheck
    @PostMapping("/vmInitScript/add")
    public ResponseResult<Object> add(@RequestBody VmInitScript script) {
        return buildResult(vmInitScriptBusinessService.addScript(script));
    }

    @AdminApiCheck
    @RequestMapping(value = "/vmInitScript/update", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseResult<Object> update(@RequestBody VmInitScript script) {
        return buildResult(vmInitScriptBusinessService.updateScript(script));
    }

    @AdminApiCheck
    @RequestMapping(value = "/vmInitScript/delete/{scriptId}", method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    public ResponseResult<Object> delete(@PathVariable Integer scriptId) {
        return buildResult(vmInitScriptBusinessService.deleteScript(scriptId));
    }

    @AdminApiCheck
    @GetMapping("/vmInitScript/get/{scriptId}")
    public ResponseResult<Object> get(@PathVariable Integer scriptId) {
        VmInitScript script = vmInitScriptService.getById(scriptId);
        if (script == null || Integer.valueOf(0).equals(script.getStatus())) {
            return ResponseResult.fail(UnifiedResultCode.ERROR_INVALID_PARAM.getCode(), "初始化脚本不存在或已删除");
        }
        return ResponseResult.ok(script);
    }

    @AdminApiCheck
    @GetMapping("/vmInitScript/list")
    public ResponseResult<Object> list(@RequestParam(defaultValue = "1") Integer page,
                                       @RequestParam(defaultValue = "20") Integer size,
                                       @RequestParam(required = false) String name) {
        QueryWrapper<VmInitScript> queryWrapper = new QueryWrapper<>();
        queryWrapper.ne("status", 0);
        if (name != null && !name.trim().isEmpty()) {
            queryWrapper.like("name", name.trim());
        }
        queryWrapper.orderByDesc("id");
        return ResponseResult.ok(vmInitScriptService.page(new Page<>(page, size), queryWrapper));
    }

    @AdminApiCheck
    @PostMapping("/vmInitScript/run")
    public ResponseResult<Object> run(@RequestBody JSONObject params) {
        Integer hostId = params.getInteger("hostId");
        return buildResult(vmInitScriptBusinessService.createManualRunTasks(hostId, getInitScriptIds(params)));
    }

    @AdminApiCheck
    @GetMapping("/vmInitScript/records")
    public ResponseResult<Object> records(@RequestParam(required = false) Integer hostId,
                                          @RequestParam(required = false) Integer scriptId) {
        return ResponseResult.ok(vmInitScriptBusinessService.getRecords(hostId, scriptId));
    }

    private ResponseResult<Object> buildResult(UnifiedResultDto<Object> resultDto) {
        if (resultDto.getResultCode().getCode() != UnifiedResultCode.SUCCESS.getCode()) {
            return ResponseResult.fail(resultDto.getResultCode().getCode(), resultDto.getMessage());
        }
        return ResponseResult.ok(resultDto.getData() == null ? resultDto.getResultCode().getMessage() : resultDto.getData());
    }

    private List<Integer> getInitScriptIds(JSONObject params) {
        List<Integer> initScriptIds = new ArrayList<>();
        if (params == null) {
            return initScriptIds;
        }
        if (params.getJSONArray("initScriptIds") != null) {
            initScriptIds.addAll(params.getJSONArray("initScriptIds").toJavaList(Integer.class));
        }
        Integer initScriptId = params.getInteger("initScriptId");
        if (initScriptId != null && !initScriptIds.contains(initScriptId)) {
            initScriptIds.add(initScriptId);
        }
        return initScriptIds;
    }
}
