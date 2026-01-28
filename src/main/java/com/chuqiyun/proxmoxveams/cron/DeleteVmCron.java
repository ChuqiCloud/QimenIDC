package com.chuqiyun.proxmoxveams.cron;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.alibaba.fastjson2.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.entity.Ippool;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.IppoolService;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.TaskService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;


import java.util.HashMap;
import java.util.List;

import static com.chuqiyun.proxmoxveams.constant.TaskType.DELETE_VM;

/**
 * @author mryunqi
 * @date 2023/9/2
 */
@Component
@EnableScheduling
public class DeleteVmCron {
    @Resource
    private MasterService masterService;
    @Resource
    private VmhostService vmhostService;
    @Resource
    private TaskService taskService;
    @Resource
    private IppoolService ippoolService;

    /**
    * @Author: mryunqi
    * @Description: 删除虚拟机
    * @DateTime: 2023/9/2 16:07
    */
    @Async
    @Scheduled(fixedDelay = 2000)
    public void deleteVm() {
        QueryWrapper<Task> taskQueryWrapper = new QueryWrapper<>();
        taskQueryWrapper.eq("type", DELETE_VM);
        taskQueryWrapper.eq("status", 0);
        Page<Task> taskPage = taskService.getTaskList(1, 1, taskQueryWrapper);
        // 判断是否没有任务
        if (taskPage.getRecords().size() == 0) {
            return;
        }
        Task task = taskPage.getRecords().get(0);
        try {
            // 获取虚拟机配置信息
            Vmhost vmhost = vmhostService.getById(task.getHostid());
            // 获取node信息
            Master node = masterService.getById(task.getNodeid());
            HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
            ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
            JSONObject vmInfo;
            //删除Nat转发
            Object vmNat = vmhostService.getVmhostNatByVmid(1, 99999, task.getHostid());
            if (vmNat != null) {
                ResponseResult responseResult = (ResponseResult) vmNat;
                Integer code = responseResult.getCode();
                String message = responseResult.getMessage();
                if (20000 == code) {
                    Object data = responseResult.getData();
                    if (data instanceof JSONArray) {
                        JSONArray dataList = (JSONArray) data;
                        for (int i = 0; i < dataList.size(); i++) {
                            try {
                                JSONObject item = dataList.getJSONObject(i);
                                Integer destinationPort = item.getInteger("destination_port");
                                Integer sourcePort = item.getInteger("source_port");
                                String destinationIp = item.getString("destination_ip");
                                String protocol = item.getString("protocol");
                                Integer vm = item.getInteger("vm");
                                vmhostService.delVmhostNat(node.getHost(), sourcePort, destinationIp, destinationPort, protocol, vm);
                                System.out.println("Deleted NAT forwarding for destination port: " + destinationPort);
                            } catch (Exception e) {
                                System.err.println("Error processing item at index " + i + ": " + e.getMessage());
                            }
                        }
                    } else {
                        System.err.println("Data is not a JSONArray: " + data.getClass().getName());
                    }
                } else {
                    System.err.println("获取VM NAT信息失败: " + message);
                }
            }
            // 获取虚拟机实时信息
            try {
                vmInfo = proxmoxApiUtil.getVmStatus(node, authentications, vmhost.getVmid());
            } catch (Exception e) {
                vmInfo = null;
            }
            // 如果vmInfo不为空，才进行下一步操作
            if (vmInfo != null) {
                // 判断虚拟机状态是否为1关机或2停止或4禁用
                if (vmhost.getStatus() != 1 && vmhost.getStatus() != 2 && vmhost.getStatus() != 4) {
                    return;
                }

            }
            else {
                // 直接删除数据库中的虚拟机信息
                vmhostService.removeById(task.getHostid());
                // 修改任务状态为2
                task.setStatus(2);
                taskService.updateById(task);
                return;
            }
            // 修改任务状态为1
            task.setStatus(1);
            taskService.updateById(task);

            proxmoxApiUtil.deleteVm(node, authentications, task.getVmid());

            List<String> ipList = vmhost.getIpList();
            for (String ip : ipList) {
                Ippool ippool = ippoolService.getIppoolByIp(ip);
                if (ippool != null) {
                    ippool.setStatus(0);
                    ippool.setVmId(0);
                    ippoolService.updateById(ippool);
                }
            }
            // 删除数据库中的虚拟机信息
            vmhostService.removeById(task.getHostid());
            // 修改任务状态为2
            task.setStatus(2);
            taskService.updateById(task);
        } catch (Exception e) {
            e.printStackTrace(); // 输出完整堆栈信息
            System.out.println("DeleteVm 异常"+ e);
            task.setStatus(2);
            taskService.updateById(task);
        }
    }
}
