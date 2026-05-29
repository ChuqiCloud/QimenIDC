package com.chuqiyun.proxmoxveams.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import com.chuqiyun.proxmoxveams.entity.VmResourceRank;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.VmInfoService;
import com.chuqiyun.proxmoxveams.service.VmResourceRankService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @author mryunqi
 * @date 2023/8/23
 */
@RestController
@RequestMapping("/{adminPath}")
public class SysVmListController {
    @Resource
    private VmInfoService vmInfoService;
    @Resource
    private VmhostService vmhostService;
    @Resource
    private VmResourceRankService vmResourceRankService;

    /**
    * @Author: mryunqi
    * @Description: 分页查询虚拟机列表
    * @DateTime: 2023/8/23 20:01
    */
    @AdminApiCheck
    @GetMapping(value = "/getVmByPage")
    public ResponseResult<Object> getVmByPage(@RequestParam(name = "page",defaultValue = "1") Integer page,
                                              @RequestParam(name = "size",defaultValue = "20") Integer size)
            throws UnauthorizedException{
        HashMap<String, Object> vmByPage = vmInfoService.getVmByPage(page, size);
        return ResponseResult.ok(vmByPage);
    }

    /**
     * @Author: 星禾
     * @Description: 分页查询虚拟机列表
     * @DateTime: 2026/5/23 23:01
     */
    @AdminApiCheck
    @GetMapping(value = "/getDeleteVmByPage")
    public ResponseResult<Object> getDeleteVmByPage(@RequestParam(name = "page",defaultValue = "1") Integer page,
                                              @RequestParam(name = "size",defaultValue = "20") Integer size)
            throws UnauthorizedException{
        HashMap<String, Object> vmByPage = vmInfoService.getDeleteVmByPage(page, size);
        return ResponseResult.ok(vmByPage);
    }

    /**
    * @Author: mryunqi
    * @Description: 指定参数查找虚拟机
    * @DateTime: 2023/8/24 15:53
    */
    @AdminApiCheck
    @GetMapping(value = "/getVmByParam")
    public ResponseResult<Object> getVmByParam(@RequestParam(name = "page",defaultValue = "1") Integer page,
                                               @RequestParam(name = "size",defaultValue = "20") Integer size,
                                               @RequestParam(name = "param") String param,
                                               @RequestParam(name = "value") String value)
            throws UnauthorizedException{
        Object vmByParam = vmInfoService.getVmHostPageByParam(page, size, param, value);
        return ResponseResult.ok(vmByParam);
    }

    /**
    * @Author: mryunqi
    * @Description: 获取虚拟机分页列表,根据创建时间降序排列
    * @DateTime: 2023/11/26 20:15
    */
    @AdminApiCheck
    @GetMapping(value = "/getVmByPageOrderByCreateTime")
    public ResponseResult<Object> getVmByPageOrderByCreateTime(@RequestParam(name = "page",defaultValue = "1") Integer page,
                                                               @RequestParam(name = "size",defaultValue = "20") Integer size)
            throws UnauthorizedException{
        HashMap<String, Object> vmByPage = vmInfoService.getVmByPageOrderByCreateTime(page, size);
        return ResponseResult.ok(vmByPage);
    }

    /**
     * @Author: 星穹
     * @Description: 获取近七日开通和删除统计数据
     * @DateTime: 2026/5/29 16:10
     */
    @AdminApiCheck
    @GetMapping(value = "/getStatisticsData")
    public ResponseResult<Object> getStatisticsData() throws UnauthorizedException {
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zoneId);
        List<HashMap<String, Object>> createData = new ArrayList<>();
        List<HashMap<String, Object>> deleteData = new ArrayList<>();

        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            long startTime = date.atStartOfDay(zoneId).toInstant().toEpochMilli();
            long endTime = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1;

            HashMap<String, Object> createItem = new HashMap<>();
            createItem.put("date", date.toString());
            createItem.put("count", vmhostService.count(new QueryWrapper<Vmhost>()
                    .between("create_time", startTime, endTime)));
            createData.add(createItem);

            HashMap<String, Object> deleteItem = new HashMap<>();
            deleteItem.put("date", date.toString());
            deleteItem.put("count", vmhostService.count(new QueryWrapper<Vmhost>()
                    .in("delete_state", 1, 2)
                    .between("expiration_time", startTime, endTime)));
            deleteData.add(deleteItem);
        }

        HashMap<String, Object> result = new HashMap<>();
        result.put("createData", createData);
        result.put("deleteData", deleteData);
        result.put("cpuRank", buildVmResourceRankData("cpu"));
        result.put("memoryRank", buildVmResourceRankData("memory"));
        return ResponseResult.ok(result);
    }

    private List<HashMap<String, Object>> buildVmResourceRankData(String rankType) {
        List<VmResourceRank> rankList = vmResourceRankService.getRank(rankType);
        List<HashMap<String, Object>> result = new ArrayList<>();
        for (VmResourceRank rank : rankList) {
            HashMap<String, Object> item = new HashMap<>();
            item.put("id", rank.getHostId());
            item.put("rankNo", rank.getRankNo());
            item.put("vmid", rank.getVmid());
            item.put("hostname", rank.getHostname());
            item.put("nodeid", rank.getNodeId());
            item.put("nodeName", rank.getNodeName());
            item.put("cpu", rank.getCpu());
            item.put("cpuPercent", rank.getCpuPercent());
            item.put("memory", rank.getMemory());
            item.put("memoryMb", rank.getMemoryMb());
            item.put("maxMemory", rank.getMaxMemory());
            item.put("maxMemoryMb", rank.getMaxMemoryMb());
            item.put("memoryPercent", rank.getMemoryPercent());
            item.put("collectTime", rank.getCollectTime());
            result.add(item);
        }
        return result;
    }

}
