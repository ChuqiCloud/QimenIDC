package com.chuqiyun.proxmoxveams.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.UnifiedLogger;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.dao.VmhostDao;
import com.chuqiyun.proxmoxveams.dto.IpDto;
import com.chuqiyun.proxmoxveams.dto.RenewalParams;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.dto.VmIpParams;
import com.chuqiyun.proxmoxveams.entity.Ippool;
import com.chuqiyun.proxmoxveams.entity.Ipstatus;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Os;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.dto.VmParams;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.*;
import com.chuqiyun.proxmoxveams.utils.ClientApiUtil;
import com.chuqiyun.proxmoxveams.utils.CloudInitNetworkUtil;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import com.chuqiyun.proxmoxveams.utils.TimeUtil;
import com.chuqiyun.proxmoxveams.utils.VmUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
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
    private static final long BACKUP_RESTORE_SHUTDOWN_TIMEOUT = 5 * 60 * 1000L;
    private static final long BACKUP_RESTORE_SHUTDOWN_WAIT = 2000L;
    private static final long IP_CHANGE_RESTART_TIMEOUT = 3 * 60 * 1000L;
    private static final long IP_CHANGE_RESTART_WAIT = 2000L;
    private static final String PENDING_STATUS = "creating";

    @Resource
    private MasterService masterService;
    @Resource
    private TaskService taskService;
    @Resource
    private OsService osService;
    @Resource
    private ConfigService configService;
    @Resource
    private IppoolService ippoolService;
    @Resource
    private IpstatusService ipstatusService;

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
        QueryWrapper<Vmhost> queryWrap = new QueryWrapper<>();
        Page<Vmhost> vmhostPage = new Page<>(page, limit);
        return this.page(vmhostPage, queryWrap.eq("delete_state",0));
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
        return this.page(vmhostPage,queryWrapper.eq("delete_state",0));
    }
    /**
     * @Author: 星禾
     * @Description: 分页查询回收站虚拟机实例信息
     * @DateTime: 2026/5/23 23:04
     */
    @Override
    public Page<Vmhost> selectPageByDelete(Integer page, Integer limit, QueryWrapper<Vmhost> queryWrapper) {
        Page<Vmhost> vmhostPage = new Page<>(page, limit);
        return this.page(vmhostPage, queryWrapper.eq("delete_state",1));
    }
    /**
     * @Author: 星禾
     * @Description: 分页查询回收站虚拟机实例信息 带参
     * @DateTime: 2026/5/23 23:04
     */
    @Override
    public Page<Vmhost> selectPageByDelete(Integer page, Integer limit) {
        QueryWrapper<Vmhost> queryWrap = new QueryWrapper<>();
        Page<Vmhost> vmhostPage = new Page<>(page, limit);
        return this.page(vmhostPage, queryWrap.eq("delete_state",1));
    }
    /**
    * @Author: mryunqi
    * @Description: 分页查询虚拟机实例信息，按照创建时间降序排列
    * @DateTime: 2023/11/26 20:09
    * @Params: Integer page 页码，Integer limit 每页数量
    * @Return Page<Vmhost> 分页数据
    */
    @Override
    public Page<Vmhost> selectPageByCreateTime(Integer page, Integer limit) {
        Page<Vmhost> vmhostPage = new Page<>(page, limit);
        return this.page(vmhostPage,new QueryWrapper<Vmhost>().orderByDesc("create_time").eq("delete_state",0));
    }

    /**
    * @Author: mryunqi
    * @Description: 分页查询虚拟机实例信息，按照创建时间降序排列，附加条件
    * @DateTime: 2023/11/26 20:11
    * @Params: Integer page 页码，Integer limit 每页数量，QueryWrapper<Vmhost> queryWrapper 附加条件
    * @Return Page<Vmhost> 分页数据
    */
    @Override
    public Page<Vmhost> selectPageByCreateTime(Integer page, Integer limit, QueryWrapper<Vmhost> queryWrapper) {
        Page<Vmhost> vmhostPage = new Page<>(page, limit);
        return this.page(vmhostPage,queryWrapper.orderByDesc("create_time").eq("delete_state",0));
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
        return this.page(vmhostPage,new QueryWrapper<Vmhost>().like("ip_config",ip).eq("delete_state",0));
    }

    /**
    * @Author: mryunqi
    * @Description: 根据主机名查询虚拟机实例信息
    * @DateTime: 2023/8/24 16:17
    * @Params: String name 主机名
    * @Return Vmhost 虚拟机实例信息
    */
    @Override
    public Page<Vmhost> getVmhostByName(Integer page, Integer limit, String hostname) {
        Page<Vmhost> vmhostPage = new Page<>(page, limit);
        return this.page(vmhostPage,new QueryWrapper<Vmhost>().like("hostname",hostname).eq("delete_state",0));
    }

    /**
     * @Author: 星禾
     * @Description: 根据主机名查询虚拟机实例信息 - 精确
     * @DateTime: 2026/1/16 17:08
     */
    @Override
    public Vmhost getVmhostByNameOne(String hostname) {
        return this.getOne(new QueryWrapper<Vmhost>().eq("hostname",hostname).eq("delete_state",0));
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
        return this.page(vmhostPage,new QueryWrapper<Vmhost>().eq("nodeid",nodeId).eq("delete_state",0));
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
        return this.page(vmhostPage,new QueryWrapper<Vmhost>().eq("status",status).eq("delete_state",0));
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
        vmhost.setHostname(vmParams.getHostname());
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
        vmhost.setMbpsRd(vmParams.getMbpsRd());
        vmhost.setMbpsWr(vmParams.getMbpsWr());
        vmhost.setMbpsRdMax(vmParams.getMbpsRdMax());
        vmhost.setMbpsWrMax(vmParams.getMbpsWrMax());
        vmhost.setIopsRd(vmParams.getIopsRd());
        vmhost.setIopsWr(vmParams.getIopsWr());
        vmhost.setIopsRdMax(vmParams.getIopsRdMax());
        vmhost.setIopsWrMax(vmParams.getIopsWrMax());
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
        vmhost.setIfnat(vmParams.getIfnat());
        vmhost.setNatnum(vmParams.getNatnum());
        vmhost.setExtraFlowLimit(vmParams.getExtraFlowLimit());
        vmhost.setResetFlowTime(vmParams.getResetFlowTime());
        vmhost.setOutFlow(vmParams.getOutFlow());
        vmhost.setFlowLimit(vmParams.getFlowLimit()*1024*1024*1024);
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
    public HashMap<String,Object> power(Integer hostId, String action,JSONObject data) {
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
        // 15=流量超限
        // action类型有start=开机、stop=关机、reboot=重启、shutdown=强制关机、suspend=挂起、resume=恢复、pause=暂停、unpause=恢复
        // qosPause=超流暂停
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
                // 判断虚拟机是否流量超限
                if (vmStatus == 15){
                    result.put("status", false);
                    result.put("msg", "虚拟机流量超限，无法开机");
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
                // 判断虚拟机是否流量超限
                if (vmStatus == 15){
                    result.put("status", false);
                    result.put("msg", "虚拟机流量超限，无法重启");
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
                // 判断虚拟机是否流量超限
                if (vmStatus == 15){
                    result.put("status", false);
                    result.put("msg", "虚拟机流量超限，已经挂起");
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
                // 判断虚拟机是否流量超限
                if (vmStatus == 15){
                    result.put("status", false);
                    result.put("msg", "虚拟机流量超限，已经暂停");
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
                // 判断data是否为空
                if (data != null) {
                    // 设置虚拟机暂停原因
                    vmhost.setPauseInfo(data.getString("pauseInfo"));
                }
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
            case "qosPause":{
                // 设置虚拟机状态为11，表示暂停中
                vmhost.setStatus(11);
                // 判断data是否为空
                if (data != null) {
                    // 设置虚拟机暂停原因
                    vmhost.setPauseInfo(data.getString("pauseInfo"));
                }
                // 更新虚拟机状态
                this.updateById(vmhost);
                Task vmPauseTask = new Task();
                vmPauseTask.setNodeid(nodeId);
                vmPauseTask.setVmid(vmId);
                vmPauseTask.setHostid(hostId);
                vmPauseTask.setType(QOS_PAUSE);
                vmPauseTask.setStatus(0);
                vmPauseTask.setCreateDate(time);
                if (taskService.save(vmPauseTask)) {
                    log.info("[Task-PauseVm] 超流暂停任务创建成功: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                    result.put("status", true);

                    // 添加任务流程
                    this.addVmHostTask(hostId, vmPauseTask.getId());
                }
                else {
                    log.info("[Task-PauseVm] 超流暂停任务创建失败: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                    result.put("status", false);
                    result.put("msg", "超流暂停任务创建失败");
                }
                return result;
            }
            case "unpause":{
                // 判断虚拟机状态是否为暂停或者超流暂停
                if (vmStatus != 4 && vmStatus != 15) {
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
            return maxVmid+1;
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
            Vmhost vmhost = this.getOne(vmhostQueryWrapper.eq("delete_state",0));
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
                this.power(vmhost.getId(),"shutdown",null);
                continue;
            }
            // 判断数据库中的状态是否为4(暂停)，且pve中的状态不为2(挂起)
            if (vmStatus == 4 && initStatus != 2 && initStatus != 1){
                // 暂停pve中的虚拟机
                this.power(vmhost.getId(),"shutdown",null);
                continue;
            }
            if (vmStatus == 6 || vmStatus == 13 || vmStatus == 15 || vmStatus == 4){
                // 6创建中 13重装系统中 15超流暂停 4暂停 状态不更新
                continue;
            }
            // 其他情况，直接更新数据库中的状态
            if (!updateVmhostStatusOnly(vmhost.getId(), initStatus)) {
                log.warn("[VmStatusSync] 更新虚拟机状态失败: NodeId={}, HostId={}, VmId={}, Status={}",
                        nodeId, vmhost.getId(), vmhost.getVmid(), initStatus);
                continue;
            }
            vmhost.setStatus(initStatus);
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
        Master node = masterService.getById(vmhost.getNodeid());

        // 判断虚拟机是否为禁用状态
        if (vmhost.getStatus() == 4){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_VM_IS_DISABLED, null);
        }
        // 判断虚拟机是否为到期
        if (vmhost.getStatus() == 5){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_VM_IS_EXPIRED, null);
        }
        // 判断虚拟机是否为创建/重装中
        if (vmhost.getStatus() == 6 || vmhost.getStatus() == 13){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_VM_IS_INSTALLOS, null);
        }
        // 判断虚拟机是否存在快照
        if (hasVmSnapshot(node, vmhost)){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_VM_HAS_SNAPSHOT, null);
        }

        Os os = osService.isExistOs(osName);
        // 判断镜像是否存在
        if (os == null) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_CLOUD_IMAGE_NOT_EXIST, null);
        }

        // 判断虚拟机是否为开机状态
        if (vmhost.getStatus() == 0){
            this.power(vmhost.getId(), "shutdown",null);
        }
        //重置用户名
        Os os_old = osService.isExistOs(vmhost.getOs());
        if (!Objects.equals(os_old.getOsType(), os.getOsType()))
        {
            if (os.getOsType().equals("windows")) {
                vmhost.setUsername("administrator");
            } else {
                vmhost.setUsername("root");
            }
        }
        if(!Objects.equals(vmhost.getOsType(), os.getType())) {
            System.out.println("vmID：" + vmHostId + " 原系统：" + vmhost.getOsType() + " 新系统：" + os.getType() + " 不一致，重置用户名和NAT");
            //重置Nat转发
            int dest_port = 0;
            if (vmhost.getIfnat() == 1) {
                if (Objects.equals(os.getType(), "windows")) {
                    dest_port = 3389;
                } else {
                    dest_port = 22;
                }
                Object vmNat = this.getVmhostNatByVmid(1, 99999, vmhost.getId());
                if (vmNat != null) {
                    ResponseResult responseResult = (ResponseResult) vmNat;
                    Integer code = responseResult.getCode();
                    String message = responseResult.getMessage();
                    if (20000 == code) {
                        Object data = responseResult.getData();
                        if (data instanceof JSONArray dataList) {
                            for (int i = 0; i < dataList.size(); i++) {
                                try {
                                    JSONObject item = dataList.getJSONObject(i);
                                    Integer destinationPort = item.getInteger("destination_port");
                                    if (destinationPort != null && (destinationPort == 22 || destinationPort == 3389)) {
                                        String sourceIp = item.getString("source_ip");
                                        Integer sourcePort = item.getInteger("source_port");
                                        String destinationIp = item.getString("destination_ip");
                                        String protocol = item.getString("protocol");
                                        Integer vm = item.getInteger("vm");

                                        this.delVmhostNat(sourceIp, sourcePort, destinationIp, destinationPort, protocol, vm);
                                        System.out.println("Deleted NAT forwarding for destination port: " + destinationPort);
                                    }
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
            }
            String dest_ip = vmhost.getIpList().get(0);
            int s_port = ThreadLocalRandom.current().nextInt(1000, 65536);
            if (!this.addVmhostNat(node.getHost(), s_port, dest_ip, dest_port, "tcp", vmhost.getId())) {
                s_port = ThreadLocalRandom.current().nextInt(1000, 65536);
                this.addVmhostNat(node.getHost(), s_port, dest_ip, dest_port, "tcp", vmhost.getId());
            }
        }
        // 重置osName
        osName = os.getFileName();
        // 设置虚拟机状态为重装系统中
        vmhost.setStatus(13);
        vmhost.setOsName(os.getFileName());
        vmhost.setOsType(os.getType());
        vmhost.setOs(os.getName());

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
     * @Author: 星禾
     * @Description: 判断虚拟机是否存在快照
     * @DateTime: 2026/5/29 23:32
     */
    private boolean hasVmSnapshot(Master node, Vmhost vmhost) {
        HashMap<String, String> cookieMap = masterService.getMasterCookieMap(vmhost.getNodeid());
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        JSONObject snapshotJson = proxmoxApiUtil.getVmSnapShot(node, cookieMap, vmhost.getVmid());
        if (snapshotJson == null) {
            return false;
        }

        JSONArray snapshots = snapshotJson.getJSONArray("data");
        if (snapshots == null || snapshots.isEmpty()) {
            return false;
        }
        for (int i = 0; i < snapshots.size(); i++) {
            JSONObject snapshot = snapshots.getJSONObject(i);
            if (snapshot == null) {
                continue;
            }
            String snapName = snapshot.getString("name");
            if (StringUtils.isBlank(snapName)) {
                snapName = snapshot.getString("snapname");
            }
            if (StringUtils.isNotBlank(snapName) && !"current".equals(snapName)) {
                return true;
            }
        }
        return false;
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
//        if (vmhost == null){
//            // 将vmHostId作为为vmid
//            vmhost = this.getVmhostByVmId(Math.toIntExact(vmHostId));
//        }
        // 判空
        if (vmhost == null){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_VM_NOT_EXIST, null);
        }

        // 判断虚拟机是否为禁用状态
        /*if (vmhost.getStatus() == 4){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_VM_IS_DISABLED, null);
        }*/

        // 判断虚拟机是否为开机状态
        if (vmhost.getStatus() == 0){
            this.power(vmhost.getId(), "shutdown",null);
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
     * @Author: 星禾
     * @Description: 删除虚拟机到回收站
     * @DateTime: 2025/5/23 23:12
     * @Params: Long vmHostId 虚拟机id
     * @Return UnifiedResultDto<Object> 统一返回结果
     */
    @Override
    public UnifiedResultDto<Object> deleteVmToRecycle(Long vmHostId){
        // 获取虚拟机信息
        Vmhost vmhost = this.getById(vmHostId);
        // 判空
        if (vmhost == null){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_VM_NOT_EXIST, null);
        }
        if (vmhost.getDeleteState() == 1)
            return new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, null);
        // 判断虚拟机是否为开机状态
        if (vmhost.getStatus() == 0){
            this.power(vmhost.getId(), "shutdown",null);

        }

        //更新虚拟机状态 和 到期时间
        vmhost.setDeleteState(1);
        Integer deleteDays = configService.getDeleteDays();
        vmhost.setExpirationTime(System.currentTimeMillis() + deleteDays * 24 * 60 * 60 * 1000L);
        this.updateById(vmhost);

        UnifiedLogger.log(UnifiedLogger.LogType.TASK_DELETE_VM,"成功将虚拟机进入回收站，虚拟机id为：" + vmHostId);
        return new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, null);
    }
    /**
     * @Author: 星禾
     * @Description: 从回收站恢复虚拟机
     * @DateTime: 2025/5/23 23:56
     * @Params: Long vmHostId 虚拟机id
     * @Return UnifiedResultDto<Object> 统一返回结果
     */
    @Override
    public UnifiedResultDto<Object> unDeleteVm(Long vmHostId){
        // 获取虚拟机信息
        Vmhost vmhost = this.getById(vmHostId);
        // 判空
        if (vmhost == null){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_VM_NOT_EXIST, null);
        }
        if (vmhost.getDeleteState() == 0)
            return new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, null);
        // 判断虚拟机是否为关机状态
        if (vmhost.getStatus() == 1){
            this.power(vmhost.getId(), "start",null);
        }

        //更新虚拟机状态
        vmhost.setDeleteState(0);
        // 时间设定为99年后到期
        vmhost.setExpirationTime(System.currentTimeMillis()+315360000000L);
        this.updateById(vmhost);

        UnifiedLogger.log(UnifiedLogger.LogType.TASK_DELETE_VM,"成功将虚拟机从回收站中恢复，虚拟机id为：" + vmHostId);
        return new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, null);
    }

    /**
     * @Author: 星禾
     * @Description: 更新虚拟机
     * @DateTime: 2025/11/25 11:22
     * @Params: VmParams vmParams
     * @Return UnifiedResultDto<Object> 统一返回结果
     */
    @Override
    public UnifiedResultDto<Object> updateVm(VmParams vmParams){
        // 获取虚拟机信息
        Vmhost vmhost = this.getById(vmParams.getHostid());
        // 判空
        if (vmhost == null){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_VM_NOT_EXIST, null);
        }
        if (vmParams.getFlowLimit() != null ) vmhost.setFlowLimit(vmParams.getFlowLimit()*1024*1024*1024);
        if (vmParams.getExtraFlowLimit() != null) vmhost.setExtraFlowLimit(vmParams.getExtraFlowLimit()*1024*1024*1024);
        if (vmParams.getNatnum() != null ) vmhost.setNatnum(vmParams.getNatnum());
        if (vmParams.getResetFlowTime() != null ) vmhost.setResetFlowTime(vmParams.getResetFlowTime());
        if (vmParams.getOutFlow() != null ) vmhost.setOutFlow(vmParams.getOutFlow());
        this.updateById(vmhost);
        if (vmParams.getSockets() != null || vmParams.getCores() != null || vmParams.getThreads() != null
        || vmParams.getMemory() != null || vmParams.getSystemDiskSize() != null || vmParams.getBandwidth() != null) {
            // 判断状态，先关机
            if (vmhost.getStatus() == 0){
                this.power(vmhost.getId(), "shutdown",null);
            }
            Master node = masterService.getById(vmhost.getNodeid());
            // 获取cookie
            HashMap<String, String> cookieMap = masterService.getMasterCookieMap(vmhost.getNodeid());
            ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
            if (vmParams.getSockets() != null) {
                proxmoxApiUtil.resetVmConfig(node,cookieMap,vmhost.getVmid(),"sockets",vmParams.getSockets().toString());
                vmhost.setSockets(vmParams.getSockets());
            }
            if (vmParams.getCores() != null) {
                proxmoxApiUtil.resetVmConfig(node,cookieMap,vmhost.getVmid(),"cores",vmParams.getCores().toString());
                vmhost.setCores(vmParams.getCores());
            }
            if (vmParams.getThreads() != null) {
                proxmoxApiUtil.resetVmConfig(node,cookieMap,vmhost.getVmid(),"threads",vmParams.getThreads().toString());
                vmhost.setThreads(vmParams.getThreads());
            }
            if (vmParams.getMemory() != null) {
                proxmoxApiUtil.resetVmConfig(node,cookieMap,vmhost.getVmid(),"memory",vmParams.getMemory().toString());
                vmhost.setMemory(vmParams.getMemory());
            }
            if (vmParams.getSystemDiskSize() != null) {
                // 硬盘只增不减
                if (vmParams.getSystemDiskSize() < vmhost.getSystemDiskSize()){
                    return new UnifiedResultDto<>(UnifiedResultCode.ERROR_UNKNOWN, null);
                } else {
                    HashMap<String, Object> params = new HashMap<>();
                    params.put("disk","scsi0");
                    params.put("size",vmParams.getSystemDiskSize()+"G");
                    try {
                        proxmoxApiUtil.putNodeApi(node,cookieMap, "/nodes/"+node.getNodeName()+"/qemu/"+vmhost.getVmid()+"/resize", params);
                        vmhost.setSystemDiskSize(vmParams.getSystemDiskSize());
                    } catch (Exception e) {
                        return new UnifiedResultDto<>(UnifiedResultCode.ERROR_UNKNOWN, null);
                    }
                }
            }
            if (vmParams.getBandwidth() != null) {
                double bandWidthValue = vmParams.getBandwidth() / 8.0;
                String bandWidth = String.format("%.2f", bandWidthValue);
                proxmoxApiUtil.resetVmConfig(node, cookieMap, vmhost.getVmid(), "net0", "virtio,bridge=" + vmhost.getBridge() + ",rate=" + bandWidth);
                vmhost.setBandwidth(vmParams.getBandwidth());
            }
            Task vmStartTask = new Task();
            vmStartTask.setNodeid(vmhost.getNodeid());
            vmStartTask.setVmid(vmhost.getVmid());
            vmStartTask.setHostid(vmhost.getId());
            vmStartTask.setType(START_VM);
            vmStartTask.setStatus(0);
            vmStartTask.setCreateDate(System.currentTimeMillis());
            if (taskService.save(vmStartTask)) {
                log.info("[Task-StartVm] 改配-开机任务创建成功: NodeId: " + vmhost.getNodeid() + ",VmId: " + vmhost.getVmid() + ",HostId: " + vmhost.getId());
            }
        }
        this.updateById(vmhost);
        return new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, null);
    }

    /**
     * @Author: 星禾
     * @Description: 修改虚拟机IP并重生成cloud-init镜像
     * @DateTime: 2026/6/4 20:14
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnifiedResultDto<Object> updateVmIp(VmIpParams vmIpParams) {
        if (vmIpParams == null || vmIpParams.getHostId() == null) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_INVALID_PARAM, null);
        }
        Vmhost vmhost = this.getById(vmIpParams.getHostId());
        if (vmhost == null) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_VM_NOT_EXIST, null);
        }
        Master node = masterService.getById(vmhost.getNodeid());
        if (node == null) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NODE_NOT_EXIST, null);
        }

        int networkIndex = vmIpParams.getNetworkIndex() == null ? 1 : vmIpParams.getNetworkIndex();
        if (networkIndex < 1) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_INVALID_PARAM, null);
        }
        HashMap<String, String> ipConfig = new HashMap<>();
        if (vmhost.getIpConfig() != null) {
            ipConfig.putAll(vmhost.getIpConfig());
        }
        String ipConfigKey = String.valueOf(networkIndex);
        String oldIp = getIpFromCloudInitConfig(ipConfig.get(ipConfigKey));
        Ippool oldIppool = oldIp == null ? null : getIppoolByIpAndNodeId(oldIp, vmhost.getNodeid());
        Ippool newIppool = getNewIppool(vmIpParams, vmhost, oldIppool);
        if (newIppool == null) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NO_AVAILABLE_IPV4, null);
        }
        if (!Objects.equals(newIppool.getNodeId(), vmhost.getNodeid())) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_INVALID_PARAM, null);
        }
        if (oldIp != null && oldIp.equals(newIppool.getIp())) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_INVALID_PARAM, null);
        }
        if (isVmhostIpUsed(vmhost, ipConfig, oldIp, newIppool.getIp())) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_INVALID_PARAM, null);
        }
        Ipstatus ipstatus = ipstatusService.getById(newIppool.getPoolId());
        if (ipstatus == null) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_IP_POOL_NOT_EXIST, null);
        }
        Integer mask = getIpMask(ipstatus, newIppool);
        String gateway = StringUtils.defaultIfBlank(newIppool.getGateway(), ipstatus.getGateway());
        if (mask == null || StringUtils.isBlank(gateway)) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_INVALID_PARAM, null);
        }

        String newIpConfig = "ip=" + newIppool.getIp() + "/" + mask + ",gw=" + gateway;
        if (!bindNewIp(newIppool, vmhost)) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NO_AVAILABLE_IPV4, null);
        }
        releaseOldIp(oldIppool, newIppool);

        ipConfig.put(ipConfigKey, newIpConfig);
        List<String> newIpList = replaceVmhostIpList(vmhost, ipConfig, oldIp, newIppool.getIp());
        updateVmhostIpFields(vmhost, ipConfig, newIpList, "更新虚拟机IP失败");
        log.info("[VmIpChange] 修改IP后数据库同步成功: NodeId={}, HostId={}, VmId={}, IpList={}",
                vmhost.getNodeid(), vmhost.getId(), vmhost.getVmid(), newIpList);

        syncSingleNicCloudInitNetwork(vmhost, node, getNameserversByIpConfig(vmhost.getNodeid(), ipConfig, Collections.singletonList(newIppool)));
        restartVmAfterIpChange(vmhost, node);
        return new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, "修改IP成功，已创建异步强制重启任务，请稍后查看任务状态");
    }

    /**
     * @Author: 星禾
     * @Description: 给虚拟机新增单网卡多IP并重生成cloud-init镜像
     * @DateTime: 2026/6/6 12:40
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnifiedResultDto<Object> addVmIp(VmIpParams vmIpParams) {
        if (vmIpParams == null || vmIpParams.getHostId() == null) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_INVALID_PARAM, null);
        }
        Vmhost vmhost = this.getById(vmIpParams.getHostId());
        if (vmhost == null) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_VM_NOT_EXIST, null);
        }
        Master node = masterService.getById(vmhost.getNodeid());
        if (node == null) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NODE_NOT_EXIST, null);
        }
        int count = getAddIpCount(vmIpParams);
        if (count < 1) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_INVALID_PARAM, null);
        }

        HashMap<String, String> ipConfig = new HashMap<>();
        if (vmhost.getIpConfig() != null) {
            ipConfig.putAll(vmhost.getIpConfig());
        }
        Set<String> usedIpSet = getVmhostIpSet(vmhost, ipConfig);
        List<Ippool> addIppoolList = getAddIppoolList(vmIpParams, vmhost, usedIpSet, count);
        if (addIppoolList == null || addIppoolList.isEmpty()) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NO_AVAILABLE_IPV4, null);
        }

        int networkIndex = vmIpParams.getNetworkIndex() == null ? getNextIpConfigIndex(ipConfig) : vmIpParams.getNetworkIndex();
        if (networkIndex < 1) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_INVALID_PARAM, null);
        }
        List<Ippool> boundIppoolList = new ArrayList<>();
        for (Ippool ippool : addIppoolList) {
            Ipstatus ipstatus = ipstatusService.getById(ippool.getPoolId());
            if (ipstatus == null) {
                return new UnifiedResultDto<>(UnifiedResultCode.ERROR_IP_POOL_NOT_EXIST, null);
            }
            String ipConfigValue = buildIpConfigValue(ippool, ipstatus);
            if (ipConfigValue == null) {
                return new UnifiedResultDto<>(UnifiedResultCode.ERROR_INVALID_PARAM, null);
            }
            while (ipConfig.containsKey(String.valueOf(networkIndex))) {
                networkIndex++;
            }
            if (!bindOrReuseIp(ippool, vmhost)) {
                throw new IllegalStateException("绑定新增IP失败: ip=" + ippool.getIp());
            }
            ipConfig.put(String.valueOf(networkIndex), ipConfigValue);
            boundIppoolList.add(ippool);
            networkIndex++;
        }

        List<String> newIpList = buildVmhostIpList(vmhost, ipConfig);
        updateVmhostIpFields(vmhost, ipConfig, newIpList, "新增虚拟机IP失败");
        log.info("[VmIpChange] 新增IP后数据库同步成功: NodeId={}, HostId={}, VmId={}, AddedIps={}, IpList={}",
                vmhost.getNodeid(), vmhost.getId(), vmhost.getVmid(),
                boundIppoolList.stream().map(Ippool::getIp).collect(Collectors.toList()), newIpList);

        syncSingleNicCloudInitNetwork(vmhost, node, getNameserversByIpConfig(vmhost.getNodeid(), ipConfig, boundIppoolList));
        restartVmAfterIpChange(vmhost, node);
        return new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, "新增IP成功，已创建异步强制重启任务，请稍后查看任务状态");
    }

    /**
     * @Author: 星禾
     * @Description: 删除虚拟机单网卡多IP并重生成cloud-init镜像
     * @DateTime: 2026/6/6 12:40
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnifiedResultDto<Object> deleteVmIp(VmIpParams vmIpParams) {
        if (vmIpParams == null || vmIpParams.getHostId() == null) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_INVALID_PARAM, null);
        }
        Vmhost vmhost = this.getById(vmIpParams.getHostId());
        if (vmhost == null) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_VM_NOT_EXIST, null);
        }
        Master node = masterService.getById(vmhost.getNodeid());
        if (node == null) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NODE_NOT_EXIST, null);
        }

        HashMap<String, String> ipConfig = new HashMap<>();
        if (vmhost.getIpConfig() != null) {
            ipConfig.putAll(vmhost.getIpConfig());
        }
        Set<String> deleteIpSet = getDeleteIpSet(vmIpParams, ipConfig);
        if (deleteIpSet.isEmpty()) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_INVALID_PARAM, null);
        }

        HashMap<String, String> newIpConfig = removeIpConfigItems(ipConfig, deleteIpSet);
        if (newIpConfig.size() == ipConfig.size() || newIpConfig.isEmpty()) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_INVALID_PARAM, null);
        }
        Set<String> removedIpSet = getRemovedIpSet(ipConfig, newIpConfig);
        releaseDeletedIps(removedIpSet, vmhost);

        List<String> newIpList = buildVmhostIpListAfterDelete(vmhost, newIpConfig, removedIpSet);
        updateVmhostIpFields(vmhost, newIpConfig, newIpList, "删除虚拟机IP失败");
        log.info("[VmIpChange] 删除IP后数据库同步成功: NodeId={}, HostId={}, VmId={}, RemovedIps={}, IpList={}",
                vmhost.getNodeid(), vmhost.getId(), vmhost.getVmid(), removedIpSet, newIpList);

        syncSingleNicCloudInitNetwork(vmhost, node, getNameserversByIpConfig(vmhost.getNodeid(), newIpConfig, null));
        restartVmAfterIpChange(vmhost, node);
        return new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, "删除IP成功，已创建异步强制重启任务，请稍后查看任务状态");
    }

    /**
     * @Author: 星禾
     * @Description: 同步节点下手动绑定但未写入vmhost的IP数据
     * @DateTime: 2026/6/6 20:55
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnifiedResultDto<Object> syncVmManualIp(VmIpParams vmIpParams) {
        if (vmIpParams == null || vmIpParams.getNodeId() == null) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_INVALID_PARAM, null);
        }
        Integer nodeId = vmIpParams.getNodeId();
        List<Vmhost> vmhostList = getVmhostListByNodeId(nodeId);
        log.info("[Sync-ManualVmIp] 开始同步节点手动绑定IP: NodeId={}, VmCount={}", nodeId, vmhostList.size());

        List<LinkedHashMap<String, Object>> vmList = new ArrayList<>();
        List<LinkedHashMap<String, Object>> failedVmList = new ArrayList<>();
        int syncVmCount = 0;
        int syncIpCount = 0;
        for (Vmhost vmhost : vmhostList) {
            log.info("[Sync-ManualVmIp] 开始同步虚拟机: NodeId={}, HostId={}, VmId={}", nodeId, vmhost.getId(), vmhost.getVmid());
            try {
                LinkedHashMap<String, Object> vmResult = syncManualIpForVmhost(vmhost);
                vmList.add(vmResult);
                Object syncCountValue = vmResult.get("syncCount");
                int currentSyncCount = syncCountValue instanceof Number ? ((Number) syncCountValue).intValue() : 0;
                if (currentSyncCount > 0) {
                    syncVmCount++;
                }
                syncIpCount += currentSyncCount;
                log.info("[Sync-ManualVmIp] 虚拟机同步完成: NodeId={}, HostId={}, VmId={}, SyncCount={}, AddedIps={}, IgnoredIps={}, Message={}",
                        nodeId,
                        vmhost.getId(),
                        vmhost.getVmid(),
                        currentSyncCount,
                        vmResult.get("addedIps"),
                        vmResult.get("ignoredIps"),
                        vmResult.get("message"));
            } catch (Exception e) {
                log.error("[Sync-ManualVmIp] 同步虚拟机失败: NodeId={}, HostId={}, VmId={}", nodeId, vmhost.getId(), vmhost.getVmid(), e);
                LinkedHashMap<String, Object> failedVmData = new LinkedHashMap<>();
                failedVmData.put("hostId", vmhost.getId());
                failedVmData.put("vmid", vmhost.getVmid());
                failedVmData.put("message", e.getMessage());
                failedVmList.add(failedVmData);
            }
        }

        LinkedHashMap<String, Object> resultData = new LinkedHashMap<>();
        resultData.put("nodeId", nodeId);
        resultData.put("vmCount", vmhostList.size());
        resultData.put("syncVmCount", syncVmCount);
        resultData.put("syncIpCount", syncIpCount);
        resultData.put("failedVmCount", failedVmList.size());
        resultData.put("vmList", vmList);
        resultData.put("failedVmList", failedVmList);
        if (vmhostList.isEmpty()) {
            resultData.put("message", "该节点下没有可同步的虚拟机");
        } else if (failedVmList.isEmpty()) {
            resultData.put("message", "节点手动绑定IP同步完成");
        } else {
            resultData.put("message", "节点手动绑定IP同步完成，部分虚拟机同步失败");
        }
        log.info("[Sync-ManualVmIp] 节点同步结束: NodeId={}, VmCount={}, SyncVmCount={}, SyncIpCount={}, FailedVmCount={}, Message={}",
                nodeId,
                vmhostList.size(),
                syncVmCount,
                syncIpCount,
                failedVmList.size(),
                resultData.get("message"));
        return new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, resultData);
    }

    private LinkedHashMap<String, Object> syncManualIpForVmhost(Vmhost vmhost) {
        HashMap<String, String> ipConfig = new HashMap<>();
        if (vmhost.getIpConfig() != null) {
            ipConfig.putAll(vmhost.getIpConfig());
        }
        Set<String> currentIpSet = getVmhostIpSet(vmhost, ipConfig);
        List<Ippool> boundIppoolList = getBoundIppoolListByNodeIdAndVmId(vmhost.getNodeid(), vmhost.getVmid());
        log.info("[Sync-ManualVmIp] 检测虚拟机已绑定IP池记录: NodeId={}, HostId={}, VmId={}, BoundIpCount={}",
                vmhost.getNodeid(), vmhost.getId(), vmhost.getVmid(), boundIppoolList.size());

        List<String> addedIpList = new ArrayList<>();
        List<String> ignoredIpList = new ArrayList<>();
        int networkIndex = getNextIpConfigIndex(ipConfig);
        for (Ippool ippool : boundIppoolList) {
            String currentIp = StringUtils.trimToNull(ippool.getIp());
            if (currentIp == null) {
                log.info("[Sync-ManualVmIp] 跳过空IP记录: NodeId={}, HostId={}, VmId={}, IppoolId={}",
                        vmhost.getNodeid(), vmhost.getId(), vmhost.getVmid(), ippool.getId());
                continue;
            }
            if (currentIpSet.contains(currentIp)) {
                ignoredIpList.add(currentIp);
                log.info("[Sync-ManualVmIp] 忽略已存在IP: NodeId={}, HostId={}, VmId={}, Ip={}",
                        vmhost.getNodeid(), vmhost.getId(), vmhost.getVmid(), currentIp);
                continue;
            }
            Ipstatus ipstatus = ipstatusService.getById(ippool.getPoolId());
            if (ipstatus == null) {
                throw new IllegalStateException("同步手动绑定IP失败，IP池不存在: poolId=" + ippool.getPoolId());
            }
            String ipConfigValue = buildIpConfigValue(ippool, ipstatus);
            if (StringUtils.isBlank(ipConfigValue)) {
                throw new IllegalStateException("同步手动绑定IP失败，IP配置无效: ip=" + currentIp);
            }
            while (ipConfig.containsKey(String.valueOf(networkIndex))) {
                networkIndex++;
            }
            ipConfig.put(String.valueOf(networkIndex), ipConfigValue);
            currentIpSet.add(currentIp);
            addedIpList.add(currentIp);
            log.info("[Sync-ManualVmIp] 写入缺失IP到vmhost: NodeId={}, HostId={}, VmId={}, Ip={}, NetworkIndex={}",
                    vmhost.getNodeid(), vmhost.getId(), vmhost.getVmid(), currentIp, networkIndex);
            networkIndex++;
        }

        LinkedHashMap<String, Object> resultData = new LinkedHashMap<>();
        resultData.put("hostId", vmhost.getId());
        resultData.put("nodeId", vmhost.getNodeid());
        resultData.put("vmid", vmhost.getVmid());
        resultData.put("addedIps", addedIpList);
        resultData.put("ignoredIps", ignoredIpList);
        resultData.put("syncCount", addedIpList.size());

        if (boundIppoolList.isEmpty()) {
            resultData.put("message", "未发现需要同步的手动绑定IP");
            log.info("[Sync-ManualVmIp] 虚拟机无需同步: NodeId={}, HostId={}, VmId={}, Message={}",
                    vmhost.getNodeid(), vmhost.getId(), vmhost.getVmid(), resultData.get("message"));
            return resultData;
        }

        List<String> newIpList = buildVmhostIpList(vmhost, ipConfig);
        List<?> newIpData = VmUtil.splitIpAddress(ipConfig);
        boolean needUpdate = !Objects.equals(vmhost.getIpConfig(), ipConfig)
                || !Objects.equals(vmhost.getIpList(), newIpList)
                || !Objects.equals(vmhost.getIpData(), newIpData);

        if (needUpdate) {
            updateVmhostIpFields(vmhost, ipConfig, newIpList, "同步手动绑定IP失败");
            log.info("[Sync-ManualVmIp] vmhost IP数据更新成功: NodeId={}, HostId={}, VmId={}, NewIpList={}",
                    vmhost.getNodeid(), vmhost.getId(), vmhost.getVmid(), newIpList);
        }

        if (addedIpList.isEmpty()) {
            resultData.put("message", "同步完成，vmhost中已存在这些IP");
        } else {
            resultData.put("message", "同步手动绑定IP成功");
        }
        log.info("[Sync-ManualVmIp] 虚拟机同步结果: NodeId={}, HostId={}, VmId={}, SyncCount={}, Message={}",
                vmhost.getNodeid(), vmhost.getId(), vmhost.getVmid(), addedIpList.size(), resultData.get("message"));
        return resultData;
    }

    private Ippool getNewIppool(VmIpParams vmIpParams, Vmhost vmhost, Ippool oldIppool) {
        String newIp = StringUtils.defaultIfBlank(vmIpParams.getNewIp(), vmIpParams.getIp());
        if (StringUtils.isNotBlank(newIp)) {
            Ippool ippool = getIppoolByIpAndNodeId(newIp, vmhost.getNodeid());
            if (ippool == null || !Objects.equals(ippool.getStatus(), 0)) {
                return null;
            }
            return ippool;
        }
        Integer poolId = vmIpParams.getPoolId();
        if (poolId == null && oldIppool != null) {
            poolId = oldIppool.getPoolId();
        }
        return ippoolService.getOneFreeIpByNodeId(vmhost.getNodeid(), poolId);
    }

    private List<Ippool> getAddIppoolList(VmIpParams vmIpParams, Vmhost vmhost, Set<String> usedIpSet, int count) {
        List<String> requestIpList = getRequestIpList(vmIpParams);
        if (!requestIpList.isEmpty()) {
            List<Ippool> ippoolList = new ArrayList<>();
            for (String ip : requestIpList) {
                if (usedIpSet.contains(ip)) {
                    return null;
                }
                Ippool ippool = getIppoolByIpAndNodeIdAndPoolId(ip, vmhost.getNodeid(), vmIpParams.getPoolId());
                if (ippool == null || !isAddIpAvailable(ippool, vmhost)) {
                    return null;
                }
                ippoolList.add(ippool);
            }
            return ippoolList;
        }

        List<Ippool> ippoolList = new ArrayList<>();
        Set<Integer> selectedIdSet = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
            Ippool ippool = getOneFreeIppoolByNodeId(vmhost.getNodeid(), null, selectedIdSet, usedIpSet);
            if (ippool == null) {
                return null;
            }
            ippoolList.add(ippool);
            selectedIdSet.add(ippool.getId());
            usedIpSet.add(ippool.getIp());
        }
        return ippoolList;
    }

    private Set<String> getDeleteIpSet(VmIpParams vmIpParams, HashMap<String, String> ipConfig) {
        Set<String> deleteIpSet = new LinkedHashSet<>(getRequestIpList(vmIpParams));
        if (!deleteIpSet.isEmpty()) {
            return deleteIpSet;
        }
        Integer networkIndex = vmIpParams.getNetworkIndex();
        if (networkIndex == null || networkIndex < 1) {
            return deleteIpSet;
        }
        String ip = getIpFromCloudInitConfig(ipConfig.get(String.valueOf(networkIndex)));
        if (StringUtils.isNotBlank(ip)) {
            deleteIpSet.add(ip);
        }
        return deleteIpSet;
    }

    private HashMap<String, String> removeIpConfigItems(HashMap<String, String> ipConfig, Set<String> deleteIpSet) {
        HashMap<String, String> newIpConfig = new HashMap<>();
        List<Map.Entry<String, String>> entries = new ArrayList<>(ipConfig.entrySet());
        entries.sort(Comparator.comparingInt(entry -> getIpConfigIndex(entry.getKey())));
        int index = 1;
        for (Map.Entry<String, String> entry : entries) {
            String ip = getIpFromCloudInitConfig(entry.getValue());
            if (StringUtils.isNotBlank(ip) && deleteIpSet.contains(ip)) {
                continue;
            }
            newIpConfig.put(String.valueOf(index), entry.getValue());
            index++;
        }
        return newIpConfig;
    }

    private Set<String> getRemovedIpSet(HashMap<String, String> oldIpConfig, HashMap<String, String> newIpConfig) {
        Set<String> removedIpSet = new LinkedHashSet<>(CloudInitNetworkUtil.getIpList(oldIpConfig));
        removedIpSet.removeAll(CloudInitNetworkUtil.getIpList(newIpConfig));
        return removedIpSet;
    }

    private int getIpConfigIndex(String key) {
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private void releaseDeletedIps(Set<String> deleteIpSet, Vmhost vmhost) {
        for (String ip : deleteIpSet) {
            Ippool ippool = getIppoolByIpAndNodeId(ip, vmhost.getNodeid());
            if (ippool == null) {
                continue;
            }
            if (Objects.equals(ippool.getStatus(), 1) && !Objects.equals(ippool.getVmId(), vmhost.getVmid())) {
                throw new IllegalStateException("IP已绑定到其他虚拟机，无法释放: ip=" + ip);
            }
            releaseIppool(ippool);
        }
    }

    private int getAddIpCount(VmIpParams vmIpParams) {
        List<String> requestIpList = getRequestIpList(vmIpParams);
        if (!requestIpList.isEmpty()) {
            return requestIpList.size();
        }
        return vmIpParams.getCount() == null ? 1 : vmIpParams.getCount();
    }

    private List<String> getRequestIpList(VmIpParams vmIpParams) {
        Set<String> ipSet = new LinkedHashSet<>();
        if (vmIpParams.getIps() != null) {
            for (String ipItem : vmIpParams.getIps()) {
                addRequestIp(ipSet, ipItem);
            }
        }
        addRequestIp(ipSet, StringUtils.defaultIfBlank(vmIpParams.getNewIp(), vmIpParams.getIp()));
        return new ArrayList<>(ipSet);
    }

    private void addRequestIp(Set<String> ipSet, String ipItem) {
        if (StringUtils.isBlank(ipItem)) {
            return;
        }
        String[] ipItems = ipItem.split(",");
        for (String ip : ipItems) {
            if (StringUtils.isNotBlank(ip)) {
                ipSet.add(ip.trim());
            }
        }
    }

    private Ippool getOneFreeIppoolByNodeId(Integer nodeId, Integer poolId, Set<Integer> excludeIdSet, Set<String> excludeIpSet) {
        QueryWrapper<Ippool> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("node_id", nodeId);
        queryWrapper.eq("status", 0);
        if (poolId != null) {
            queryWrapper.eq("pool_id", poolId);
        }
        if (excludeIdSet != null && !excludeIdSet.isEmpty()) {
            queryWrapper.notIn("id", excludeIdSet);
        }
        if (excludeIpSet != null && !excludeIpSet.isEmpty()) {
            queryWrapper.notIn("ip", excludeIpSet);
        }
        queryWrapper.orderByAsc("id");
        queryWrapper.last("limit 1");
        return ippoolService.getOne(queryWrapper);
    }

    private boolean isAddIpAvailable(Ippool ippool, Vmhost vmhost) {
        if (!Objects.equals(ippool.getNodeId(), vmhost.getNodeid())) {
            return false;
        }
        if (Objects.equals(ippool.getStatus(), 0)) {
            return true;
        }
        return Objects.equals(ippool.getStatus(), 1) && Objects.equals(ippool.getVmId(), vmhost.getVmid());
    }

    private boolean bindOrReuseIp(Ippool ippool, Vmhost vmhost) {
        if (Objects.equals(ippool.getStatus(), 1) && Objects.equals(ippool.getVmId(), vmhost.getVmid())) {
            return true;
        }
        return bindNewIp(ippool, vmhost);
    }

    private boolean bindNewIp(Ippool newIppool, Vmhost vmhost) {
        UpdateWrapper<Ippool> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", newIppool.getId());
        updateWrapper.eq("status", 0);
        updateWrapper.set("status", 1);
        updateWrapper.set("vm_id", vmhost.getVmid());
        return ippoolService.update(updateWrapper);
    }

    private void releaseOldIp(Ippool oldIppool, Ippool newIppool) {
        if (oldIppool == null || Objects.equals(oldIppool.getId(), newIppool.getId())) {
            return;
        }
        UpdateWrapper<Ippool> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", oldIppool.getId());
        updateWrapper.set("status", 0);
        updateWrapper.set("vm_id", 0);
        if (!ippoolService.update(updateWrapper)) {
            throw new IllegalStateException("释放旧IP失败: ip=" + oldIppool.getIp());
        }
    }

    private void releaseIppool(Ippool ippool) {
        UpdateWrapper<Ippool> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", ippool.getId());
        updateWrapper.set("status", 0);
        updateWrapper.set("vm_id", 0);
        if (!ippoolService.update(updateWrapper)) {
            throw new IllegalStateException("释放IP失败: ip=" + ippool.getIp());
        }
    }

    private Ippool getIppoolByIpAndNodeId(String ip, Integer nodeId) {
        return getIppoolByIpAndNodeIdAndPoolId(ip, nodeId, null);
    }

    private List<Ippool> getBoundIppoolListByNodeIdAndVmId(Integer nodeId, Integer vmId) {
        if (nodeId == null || vmId == null) {
            return Collections.emptyList();
        }
        QueryWrapper<Ippool> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("node_id", nodeId);
        queryWrapper.eq("vm_id", vmId);
        queryWrapper.eq("status", 1);
        queryWrapper.orderByAsc("id");
        return ippoolService.list(queryWrapper);
    }

    private List<Vmhost> getVmhostListByNodeId(Integer nodeId) {
        if (nodeId == null) {
            return Collections.emptyList();
        }
        QueryWrapper<Vmhost> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("nodeid", nodeId);
        queryWrapper.eq("delete_state", 0);
        queryWrapper.orderByAsc("id");
        return this.list(queryWrapper);
    }

    private Ippool getIppoolByIpAndNodeIdAndPoolId(String ip, Integer nodeId, Integer poolId) {
        if (StringUtils.isBlank(ip) || nodeId == null) {
            return null;
        }
        QueryWrapper<Ippool> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("ip", ip.trim());
        queryWrapper.eq("node_id", nodeId);
        if (poolId != null) {
            queryWrapper.eq("pool_id", poolId);
        }
        queryWrapper.last("limit 1");
        return ippoolService.getOne(queryWrapper);
    }

    private String getIpFromCloudInitConfig(String ipConfig) {
        return CloudInitNetworkUtil.getIpFromCloudInitConfig(ipConfig);
    }

    private void updateVmhostIpFields(Vmhost vmhost, HashMap<String, String> ipConfig, List<String> ipList, String errorMessage) {
        List<IpDto> ipData = VmUtil.splitIpAddress(ipConfig);
        Vmhost updateVmhost = new Vmhost();
        updateVmhost.setId(vmhost.getId());
        updateVmhost.setIpConfig(ipConfig);
        updateVmhost.setIpList(ipList);
        updateVmhost.setIpData(ipData);
        if (!this.updateById(updateVmhost)) {
            throw new IllegalStateException(errorMessage + ": hostId=" + vmhost.getId());
        }
        vmhost.setIpConfig(ipConfig);
        vmhost.setIpList(ipList);
        vmhost.setIpData(ipData);
    }

    private boolean updateVmhostStatusOnly(Integer hostId, Integer status) {
        Vmhost updateVmhost = new Vmhost();
        updateVmhost.setId(hostId);
        updateVmhost.setStatus(status);
        return this.updateById(updateVmhost);
    }

    private void updateVmhostStatusOnly(Vmhost vmhost, Integer status) {
        if (!updateVmhostStatusOnly(vmhost.getId(), status)) {
            throw new IllegalStateException("更新虚拟机状态失败: hostId=" + vmhost.getId() + ", status=" + status);
        }
        vmhost.setStatus(status);
    }

    private Integer getIpMask(Ipstatus ipstatus, Ippool ippool) {
        if (ipstatus.getMask() != null) {
            return ipstatus.getMask();
        }
        return subnetMaskToPrefix(ippool.getSubnetMask());
    }

    private Integer subnetMaskToPrefix(String subnetMask) {
        if (StringUtils.isBlank(subnetMask)) {
            return null;
        }
        String[] items = subnetMask.split("\\.");
        if (items.length != 4) {
            return null;
        }
        int prefix = 0;
        try {
            for (String item : items) {
                int value = Integer.parseInt(item);
                prefix += Integer.bitCount(value);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return prefix;
    }

    private String buildIpConfigValue(Ippool ippool, Ipstatus ipstatus) {
        Integer mask = getIpMask(ipstatus, ippool);
        String gateway = StringUtils.defaultIfBlank(ippool.getGateway(), ipstatus.getGateway());
        if (mask == null || StringUtils.isBlank(gateway)) {
            return null;
        }
        return "ip=" + ippool.getIp() + "/" + mask + ",gw=" + gateway;
    }

    private boolean isVmhostIpUsed(Vmhost vmhost, HashMap<String, String> ipConfig, String oldIp, String newIp) {
        if (StringUtils.isBlank(newIp)) {
            return false;
        }
        Set<String> ipSet = getVmhostIpSet(vmhost, ipConfig);
        if (StringUtils.isNotBlank(oldIp)) {
            ipSet.remove(oldIp);
        }
        return ipSet.contains(newIp);
    }

    private Set<String> getVmhostIpSet(Vmhost vmhost, HashMap<String, String> ipConfig) {
        Set<String> ipSet = new LinkedHashSet<>();
        if (vmhost.getIpList() != null) {
            for (String ip : vmhost.getIpList()) {
                if (StringUtils.isNotBlank(ip)) {
                    ipSet.add(ip.trim());
                }
            }
        }
        ipSet.addAll(CloudInitNetworkUtil.getIpList(ipConfig));
        return ipSet;
    }

    private int getNextIpConfigIndex(HashMap<String, String> ipConfig) {
        int nextIndex = 1;
        for (String key : ipConfig.keySet()) {
            try {
                nextIndex = Math.max(nextIndex, Integer.parseInt(key) + 1);
            } catch (NumberFormatException ignored) {
            }
        }
        return nextIndex;
    }

    private List<String> buildVmhostIpList(Vmhost vmhost, HashMap<String, String> ipConfig) {
        Set<String> ipSet = getVmhostIpSet(vmhost, ipConfig);
        return new ArrayList<>(ipSet);
    }

    private List<String> buildVmhostIpListAfterDelete(Vmhost vmhost, HashMap<String, String> ipConfig, Set<String> deleteIpSet) {
        Set<String> ipSet = getVmhostIpSet(vmhost, ipConfig);
        ipSet.removeAll(deleteIpSet);
        return new ArrayList<>(ipSet);
    }

    private List<String> replaceVmhostIpList(Vmhost vmhost, HashMap<String, String> ipConfig, String oldIp, String newIp) {
        Set<String> ipSet = getVmhostIpSet(vmhost, ipConfig);
        if (StringUtils.isNotBlank(oldIp)) {
            ipSet.remove(oldIp);
        }
        if (StringUtils.isNotBlank(newIp)) {
            ipSet.add(newIp);
        }
        return new ArrayList<>(ipSet);
    }

    private List<String> getNameserversByIpConfig(Integer nodeId, HashMap<String, String> ipConfig, List<Ippool> fallbackIppoolList) {
        Set<String> nameserverSet = new LinkedHashSet<>();
        if (fallbackIppoolList != null) {
            for (Ippool ippool : fallbackIppoolList) {
                addIppoolNameservers(nameserverSet, ippool);
            }
        }
        for (String ip : CloudInitNetworkUtil.getIpList(ipConfig)) {
            Ippool ippool = getIppoolByIpAndNodeId(ip, nodeId);
            addIppoolNameservers(nameserverSet, ippool);
        }
        return CloudInitNetworkUtil.distinctNameservers(new ArrayList<>(nameserverSet));
    }

    private void addIppoolNameservers(Set<String> nameserverSet, Ippool ippool) {
        if (ippool == null) {
            return;
        }
        addNameserver(nameserverSet, ippool.getDns1());
        addNameserver(nameserverSet, ippool.getDns2());
        Ipstatus ipstatus = ipstatusService.getById(ippool.getPoolId());
        if (ipstatus != null) {
            addNameserver(nameserverSet, ipstatus.getDns1());
            addNameserver(nameserverSet, ipstatus.getDns2());
        }
    }

    private void addNameserver(Set<String> nameserverSet, String nameserver) {
        if (StringUtils.isNotBlank(nameserver)) {
            nameserverSet.add(nameserver.trim());
        }
    }

    private void syncSingleNicCloudInitNetwork(Vmhost vmhost, Master node, List<String> nameservers) {
        HashMap<String, String> ipConfig = new HashMap<>();
        if (vmhost.getIpConfig() != null) {
            ipConfig.putAll(vmhost.getIpConfig());
        }
        String primaryIpConfig = CloudInitNetworkUtil.getPrimaryIpConfig(ipConfig);
        if (StringUtils.isBlank(primaryIpConfig)) {
            throw new IllegalStateException("虚拟机IP配置为空: hostId=" + vmhost.getId());
        }
        HashMap<String, String> cookieMap = masterService.getMasterCookieMap(vmhost.getNodeid());
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        JSONObject pveVmConfig = getPveVmConfig(proxmoxApiUtil, node, cookieMap, vmhost);
        String net0Config = pveVmConfig == null ? vmhost.getNet0() : pveVmConfig.getString("net0");
        String macAddress = CloudInitNetworkUtil.extractMacAddress(net0Config);
        try {
            CloudInitNetworkUtil.uploadSingleNicNetworkSnippet(node, vmhost.getVmid(), ipConfig, nameservers, macAddress);
        } catch (Exception e) {
            throw new IllegalStateException("写入cloud-init单网卡多IP配置失败: vmid=" + vmhost.getVmid(), e);
        }
        proxmoxApiUtil.resetVmConfig(node, cookieMap, vmhost.getVmid(), "ipconfig0", primaryIpConfig);
        proxmoxApiUtil.resetVmConfig(node, cookieMap, vmhost.getVmid(), "cicustom", mergeCicustomNetwork(pveVmConfig, vmhost.getVmid()));
        if (nameservers != null && !nameservers.isEmpty()) {
            proxmoxApiUtil.resetVmConfig(node, cookieMap, vmhost.getVmid(), "nameserver", String.join(" ", nameservers));
        }
        proxmoxApiUtil.resetVmCloudinit(node, cookieMap, vmhost.getVmid());
    }

    /**
     * @Author: 星禾
     * @Description: IP变更后创建异步强制重启任务
     * @DateTime: 2026/6/6 12:40
     */
    private void restartVmAfterIpChange(Vmhost vmhost, Master node) {
        Task pendingTask = getPendingIpChangeRestartTask(vmhost.getId());
        if (pendingTask != null) {
            log.info("[Task-IpChangeRestart] 已存在待执行任务，跳过重复创建: NodeId={}, VmId={}, HostId={}, TaskId={}",
                    node.getId(), vmhost.getVmid(), vmhost.getId(), pendingTask.getId());
            return;
        }
        Task restartTask = new Task();
        restartTask.setNodeid(node.getId());
        restartTask.setVmid(vmhost.getVmid());
        restartTask.setHostid(vmhost.getId());
        restartTask.setType(IP_CHANGE_RESTART_VM);
        restartTask.setStatus(0);
        Map<Object, Object> params = new HashMap<>();
        params.put("source", "vm_ip_change");
        restartTask.setParams(params);
        restartTask.setCreateDate(System.currentTimeMillis());
        if (!taskService.insertTask(restartTask)) {
            throw new IllegalStateException("创建IP变更异步重启任务失败: hostId=" + vmhost.getId());
        }
        if (!this.addVmHostTask(vmhost.getId(), restartTask.getId())) {
            log.warn("[Task-IpChangeRestart] 追加虚拟机任务流程失败: NodeId={}, VmId={}, HostId={}, TaskId={}",
                    node.getId(), vmhost.getVmid(), vmhost.getId(), restartTask.getId());
        }
        log.info("[Task-IpChangeRestart] IP变更异步重启任务创建成功: NodeId={}, VmId={}, HostId={}, TaskId={}",
                node.getId(), vmhost.getVmid(), vmhost.getId(), restartTask.getId());
    }

    private Task getPendingIpChangeRestartTask(Integer hostId) {
        if (hostId == null) {
            return null;
        }
        QueryWrapper<Task> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("hostid", hostId);
        queryWrapper.eq("type", IP_CHANGE_RESTART_VM);
        queryWrapper.in("status", Arrays.asList(0, 1));
        queryWrapper.orderByAsc("create_date");
        queryWrapper.last("LIMIT 1");
        return taskService.getOne(queryWrapper);
    }

    private void waitPveVmStopped(ProxmoxApiUtil proxmoxApiUtil, Master node, HashMap<String, String> cookieMap, Integer vmid) {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime <= IP_CHANGE_RESTART_TIMEOUT) {
            String status = getPveVmStatus(proxmoxApiUtil, node, cookieMap, vmid);
            if ("stopped".equals(status)) {
                return;
            }
            try {
                Thread.sleep(IP_CHANGE_RESTART_WAIT);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待虚拟机停止被中断: vmid=" + vmid, e);
            }
        }
        throw new IllegalStateException("等待虚拟机强制停止超时: vmid=" + vmid);
    }

    private String getPveVmStatus(ProxmoxApiUtil proxmoxApiUtil, Master node, HashMap<String, String> cookieMap, Integer vmid) {
        JSONObject result = proxmoxApiUtil.getVmStatus(node, cookieMap, vmid);
        if (result == null || result.getJSONObject("data") == null) {
            throw new IllegalStateException("获取虚拟机状态失败: vmid=" + vmid);
        }
        String status = result.getJSONObject("data").getString("status");
        if (StringUtils.isBlank(status)) {
            throw new IllegalStateException("虚拟机状态为空: vmid=" + vmid);
        }
        return status;
    }

    private JSONObject getPveVmConfig(ProxmoxApiUtil proxmoxApiUtil, Master node, HashMap<String, String> cookieMap, Vmhost vmhost) {
        JSONObject result = proxmoxApiUtil.getNodeApi(node, cookieMap, "/nodes/" + node.getNodeName() + "/qemu/" + vmhost.getVmid() + "/config", new HashMap<>());
        if (result == null) {
            return null;
        }
        return result.getJSONObject("data");
    }

    private String mergeCicustomNetwork(JSONObject pveVmConfig, Integer vmid) {
        String networkVolume = CloudInitNetworkUtil.getNetworkSnippetVolume(vmid);
        if (pveVmConfig == null || StringUtils.isBlank(pveVmConfig.getString("cicustom"))) {
            return "network=" + networkVolume;
        }
        String cicustom = pveVmConfig.getString("cicustom");
        List<String> items = new ArrayList<>();
        boolean hasNetwork = false;
        for (String item : cicustom.split(",")) {
            String value = item.trim();
            if (StringUtils.isBlank(value)) {
                continue;
            }
            if (value.startsWith("network=")) {
                items.add("network=" + networkVolume);
                hasNetwork = true;
            } else {
                items.add(value);
            }
        }
        if (!hasNetwork) {
            items.add("network=" + networkVolume);
        }
        return String.join(",", items);
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
//        if (vmhost == null){
//            // 将vmHostId作为为vmid
//            vmhost = this.getVmhostByVmId(Math.toIntExact(vmHostId));
//        }
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
            this.power(vmhost.getId(), "shutdown",null);
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
//        if (vmhost == null){
//            // 将vmHostId作为为vmid
//            vmhost = this.getVmhostByVmId(Math.toIntExact(renewalParams.getHostId()));
//        }
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

    /**
    * @Author: mryunqi
    * @Description: 更新虚拟机OS数据
    * @DateTime: 2023/11/18 18:29
    * @Params: vmId 虚拟机id，os 虚拟机OS数据
     * @Return void 无返回值
    */
    @Override
    public void updateVmhostOsData(long vmId, Os os) {
        // 获取虚拟机信息
        Vmhost vmhost = this.getById(vmId);
        // 判空
        if (vmhost == null){
            return;
        }
        // 更新虚拟机OS数据
        vmhost.setOsName(os.getFileName());
        vmhost.setOsType(os.getType());
        vmhost.setOs(os.getName());

        // 更新虚拟机信息
        if (this.updateById(vmhost)){
            UnifiedLogger.log(UnifiedLogger.LogType.VMHOST_UPDATE_OS,"更新虚拟机OS数据成功，虚拟机id为：" + vmId);
        }else{
            UnifiedLogger.log(UnifiedLogger.LogType.VMHOST_UPDATE_OS,"更新虚拟机OS数据失败，虚拟机id为：" + vmId);
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 分页获取指定状态的虚拟机
    * @DateTime: 2023/11/26 21:58
    * @Params: page 当前页，size 每页显示数量，status 虚拟机状态
    * @Return Page<Vmhost> 分页虚拟机数据
    */
    @Override
    public Page<Vmhost> getVmhostByStatus(Long page, Long size, int status) {
        QueryWrapper<Vmhost> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status",status);
        return this.selectPage(Math.toIntExact(page), Math.toIntExact(size),queryWrapper);
    }

    /**
    * @Author: mryunqi
    * @Description: 带参数获取虚拟机数量
    * @DateTime: 2023/11/26 22:06
    * @Params: QueryWrapper<Vmhost> queryWrapper 查询条件
    * @Return Long 虚拟机数量
    */
    @Override
    public Long selectCount(QueryWrapper<Vmhost> queryWrapper) {
        // 获取所有符合条件的虚拟机
        List<Vmhost> vmhosts = this.list(queryWrapper);
        // 返回虚拟机数量
        return (long) vmhosts.size();
    }

    /**
    * @Author: mryunqi
    * @Description: 获取指定状态的虚拟机数量
    * @DateTime: 2023/11/26 22:05
    * @Params: status 虚拟机状态
    * @Return Long 虚拟机数量
    */
    @Override
    public Long getVmhostCountByStatus(int status) {
        QueryWrapper<Vmhost> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status",status);
        return this.selectCount(queryWrapper);
    }
    /**
     * @Author: 星禾
     * @Description: 添加NAT规则
     * @DateTime: 2024/12/30 17:00
     * @Params: vm 虚拟机数据库ID, source_port宿主机端口, destination_ip虚拟机IP destination_port虚拟机端口, protocol协议
     */
    @Override
    public Boolean addVmhostNat(String source_ip, int source_port, String destination_ip, int destination_port, String protocol , int vm) {
        String token = configService.getToken();
        Master node = masterService.getById(this.getVmhostNodeId(vm));
        Vmhost vmhost = this.getById(vm);
        if(Objects.equals(source_port, node.getPort()) || Objects.equals(source_port, node.getControllerPort()) || node.getNaton() == 0
                || source_port < 1000 || source_port > 65535 || Objects.equals(source_port, node.getSshPort()) || source_port == 3128
                || source_port == 5404 || source_port == 5405 || (source_port >= 5900 && source_port < 6000)
                || (source_port >= 59000 && source_port <= 60050) || source_port == 6080
        ) {return false;} //端口不符合要求和节点是否开启NAT 否则直接返回，缺点是没有写错误提示
        //上述禁用端口说明：禁止<1000与>65535端口，禁止web端口、控制端口、ssh端口、禁止未开启NAT的机器使用该功能
        //corosync集群流量 5404 5405 UDP、实时迁移：60000-60050 TCP、SPICE代理：3128 TCP、vnc：59000-59999，6080 TCP/WEBSOCKET
        Object natForward = this.getVmhostNatByVmid(1, 10000, vm);
        if (natForward instanceof ResponseResult) {
            ResponseResult result = (ResponseResult) natForward;
            List<?> dataList = (List<?>) result.getData();
            int dataCount = dataList.size();
            if (dataCount >= vmhost.getNatnum()) {
                return false;
            }
        }
        return ClientApiUtil.addPortForward(node.getHost(), token, node.getControllerPort(), vm, source_port, destination_ip, destination_port, protocol);
    }
    /**
     * @Author: 星禾
     * @Description: 删除NAT规则
     * @DateTime: 2024/12/30 17:15
     * @Params: vm 虚拟机数据库ID, source_port宿主机端口, destination_ip虚拟机IP destination_port虚拟机端口, protocol协议
     */
    @Override
    public Boolean delVmhostNat(String source_ip, int source_port, String destination_ip, int destination_port, String protocol , int vm) {
        String token = configService.getToken();
        Master node = masterService.getById(this.getVmhostNodeId(vm));
        return ClientApiUtil.deletePortForward(node.getHost(), token, node.getControllerPort(), vm, source_port, destination_ip, destination_port, protocol);
    }
    /**
     * @Author: 星禾
     * @Description: 获取指定虚拟机ID的节点ID
     * @DateTime: 2024/12/29 21:00
     * @Params: hostId 虚拟机id
     */
    @Override
    public Object getVmhostNatByVmid (int page, int size, int hostId) {
        String token = configService.getToken();
        Master node = masterService.getById(this.getVmhostNodeId(hostId));
        JSONObject data = ClientApiUtil.getPortForwardList(node.getHost(), token, node.getControllerPort(), hostId, page, size);
        if (data != null) {
            // 检查返回的代码，如果成功，则返回数据
            int code = data.getIntValue("code");
            if (code == 200) {
                JSONArray dataArray = data.getJSONArray("data");
                if (dataArray != null) {
                    return ResponseResult.ok(dataArray);
                } else {
                    return ResponseResult.fail("Data array is null");
                }
            } else {
                String message = data.getString("message");
                return ResponseResult.fail(message != null ? message : "Unknown error");
            }
        } else {
            return ResponseResult.fail("Failed to retrieve port forward list");
        }
    }
    /**
     * @Author: 星禾
     * @Description: 获取指定虚拟机ID的节点ID
     * @DateTime: 2024/12/29 21:00
     * @Params: hostId 虚拟机id
     */
    @Override
    public Integer getVmhostNodeId(int hostId) {
        QueryWrapper<Vmhost> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id",hostId);
        Vmhost vmhost = this.getOne(queryWrapper);
        return vmhost.getNodeid();
    }
    /**
     * @Author: 星禾
     * @Description: 获取指定虚拟机ID的节点ID
     * @DateTime: 2024/12/29 21:00
     * @Params: hostId 虚拟机id
     */
    @Override
    public Object getVmhostNatAddrByVmid (int hostId) {
        Master node = masterService.getById(this.getVmhostNodeId(hostId));
        String nataddr = node.getNataddr();
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("natAddr", nataddr);
        return ResponseResult.ok(resultData);
    }
    /**
     * @Author: 星禾
     * @Description: 重置虚拟机流量
     * @DateTime: 2025/11/21 21:00
     * @Params: hostId 虚拟机id
     */
    @Override
    public Boolean resetVmHostFlow (int hostId) {
        Vmhost vmhost = this.getById(hostId);
        vmhost.setUsedFlow(0.0);
        return this.updateById(vmhost);
    }
    /**
     * @Author: 星禾
     * @Description: 重置虚拟机状态
     * @DateTime: 2025/12/05 16:16
     * @Params: hostId 虚拟机id
     */
    @Override
    public Boolean resetVmHostStatus (int hostId) {
        Vmhost vmhost = this.getById(hostId);
        vmhost.setStatus(0);
        return this.updateById(vmhost);
    }
    /**
     * @Author: 星禾
     * @Description: 虚拟添加机流量包
     * @DateTime: 2025/11/22 20:20
     * @Params: hostId 虚拟机id flow 流量 单位G
     */
    @Override
    public Boolean addVmHostFlow (int hostId, Long flow) {
        Vmhost vmhost = this.getById(hostId);
        Long oldFlow = vmhost.getExtraFlowLimit();
        Long newFlow = flow * 1024 * 1024 * 1024 + oldFlow;
        vmhost.setExtraFlowLimit(newFlow);
        return this.updateById(vmhost);
    }
    /**
     * @Author: 星禾
     * @Description: 修改虚拟机带宽
     * @DateTime: 2025/11/26 22:20
     * @Params: vmhost bandwidth
     */
    @Override
    public Boolean changeVmHostBandWidth(Vmhost vmhost, String bandwidth){
        Master node = masterService.getById(vmhost.getNodeid());
        // 获取cookie
        HashMap<String, String> cookieMap = masterService.getMasterCookieMap(vmhost.getNodeid());
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        proxmoxApiUtil.resetVmConfig(node, cookieMap, vmhost.getVmid(), "net0", "virtio,bridge=" + vmhost.getBridge() + ",rate=" + bandwidth);
        return true;
    }
    /**
     * @Author: 星禾
     * @Description: 获取虚拟机快照列表
     * @DateTime: 2026/5/24 18:20
     * @Params: vmhost bandwidth
     */
    @Override
    public JSONObject getVmSnapShot(Vmhost vmhost){
        Master node = masterService.getById(vmhost.getNodeid());
        // 获取cookie
        HashMap<String, String> cookieMap = masterService.getMasterCookieMap(vmhost.getNodeid());
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        JSONObject snapshotJson = proxmoxApiUtil.getVmSnapShot(node,cookieMap,vmhost.getVmid());
        appendPendingSnapshotTasks(snapshotJson, proxmoxApiUtil.getVmActiveTasks(node, cookieMap, vmhost.getVmid()), vmhost);
        return snapshotJson;
    }

    /**
     * @Author: 星禾
     * @Description: 创建指定虚拟机快照
     * @DateTime: 2026/5/29 23:13
     */
    @Override
    public boolean addVmSnapShot(Vmhost vmhost, String snapName, Boolean vmstate, String description) {
        Master node = masterService.getById(vmhost.getNodeid());
        // 获取cookie
        HashMap<String, String> cookieMap = masterService.getMasterCookieMap(vmhost.getNodeid());
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        if (hasPendingSnapshotOrBackupTask(proxmoxApiUtil.getVmActiveTasks(node, cookieMap, vmhost.getVmid()), vmhost.getVmid())) {
            throw new IllegalStateException("该虚拟机存在创建中的快照或备份，无法重复创建");
        }
        proxmoxApiUtil.addVmSnapShot(node,cookieMap,vmhost.getVmid(),snapName,vmstate,description);
        return true;
    }

    /**
     * @Author: 星禾
     * @Description: 删除指定虚拟机快照
     * @DateTime: 2026/5/29 23:13
     */
    @Override
    public boolean deleteVmSnapShot(Vmhost vmhost, String snapName) {
        Master node = masterService.getById(vmhost.getNodeid());
        // 获取cookie
        HashMap<String, String> cookieMap = masterService.getMasterCookieMap(vmhost.getNodeid());
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        proxmoxApiUtil.deleteVmSnapShot(node,cookieMap,vmhost.getVmid(),snapName);
        return true;
    }

    /**
     * @Author: 星禾
     * @Description: 回滚指定虚拟机快照
     * @DateTime: 2026/5/29 23:13
     */
    @Override
    public boolean rollbackVmSnapShot(Vmhost vmhost, String snapName) {
        Master node = masterService.getById(vmhost.getNodeid());
        // 获取cookie
        HashMap<String, String> cookieMap = masterService.getMasterCookieMap(vmhost.getNodeid());
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        proxmoxApiUtil.rollbackVmSnapShot(node,cookieMap,vmhost.getVmid(),snapName);
        return true;
    }
    /**
     * @Author: 星禾
     * @Description: 获取指定虚拟机备份列表
     * @DateTime: 2026/5/29 23:03
     */
    @Override
    public JSONObject getVmBackup(Vmhost vmhost) {
        Master node = masterService.getById(vmhost.getNodeid());
        if (StringUtils.isBlank(node.getBackupStorage())) {
            throw new IllegalArgumentException("节点备份存储未配置");
        }
        HashMap<String, String> cookieMap = masterService.getMasterCookieMap(vmhost.getNodeid());
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        JSONObject backupJson = proxmoxApiUtil.getVmBackup(node, cookieMap, vmhost.getVmid(), node.getBackupStorage());
        appendPendingBackupTasks(backupJson, proxmoxApiUtil.getVmActiveTasks(node, cookieMap, vmhost.getVmid()), vmhost, node.getBackupStorage());
        return backupJson;
    }

    /**
     * @Author: 星禾
     * @Description: 创建指定虚拟机备份
     * @DateTime: 2026/5/29 23:03
     */
    @Override
    public JSONObject addVmBackup(Vmhost vmhost, String mode, String compress, String notes) {
        Master node = masterService.getById(vmhost.getNodeid());
        if (StringUtils.isBlank(node.getBackupStorage())) {
            throw new IllegalArgumentException("节点备份存储未配置");
        }
        HashMap<String, String> cookieMap = masterService.getMasterCookieMap(vmhost.getNodeid());
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        if (hasPendingSnapshotOrBackupTask(proxmoxApiUtil.getVmActiveTasks(node, cookieMap, vmhost.getVmid()), vmhost.getVmid())) {
            throw new IllegalStateException("该虚拟机存在创建中的快照或备份，无法重复创建");
        }
        return proxmoxApiUtil.addVmBackup(node, cookieMap, vmhost.getVmid(), node.getBackupStorage(), mode, compress, notes);
    }

    private void appendPendingSnapshotTasks(JSONObject snapshotJson, JSONObject tasksJson, Vmhost vmhost) {
        JSONArray snapshots = ensureDataArray(snapshotJson);
        for (JSONObject task : getPendingVmTasks(tasksJson, vmhost.getVmid())) {
            if (!"qmsnapshot".equals(task.getString("type"))) {
                continue;
            }
            JSONObject snapshot = new JSONObject();
            snapshot.put("name", task.getString("id"));
            snapshot.put("snapname", task.getString("id"));
            snapshot.put("description", "快照创建中");
            snapshot.put("status", PENDING_STATUS);
            snapshot.put("running", true);
            snapshot.put("upid", task.getString("upid"));
            snapshot.put("starttime", task.getLong("starttime"));
            snapshot.put("source", "task");
            snapshots.add(snapshot);
        }
    }

    private void appendPendingBackupTasks(JSONObject backupJson, JSONObject tasksJson, Vmhost vmhost, String storage) {
        JSONArray backups = ensureDataArray(backupJson);
        for (JSONObject task : getPendingVmTasks(tasksJson, vmhost.getVmid())) {
            if (!"vzdump".equals(task.getString("type"))) {
                continue;
            }
            JSONObject backup = new JSONObject();
            backup.put("volid", task.getString("id"));
            backup.put("filename", task.getString("id"));
            backup.put("notes", "备份创建中");
            backup.put("storage", storage);
            backup.put("vmid", vmhost.getVmid());
            backup.put("status", PENDING_STATUS);
            backup.put("running", true);
            backup.put("upid", task.getString("upid"));
            backup.put("starttime", task.getLong("starttime"));
            backup.put("source", "task");
            backups.add(backup);
        }
    }

    private boolean hasPendingSnapshotOrBackupTask(JSONObject tasksJson, Integer vmid) {
        for (JSONObject task : getPendingVmTasks(tasksJson, vmid)) {
            String type = task.getString("type");
            if ("qmsnapshot".equals(type) || "vzdump".equals(type)) {
                return true;
            }
        }
        return false;
    }

    private List<JSONObject> getPendingVmTasks(JSONObject tasksJson, Integer vmid) {
        JSONArray tasks = tasksJson == null ? null : tasksJson.getJSONArray("data");
        if (tasks == null || tasks.isEmpty()) {
            return Collections.emptyList();
        }

        List<JSONObject> pendingTasks = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            JSONObject task = tasks.getJSONObject(i);
            if (task == null || !isPendingTask(task) || !isSameVmTask(task, vmid)) {
                continue;
            }
            pendingTasks.add(task);
        }
        return pendingTasks;
    }

    private boolean isPendingTask(JSONObject task) {
        String status = task.getString("status");
        return StringUtils.isBlank(status) || "running".equalsIgnoreCase(status);
    }

    private boolean isSameVmTask(JSONObject task, Integer vmid) {
        if (vmid == null) {
            return false;
        }
        Integer taskVmid = task.getInteger("vmid");
        if (vmid.equals(taskVmid)) {
            return true;
        }
        String taskId = task.getString("id");
        if (String.valueOf(vmid).equals(taskId)) {
            return true;
        }
        String vmidText = String.valueOf(vmid);
        String upid = task.getString("upid");
        return (taskId != null && taskId.contains(vmidText)) || (upid != null && upid.contains(":qemu:" + vmidText + ":"));
    }

    private JSONArray ensureDataArray(JSONObject json) {
        if (json == null) {
            throw new IllegalStateException("获取列表失败");
        }
        JSONArray data = json.getJSONArray("data");
        if (data == null) {
            data = new JSONArray();
            json.put("data", data);
        }
        return data;
    }

    /**
     * @Author: 星禾
     * @Description: 删除指定虚拟机备份
     * @DateTime: 2026/5/29 23:03
     */
    @Override
    public JSONObject deleteVmBackup(Vmhost vmhost, String volid) {
        Master node = masterService.getById(vmhost.getNodeid());
        HashMap<String, String> cookieMap = masterService.getMasterCookieMap(vmhost.getNodeid());
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        return proxmoxApiUtil.deleteVmBackup(node, cookieMap, volid);
    }

    /**
     * @Author: 星禾
     * @Description: 还原指定虚拟机备份
     * @DateTime: 2026/5/29 23:03
     */
    @Override
    public JSONObject rollbackVmBackup(Vmhost vmhost, String volid, Boolean force, Boolean start) {
        Master node = masterService.getById(vmhost.getNodeid());
        HashMap<String, String> cookieMap = masterService.getMasterCookieMap(vmhost.getNodeid());
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        forceStopVmBeforeRollbackBackup(vmhost, node, cookieMap, proxmoxApiUtil);
        return proxmoxApiUtil.rollbackVmBackup(node, cookieMap, vmhost.getVmid(), volid, force == null ? true : force, start);
    }

    /**
     * @Author: 星禾
     * @Description: 还原备份前强制关闭虚拟机
     * @DateTime: 2026/5/29 23:13
     */
    private void forceStopVmBeforeRollbackBackup(Vmhost vmhost, Master node, HashMap<String, String> cookieMap,
                                                 ProxmoxApiUtil proxmoxApiUtil) {
        String status = getVmPowerStatus(node, cookieMap, proxmoxApiUtil, vmhost.getVmid());
        if ("stopped".equals(status)) {
            return;
        }

        log.info("[VmBackupRestore] 还原备份前强制关机: NodeID:{} VM-ID:{} currentStatus:{}",
                node.getId(), vmhost.getVmid(), status);
        proxmoxApiUtil.forceStopVm(node, cookieMap, vmhost.getVmid());
        updateVmhostStatusOnly(vmhost, 9);
        waitVmStoppedBeforeRollbackBackup(vmhost, node, cookieMap, proxmoxApiUtil);
        updateVmhostStatusOnly(vmhost, 1);
    }

    /**
     * @Author: 星禾
     * @Description: 等待虚拟机关机完成后继续还原备份
     * @DateTime: 2026/5/29 23:13
     */
    private void waitVmStoppedBeforeRollbackBackup(Vmhost vmhost, Master node, HashMap<String, String> cookieMap,
                                                   ProxmoxApiUtil proxmoxApiUtil) {
        long endTime = System.currentTimeMillis() + BACKUP_RESTORE_SHUTDOWN_TIMEOUT;
        while (System.currentTimeMillis() <= endTime) {
            String status = getVmPowerStatus(node, cookieMap, proxmoxApiUtil, vmhost.getVmid());
            if ("stopped".equals(status)) {
                return;
            }
            sleepBeforeNextVmStatusCheck();
        }
        throw new IllegalStateException("还原备份前强制关机超时");
    }

    /**
     * @Author: 星禾
     * @Description: 获取虚拟机实时电源状态
     * @DateTime: 2026/5/29 23:13
     */
    private String getVmPowerStatus(Master node, HashMap<String, String> cookieMap, ProxmoxApiUtil proxmoxApiUtil,
                                    Integer vmid) {
        JSONObject current = proxmoxApiUtil.getVmStatus(node, cookieMap, vmid);
        if (current == null) {
            throw new IllegalStateException("还原备份前获取虚拟机状态失败");
        }

        JSONObject data = current.getJSONObject("data");
        if (data == null) {
            data = current;
        }
        String status = data.getString("status");
        if (StringUtils.isBlank(status)) {
            throw new IllegalStateException("还原备份前虚拟机状态为空");
        }
        return status;
    }

    /**
     * @Author: 星禾
     * @Description: 等待下一次虚拟机状态检查
     * @DateTime: 2026/5/29 23:13
     */
    private void sleepBeforeNextVmStatusCheck() {
        try {
            Thread.sleep(BACKUP_RESTORE_SHUTDOWN_WAIT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待虚拟机关机时被中断，备份还原已取消", e);
        }
    }
}

