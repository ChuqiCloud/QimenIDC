package com.chuqiyun.proxmoxveams.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.common.UnifiedLogger;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.dao.VmhostDao;
import com.chuqiyun.proxmoxveams.dto.RenewalParams;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Os;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.dto.VmParams;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.OsService;
import com.chuqiyun.proxmoxveams.service.TaskService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import com.chuqiyun.proxmoxveams.utils.TimeUtil;
import com.chuqiyun.proxmoxveams.utils.VmUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.chuqiyun.proxmoxveams.constant.TaskType.*;

/**
 * (Vmhost)表服务实现类
 *
 * @author mryunqi
 * @since 2023-06-21 15:11:18
 */
@Slf4j
@Service("vmhostService")
public class VmhostServiceImpl extends ServiceImpl<VmhostDao, Vmhost> implements VmhostService {
    @Resource
    private MasterService masterService;
    @Resource
    private TaskService taskService;
    @Resource
    private OsService osService;
    /**
    * @Author: mryunqi
    * @Description: 根据虚拟机id获取虚拟机实例信息
    * @DateTime: 2023/6/22 1:37
    */
    @Override
    public Vmhost getVmhostByVmId(int vmId) {
        return this.getOne(new QueryWrapper<Vmhost>().eq("vmid",vmId));
    }

    /**
    * @Author: mryunqi
    * @Description: 分页查询虚拟机实例信息
    * @DateTime: 2023/7/19 17:51
    */
    @Override
    public Page<Vmhost> selectPage(Integer page, Integer limit) {
        Page<Vmhost> vmhostPage = new Page<>(page, limit);
        return this.page(vmhostPage);
    }
    /**
    * @Author: mryunqi
    * @Description: 附加条件分页查询虚拟机实例信息
    * @DateTime: 2023/7/19 17:53
    * @Params: Integer page 页码，Integer limit 每页数量，QueryWrapper<Vmhost> queryWrapper 附加条件
    * @Return Page<Vmhost> 分页数据
    */
    @Override
    public Page<Vmhost> selectPage(Integer page, Integer limit, QueryWrapper<Vmhost> queryWrapper){
        Page<Vmhost> vmhostPage = new Page<>(page, limit);
        return this.page(vmhostPage,queryWrapper);
    }

    /**
    * @Author: mryunqi
    * @Description: 模糊查询指定IP地址的虚拟机实例信息
    * @DateTime: 2023/8/24 16:05
    * @Params: Integer page 页码，Integer limit 每页数量，String ip IP地址
    * @Return Page<Vmhost> 分页数据
    */
    @Override
    public Page<Vmhost> selectPageByIp(Integer page, Integer limit, String ip) {
        Page<Vmhost> vmhostPage = new Page<>(page, limit);
        return this.page(vmhostPage,new QueryWrapper<Vmhost>().like("ip_config",ip));
    }

    /**
    * @Author: mryunqi
    * @Description: 根据主机名查询虚拟机实例信息
    * @DateTime: 2023/8/24 16:17
    * @Params: String name 主机名
    * @Return Vmhost 虚拟机实例信息
    */
    @Override
    public Vmhost getVmhostByName(String name) {
        return this.getOne(new QueryWrapper<Vmhost>().eq("name",name));
    }

    /**
    * @Author: mryunqi
    * @Description: 根据节点id分页查询虚拟机实例信息
    * @DateTime: 2023/8/24 16:24
    * @Params: Integer page 页码，Integer limit 每页数量，String nodeId 节点id
    * @Return Page<Vmhost> 分页数据
    */
    @Override
    public Page<Vmhost> selectPageByNodeId(Integer page, Integer limit, String nodeId) {
        Page<Vmhost> vmhostPage = new Page<>(page, limit);
        return this.page(vmhostPage,new QueryWrapper<Vmhost>().eq("nodeid",nodeId));
    }

    /**
    * @Author: mryunqi
    * @Description: 根据状态分页查询
    * @DateTime: 2023/8/24 17:41
    * @Params: Integer page 页码，Integer limit 每页数量，Integer status 状态
    * @Return Page<Vmhost> 分页数据
    */
    @Override
    public Page<Vmhost> selectPageByStatus(Integer page,Integer size,Integer status){
        Page<Vmhost> vmhostPage = new Page<>(page, size);
        return this.page(vmhostPage,new QueryWrapper<Vmhost>().eq("status",status));
    }

    /**
    * @Author: mryunqi
    * @Description: 添加虚拟机实例信息
    * @DateTime: 2023/6/21 23:54
    */
    @Override
    public Integer addVmhost(int vmId,VmParams vmParams) {
        Vmhost vmhost = new Vmhost();
        vmhost.setNodeid(vmParams.getNodeid());
        vmhost.setVmid(vmId);
        vmhost.setName(vmParams.getHostname());
        vmhost.setConfigureTemplateId(vmParams.getConfigureTemplateId());
        vmhost.setSockets(vmParams.getSockets());
        vmhost.setCores(vmParams.getCores());
        vmhost.setThreads(vmParams.getThreads());
        vmhost.setDevirtualization(vmParams.getDevirtualization());
        vmhost.setKvm(vmParams.getKvm());
        vmhost.setCpuModel(vmParams.getCpuModel());
        vmhost.setModelGroup(vmParams.getModelGroup());
        vmhost.setArgs(vmParams.getArgs());
        vmhost.setCpu(vmParams.getCpu());
        vmhost.setCpuUnits(vmParams.getCpuUnits());
        vmhost.setBwlimit(vmParams.getBwlimit());
        vmhost.setUsername(vmParams.getUsername());
        vmhost.setPassword(vmParams.getPassword());
        vmhost.setArch(vmParams.getArch());
        vmhost.setAcpi(vmParams.getAcpi());
        vmhost.setMemory(vmParams.getMemory());
        vmhost.setSwap(vmParams.getSwap());
        vmhost.setStorage(vmParams.getStorage());
        vmhost.setSystemDiskSize(vmParams.getSystemDiskSize());
        vmhost.setDataDisk(vmParams.getDataDisk());
        vmhost.setBridge(vmParams.getBridge());
        vmhost.setOs(vmParams.getOs());
        vmhost.setOsName(vmParams.getOsName());
        vmhost.setOsType(vmParams.getOsType());
        vmhost.setIso(vmParams.getIso());
        vmhost.setTemplate(vmParams.getTemplate());
        vmhost.setOnBoot(vmParams.getOnBoot());
        vmhost.setBandwidth(vmParams.getBandwidth());
        vmhost.setIpConfig(vmParams.getIpConfig());
        vmhost.setIpList(vmParams.getIpList());
        if (vmParams.getNested() == null || !vmParams.getNested()) {
            vmhost.setNested(0);
        }
        else {
            vmhost.setNested(1);
        }
        vmhost.setTask(vmParams.getTask());
        vmhost.setCreateTime(System.currentTimeMillis());
        // 设置状态为6，表示创建中
        vmhost.setStatus(6);
        // 判断到期时间是否为空
        if (vmParams.getExpirationTime() == null) {
            // 时间设定为99年后到期
            vmhost.setExpirationTime(System.currentTimeMillis()+315360000000L);
        }
        // 判断到期时间是否为10位
        else if (vmParams.getExpirationTime().toString().length() == 10) {
            // 将时间转换为13位
            vmhost.setExpirationTime(TimeUtil.tenToThirteen(vmParams.getExpirationTime()));
        }
        else{
            vmhost.setExpirationTime(vmParams.getExpirationTime());
        }
        // 返回id
        return this.save(vmhost) ? vmhost.getId() : null;
    }

    /**
    * @Author: mryunqi
    * @Description: 虚拟机电源管理
    * @DateTime: 2023/7/18 16:57
    * @Params: Integer hostId 虚拟机ID, String action 操作类型
    * @Return HashMap<String,Object> 返回操作结果
    */
    @Override
    public HashMap<String,Object> power(Integer hostId, String action) {
        HashMap<String,Object> result = new HashMap<>();
        // 获取虚拟机实例信息
        Vmhost vmhost = this.getById(hostId);
        // 获取虚拟机id
        int vmId = vmhost.getVmid();
        // 获取节点id
        int nodeId = vmhost.getNodeid();
        // 获取虚拟机状态
        int vmStatus = vmhost.getStatus();
        long time = System.currentTimeMillis();
        // vmStatus状态有0=运行中、1=已关机、2=挂起、3=恢复中、4=暂停、5=到期、6=创建中、7=开机中、8=关机中、9=停止中（强制关机中）、10=挂起中、11=暂停中、12重启中、13=重装系统中、14=修改密码中
        // action类型有start=开机、stop=关机、reboot=重启、shutdown=强制关机、suspend=挂起、resume=恢复、pause=暂停、unpause=恢复
        switch (action) {
            case "start": {
                // 判断虚拟机是否被暂停
                if (vmStatus == 4){
                    result.put("status", false);
                    result.put("msg", "虚拟机已暂停，无法关机");
                    return result;
                }
                // 判断虚拟机是否到期
                if (vmStatus == 5){
                    result.put("status", false);
                    result.put("msg", "虚拟机已到期，无法开机");
                    return result;
                }
                // 判断虚拟机状态是否为已停止
                if (vmStatus == 0 || vmStatus == 3) {
                    result.put("status", true);
                    // 直接返回true
                }
                else {
                    // 设置虚拟机状态为7，表示开机中
                    vmhost.setStatus(7);
                    // 更新虚拟机状态
                    this.updateById(vmhost);
                    // 创建开机任务
                    Task vmStartTask = new Task();
                    vmStartTask.setNodeid(nodeId);
                    vmStartTask.setVmid(vmId);
                    vmStartTask.setHostid(hostId);
                    vmStartTask.setType(START_VM);
                    vmStartTask.setStatus(0);
                    vmStartTask.setCreateDate(time);
                    // 保存任务
                    if (taskService.save(vmStartTask)) {
                        log.info("[Task-StartVm] 开机任务创建成功: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                        result.put("status", true);
                        // 添加任务流程
                        this.addVmHostTask(hostId, vmStartTask.getId());
                    }
                    else {
                        log.info("[Task-StartVm] 开机任务创建失败: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                        result.put("status", false);
                        result.put("msg", "开机任务创建失败");
                    }
                }
                return result;
            }
            case "stop": {
                // 判断虚拟机是否被暂停
                if (vmStatus == 4){
                    result.put("status", false);
                    result.put("msg", "虚拟机已暂停，无法关机");
                    return result;
                }
                // 判断虚拟机是否到期
                if (vmStatus == 5){
                    result.put("status", false);
                    result.put("msg", "虚拟机已到期，无法关机");
                    return result;
                }
                if (vmStatus == 1 || vmStatus == 2) {
                    result.put("status", true);
                    // 直接返回true
                }
                else {
                    // 设置虚拟机状态为8，表示关机中
                    vmhost.setStatus(8);
                    // 更新虚拟机状态
                    this.updateById(vmhost);
                    Task vmStopTask = new Task();
                    vmStopTask.setNodeid(nodeId);
                    vmStopTask.setVmid(vmId);
                    vmStopTask.setHostid(hostId);
                    vmStopTask.setType(STOP_VM);
                    vmStopTask.setStatus(0);
                    vmStopTask.setCreateDate(time);
                    if (taskService.save(vmStopTask)) {
                        log.info("[Task-StopVm] 关机任务创建成功: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                        result.put("status", true);
                        // 添加任务流程
                        this.addVmHostTask(hostId, vmStopTask.getId());
                    }
                    else {
                        log.info("[Task-StopVm] 关机任务创建失败: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                        result.put("status", false);
                        result.put("msg", "关机任务创建失败");
                    }
                }
                return result;
            }
            case "reboot": {
                // 判断虚拟机状态是否为暂停
                if (vmStatus == 4) {
                    result.put("status", false);
                    result.put("msg", "虚拟机已暂停，无法重启");
                    return result;
                }
                // 判断虚拟机是否到期
                if (vmStatus == 5){
                    result.put("status", false);
                    result.put("msg", "虚拟机已到期，无法重启");
                    return result;
                }
                else {
                    // 设置虚拟机状态为12，表示重启中
                    vmhost.setStatus(12);
                    // 更新虚拟机状态
                    this.updateById(vmhost);
                    // 判断虚拟机状态是否为已停止，如果是则直接开机
                    if (vmStatus == 1 || vmStatus == 2) {
                        Task vmStartTask = new Task();
                        vmStartTask.setNodeid(nodeId);
                        vmStartTask.setVmid(vmId);
                        vmStartTask.setHostid(hostId);
                        vmStartTask.setType(START_VM);
                        vmStartTask.setStatus(0);
                        vmStartTask.setCreateDate(time);
                        if (taskService.save(vmStartTask)) {
                            log.info("[Task-StartVm] 开机任务创建成功: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                            result.put("status", true);
                            // 添加任务流程
                            this.addVmHostTask(hostId, vmStartTask.getId());
                        }
                        else {
                            log.info("[Task-StartVm] 开机任务创建失败: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                            result.put("status", false);
                            result.put("msg", "开机任务创建失败");
                        }
                    }
                    else {
                        // 创建重启任务
                        Task vmRebootTask = new Task();
                        vmRebootTask.setNodeid(nodeId);
                        vmRebootTask.setVmid(vmId);
                        vmRebootTask.setHostid(hostId);
                        vmRebootTask.setType(REBOOT_VM);
                        vmRebootTask.setStatus(0);
                        vmRebootTask.setCreateDate(time);
                        if (taskService.save(vmRebootTask)) {
                            log.info("[Task-RebootVm] 重启任务创建成功: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                            result.put("status", true);
                            // 添加任务流程
                            this.addVmHostTask(hostId, vmRebootTask.getId());
                        } else {
                            log.info("[Task-RebootVm] 重启任务创建失败: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                            result.put("status", false);
                            result.put("msg", "重启任务创建失败");
                        }
                    }
                }
                return result;
            }
            case "shutdown":{
                // 判断虚拟机状态是否为暂停
                if (vmStatus == 4) {
                    // 调用节点接口关机
                    result.put("status", false);
                    result.put("msg", "虚拟机已暂停，无法关机");
                    return result;
                }
                // 判断虚拟机是否到期
                if (vmStatus == 5){
                    result.put("status", false);
                    result.put("msg", "虚拟机已到期，无法关机");
                    return result;
                }
                else {
                    // 设置虚拟机状态为9，表示强制关机中
                    vmhost.setStatus(9);
                    // 更新虚拟机状态
                    this.updateById(vmhost);
                    Task vmShutdownTask = new Task();
                    vmShutdownTask.setNodeid(nodeId);
                    vmShutdownTask.setVmid(vmId);
                    vmShutdownTask.setHostid(hostId);
                    vmShutdownTask.setType(STOP_VM_FORCE);
                    vmShutdownTask.setStatus(0);
                    vmShutdownTask.setCreateDate(time);
                    if (taskService.save(vmShutdownTask)) {
                        log.info("[Task-ShutdownVm] 强制关机任务创建成功: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                        result.put("status", true);
                        // 添加任务流程
                        this.addVmHostTask(hostId, vmShutdownTask.getId());
                    }
                    else {
                        log.info("[Task-ShutdownVm] 强制关机任务创建失败: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                        result.put("status", false);
                        result.put("msg", "强制关机任务创建失败");
                    }
                }
                return result;
            }
            case "suspend": {
                // 判断虚拟机状态是否为暂停
                if (vmStatus == 4) {
                    result.put("status", false);
                    result.put("msg", "虚拟机已暂停，无法挂起");
                    return result;
                }
                // 判断虚拟机是否到期
                if (vmStatus == 5){
                    result.put("status", false);
                    result.put("msg", "虚拟机已到期，无法挂起");
                    return result;
                }
                else {
                    // 设置虚拟机状态为10，表示挂起中
                    vmhost.setStatus(10);
                    // 更新虚拟机状态
                    this.updateById(vmhost);
                    Task vmSuspendTask = new Task();
                    vmSuspendTask.setNodeid(nodeId);
                    vmSuspendTask.setVmid(vmId);
                    vmSuspendTask.setHostid(hostId);
                    vmSuspendTask.setType(SUSPEND_VM);
                    vmSuspendTask.setStatus(0);
                    vmSuspendTask.setCreateDate(time);
                    if (taskService.save(vmSuspendTask)) {
                        log.info("[Task-SuspendVm] 挂起任务创建成功: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                        result.put("status", true);

                        // 添加任务流程
                        this.addVmHostTask(hostId, vmSuspendTask.getId());
                    }
                    else {
                        log.info("[Task-SuspendVm] 挂起任务创建失败: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                        result.put("status", false);
                        result.put("msg", "挂起任务创建失败");
                    }
                }
                return result;
            }
            case "resume": {
                // 判断虚拟机状态是否为暂停
                if (vmStatus == 4) {
                    result.put("status", false);
                    result.put("msg", "虚拟机已暂停，无法恢复");
                    return result;
                }
                // 判断虚拟机是否到期
                if (vmStatus == 5){
                    result.put("status", false);
                    result.put("msg", "虚拟机已到期，无法恢复");
                    return result;
                }
                else {
                    // 设置虚拟机状态为3，表示恢复中
                    vmhost.setStatus(3);
                    // 更新虚拟机状态
                    this.updateById(vmhost);
                    Task vmResumeTask = new Task();
                    vmResumeTask.setNodeid(nodeId);
                    vmResumeTask.setVmid(vmId);
                    vmResumeTask.setHostid(hostId);
                    vmResumeTask.setType(RESUME_VM);
                    vmResumeTask.setStatus(0);
                    vmResumeTask.setCreateDate(time);
                    if (taskService.save(vmResumeTask)) {
                        log.info("[Task-ResumeVm] 恢复任务创建成功: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                        result.put("status", true);

                        // 添加任务流程
                        this.addVmHostTask(hostId, vmResumeTask.getId());
                    }
                    else {
                        log.info("[Task-ResumeVm] 恢复任务创建失败: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                        result.put("status", false);
                        result.put("msg", "恢复任务创建失败");
                    }
                }
                return result;
            }
            case "pause":{
                // 设置虚拟机状态为11，表示暂停中
                vmhost.setStatus(11);
                // 更新虚拟机状态
                this.updateById(vmhost);
                Task vmPauseTask = new Task();
                vmPauseTask.setNodeid(nodeId);
                vmPauseTask.setVmid(vmId);
                vmPauseTask.setHostid(hostId);
                vmPauseTask.setType(PAUSE_VM);
                vmPauseTask.setStatus(0);
                vmPauseTask.setCreateDate(time);
                if (taskService.save(vmPauseTask)) {
                    log.info("[Task-PauseVm] 暂停任务创建成功: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                    result.put("status", true);

                    // 添加任务流程
                    this.addVmHostTask(hostId, vmPauseTask.getId());
                }
                else {
                    log.info("[Task-PauseVm] 暂停任务创建失败: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                    result.put("status", false);
                    result.put("msg", "暂停任务创建失败");
                }
                return result;
            }
            case "unpause":{
                // 判断虚拟机状态是否为暂停
                if (vmStatus != 4) {
                    result.put("status", false);
                    result.put("msg", "虚拟机未暂停");
                    return result;
                }
                else {
                    // 设置虚拟机状态为3，表示恢复中
                    vmhost.setStatus(3);
                    // 更新虚拟机状态
                    this.updateById(vmhost);
                    Task vmUnpauseTask = new Task();
                    vmUnpauseTask.setNodeid(nodeId);
                    vmUnpauseTask.setVmid(vmId);
                    vmUnpauseTask.setHostid(hostId);
                    vmUnpauseTask.setType(UNPAUSE_VM);
                    vmUnpauseTask.setStatus(0);
                    vmUnpauseTask.setCreateDate(time);
                    if (taskService.save(vmUnpauseTask)) {
                        log.info("[Task-UnpauseVm] 恢复任务创建成功: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                        result.put("status", true);

                        // 添加任务流程
                        this.addVmHostTask(hostId, vmUnpauseTask.getId());
                    }
                    else {
                        log.info("[Task-UnpauseVm] 恢复任务创建失败: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                        result.put("status", false);
                        result.put("msg", "恢复任务创建失败");
                    }
                }
                return result;
            }

            default: {
                result.put("status", false);
                result.put("msg", "未知操作");
                return result;
            }
        }
    }

    /**
     * @Author: mryunqi
     * @Description: 生成最新vmid
     * @DateTime: 2023/6/21 15:21
     * @Params: Integer id 节点id
     * @Return Integer
     */
    @Override
    public Integer getNewVmid(Integer id) {
        // 获取master
        Master master = masterService.getById(id);
        // 获取cookie
        HashMap<String, String> cookieMap = masterService.getMasterCookieMap(id);
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        // 查询vm列表 {"data":[{'vmid':100,'name':'test'},{'vmid':101,'name':'test2'}]}
        JSONObject vmJson = proxmoxApiUtil.getNodeApi(master,cookieMap,"/nodes/"+master.getNodeName()+"/qemu",new HashMap<>());
        JSONObject lxcJson = proxmoxApiUtil.getNodeApi(master,cookieMap,"/nodes/"+master.getNodeName()+"/lxc",new HashMap<>());
        JSONArray VmJsonArray = vmJson.getJSONArray("data");
        JSONArray LxcJsonArray = lxcJson.getJSONArray("data");
        // 提取出所有lxc的vmid
        ArrayList<Integer> lxcVmidList = new ArrayList<>();
        for (int i = 0; i < LxcJsonArray.size(); i++) {
            JSONObject tempJsonObject = LxcJsonArray.getJSONObject(i);
            int vmid = tempJsonObject.getIntValue("vmid");
            lxcVmidList.add(vmid);
        }

        ArrayList<Integer> vmidList = new ArrayList<>();

        for (int i = 0; i < VmJsonArray.size(); i++) {
            JSONObject tempJsonObject = VmJsonArray.getJSONObject(i);
            int vmid = tempJsonObject.getIntValue("vmid");
            vmidList.add(vmid);
        }

        // 合并两个list
        vmidList.addAll(lxcVmidList);
        // 去重
        vmidList = (ArrayList<Integer>) vmidList.stream().distinct().collect(Collectors.toList());
        // 排序
        vmidList.sort(Comparator.naturalOrder());
        int maxVmid;
        // 如果vmidList为空，maxVmid为100
        if (vmidList.size() == 0) {
            maxVmid = 100;
        }else{
            maxVmid = vmidList.get(vmidList.size() - 1);
        }
        // 获取数据库中是否存在该vmid
        QueryWrapper<Vmhost> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("vmid",maxVmid);
        queryWrapper.eq("nodeid",id);
        Vmhost vmhost;
        vmhost = this.getOne(queryWrapper);
        if (vmhost == null) {
            return maxVmid;
        }
        // 循环+1，直到找到一个不存在的vmid
        while (vmhost != null) {
            maxVmid++;
            queryWrapper.clear();
            queryWrapper.eq("nodeid",id);
            queryWrapper.eq("vmid",maxVmid);
            vmhost = this.getOne(queryWrapper);
        }
        return maxVmid;
    }

    /**
    * @Author: mryunqi
    * @Description: 同步虚拟机状态
    * @DateTime: 2023/7/19 20:41
    * @Params: JSONArray vmHosts pve中虚拟机信息 ，Integer nodeId 节点id
    */
    @Override
    public void syncVmStatus(JSONArray vmHosts, Integer nodeId) {
        for (int i = 0; i < vmHosts.size(); i++){
            JSONObject vmHostJson = vmHosts.getJSONObject(i);
            // 判空
            if (vmHostJson == null){
                continue;
            }
            int vmId = vmHostJson.getInteger("vmid");
            // 获取数据库中虚拟机信息
            QueryWrapper<Vmhost> vmhostQueryWrapper = new QueryWrapper<>();
            vmhostQueryWrapper.eq("nodeid",nodeId);
            vmhostQueryWrapper.eq("vmid",vmId);
            Vmhost vmhost = this.getOne(vmhostQueryWrapper);
            // 判空
            if (vmhost == null){
                continue;
            }
            // pve中虚拟机状态
            String strStatus = vmHostJson.getString("status");
            // 转换为int
            int initStatus = VmUtil.getVmStatusNumByStr(strStatus);
            // 判断是否存在lock字段
            if (vmHostJson.containsKey("lock")){
                // 如果为suspending，则将状态设置为2
                if ("suspending".equals(vmHostJson.getString("lock"))){
                    initStatus = 2;
                }
                // 如果为suspended，也为2
                if ("suspended".equals(vmHostJson.getString("lock"))){
                    initStatus = 2;
                }
            }
            // 数据库中虚拟机状态
            int vmStatus = vmhost.getStatus();
            // 如果相同则不做处理
            if (initStatus == vmStatus){
                continue;
            }
            // 先判断数据库中状态为4(暂停)，且pve中的状态为2
            if (vmStatus == 4 && initStatus == 2){
                continue;
            }
            // 判断数据库中的状态是否为5(到期)，且pve中的状态不为1(关机)
            if (vmStatus == 5 && initStatus != 1){
                // 强制关机
                this.power(vmhost.getId(),"shutdown");
                continue;
            }
            // 判断数据库中的状态是否为4(暂停)，且pve中的状态不为2(挂起)
            if (vmStatus == 4){
                // 暂停pve中的虚拟机
                this.power(vmhost.getId(),"pause");
                continue;
            }
            // 其他情况，直接更新数据库中的状态
            vmhost.setStatus(initStatus);
            this.updateById(vmhost);
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 重装虚拟机系统
    * @DateTime: 2023/9/1 15:33
    * @Params: Long vmHostId 虚拟机id，String osName 镜像名称，String newPassword 新密码，Boolean resetDataDisk 是否重置数据盘
    * @Return UnifiedResultDto<Object> 统一返回结果
    */
    @Override
    public UnifiedResultDto<Object> resetVmOs(Long vmHostId, String osName, String newPassword, Boolean resetDataDisk){
        // 获取虚拟机信息
        Vmhost vmhost = this.getById(vmHostId);
        // 判空
        if (vmhost == null){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_VM_NOT_EXIST, null);
        }

        // 判断虚拟机是否为禁用状态
        if (vmhost.getStatus() == 4){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_VM_IS_DISABLED, null);
        }
        // 判断虚拟机是否为到期
        if (vmhost.getStatus() == 5){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_VM_IS_EXPIRED, null);
        }


        Os os = osService.isExistOs(osName);
        // 判断镜像是否存在
        if (os == null) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_CLOUD_IMAGE_NOT_EXIST, null);
        }

        // 判断虚拟机是否为开机状态
        if (vmhost.getStatus() == 0){
            this.power(vmhost.getId(), "shutdown");
        }
        // 重置osName
        osName = os.getFileName();

        // 设置虚拟机状态为重装系统中
        vmhost.setStatus(13);
        this.updateById(vmhost);

        // 创建重置虚拟机系统的任务
        HashMap<Object, Object> vmParamsMap = new HashMap<>();
        vmParamsMap.put("osName",osName);
        vmParamsMap.put("newPassword",newPassword);
        vmParamsMap.put("resetDataDisk",resetDataDisk);
        Task task = new Task();
        task.setHostid(vmhost.getId());
        task.setVmid(vmhost.getVmid());
        task.setNodeid(vmhost.getNodeid());
        task.setStatus(0);
        task.setType(REINSTALL_VM);
        task.setParams(vmParamsMap);
        task.setCreateDate(System.currentTimeMillis());
        if (taskService.insertTask(task)){
            UnifiedLogger.log(UnifiedLogger.LogType.TASK_RESET_SYSTEM,"创建重置虚拟机系统任务成功，任务id为：" + task.getId());
            return new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, null);
        }
        // 添加任务流程
        this.addVmHostTask(vmhost.getId(), task.getId());
        return new UnifiedResultDto<>(UnifiedResultCode.ERROR_RESET_SYSTEM_FAILED, null);
    }

    /**
    * @Author: mryunqi
    * @Description: 删除虚拟机
    * @DateTime: 2023/9/2 16:12
    * @Params: Long vmHostId 虚拟机id
    * @Return UnifiedResultDto<Object> 统一返回结果
    */
    @Override
    public UnifiedResultDto<Object> deleteVm(Long vmHostId){
        // 获取虚拟机信息
        Vmhost vmhost = this.getById(vmHostId);
        // 如果虚拟机不存在
        if (vmhost == null){
            // 将vmHostId作为为vmid
            vmhost = this.getVmhostByVmId(Math.toIntExact(vmHostId));
        }
        // 判空
        if (vmhost == null){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_VM_NOT_EXIST, null);
        }

        // 判断虚拟机是否为禁用状态
        if (vmhost.getStatus() == 4){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_VM_IS_DISABLED, null);
        }

        // 判断虚拟机是否为开机状态
        if (vmhost.getStatus() == 0){
            this.power(vmhost.getId(), "shutdown");
        }

        // 创建删除虚拟机的任务
        Task task = new Task();
        task.setHostid(vmhost.getId());
        task.setVmid(vmhost.getVmid());
        task.setNodeid(vmhost.getNodeid());
        task.setStatus(0);
        task.setType(DELETE_VM);
        task.setCreateDate(System.currentTimeMillis());
        if (taskService.insertTask(task)){
            UnifiedLogger.log(UnifiedLogger.LogType.TASK_DELETE_VM,"创建删除虚拟机任务成功，任务id为：" + task.getId());
            return new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, null);
        }
        // 添加任务流程
        this.addVmHostTask(vmhost.getId(), task.getId());
        return new UnifiedResultDto<>(UnifiedResultCode.ERROR_DELETE_VM_FAILED, null);
    }

    /**
    * @Author: mryunqi
    * @Description: 创建重置密码任务
    * @DateTime: 2023/9/29 15:09
    * @Params: Long vmHostId 虚拟机id，String newPassword 新密码
    * @Return UnifiedResultDto<Object> 统一返回结果
    */
    @Override
    public UnifiedResultDto<Object> resetVmPassword(Long vmHostId, String newPassword){
        // 获取虚拟机信息
        Vmhost vmhost = this.getById(vmHostId);
        // 如果虚拟机不存在
        if (vmhost == null){
            // 将vmHostId作为为vmid
            vmhost = this.getVmhostByVmId(Math.toIntExact(vmHostId));
        }
        // 判空
        if (vmhost == null){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_VM_NOT_EXIST, null);
        }

        // 判断新密码是否为空
        if (StringUtils.isEmpty(newPassword)){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NEW_PASSWORD_NOT_NULL, null);
        }

        // 判断虚拟机是否为禁用状态
        if (vmhost.getStatus() == 4){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_VM_IS_DISABLED, null);
        }
        // 判断虚拟机是否为到期
        if (vmhost.getStatus() == 5){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_VM_IS_EXPIRED, null);
        }

        // 判断虚拟机是否为开机状态
        if (vmhost.getStatus() == 0){
            this.power(vmhost.getId(), "shutdown");
        }
        // 对比新旧密码是否相同
        if (newPassword.equals(vmhost.getPassword())){
            return new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, null);
        }
        // 设置虚拟机状态为重置密码中
        vmhost.setStatus(14);
        // 创建重置密码的任务
        Task task = new Task();
        task.setHostid(vmhost.getId());
        task.setVmid(vmhost.getVmid());
        task.setNodeid(vmhost.getNodeid());
        task.setStatus(0);
        task.setType(RESET_PASSWORD);
        Map<Object, Object> params = new HashMap<>();
        params.put("newPassword",newPassword);
        task.setParams(params);
        task.setCreateDate(System.currentTimeMillis());
        if (taskService.insertTask(task)){
            UnifiedLogger.log(UnifiedLogger.LogType.TASK_RESET_PASSWORD,"创建重置密码任务成功，任务id为：" + task.getId());
            return new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, null);
        }
        // 添加任务流程
        this.addVmHostTask(vmhost.getId(), task.getId());

        return new UnifiedResultDto<>(UnifiedResultCode.ERROR_RESET_PASSWORD_FAILED, null);
    }

    /**
    * @Author: mryunqi
    * @Description: 增加虚拟机任务流程
    * @DateTime: 2023/9/29 16:49
    * @Params: Long hostId 虚拟机id，Long taskId 任务id
    * @Return Boolean 增加任务是否成功
    */
    @Override
    public Boolean addVmHostTask(Object hostId, Object taskId){
        // 转换为Long类型
        long hostid  = Long.parseLong(hostId.toString());

        // 获取虚拟机信息
        Vmhost vmhost = this.getById(hostid);
        // 判空
        if (vmhost == null){
            return false;
        }
        Map<Object,Object> nowTask;
        if (vmhost.getTask() == null){
            nowTask = new HashMap<>();
        }else{
            nowTask = vmhost.getTask();
        }
        // 追加任务
        nowTask.put(System.currentTimeMillis(),taskId);
        vmhost.setTask(nowTask);
        // 更新任务
        return this.updateById(vmhost);
    }

    /**
    * @Author: mryunqi
    * @Description: 修改到期时间
    * @DateTime: 2023/9/29 18:08
    * @Params: renewalParams 到期时间参数
    * @Return  UnifiedResultDto<Object> 统一返回结果
    */
    @Override
    public UnifiedResultDto<Object> updateVmhostExpireTime(RenewalParams renewalParams) {
        // 判断虚拟机ID是否为空
        if (renewalParams.getHostId() == null){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_VM_NOT_EXIST, null);
        }
        Vmhost vmhost = this.getById(renewalParams.getHostId());
        if (vmhost == null){
            // 将vmHostId作为为vmid
            vmhost = this.getVmhostByVmId(Math.toIntExact(renewalParams.getHostId()));
        }
        // 判空
        if (vmhost == null){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_VM_NOT_EXIST, null);
        }
        // 判断虚拟机是否为禁用状态
        if (vmhost.getStatus() == 4){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_VM_IS_DISABLED, null);
        }
        // 修改到期时间
        vmhost.setExpirationTime(renewalParams.getExpirationTime());
        // 更新到期时间
        if (this.updateById(vmhost)){
            return new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, null);
        }
        return new UnifiedResultDto<>(UnifiedResultCode.ERROR_RENEWAL_FAILED, null);
    }
}

