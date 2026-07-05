package com.chuqiyun.proxmoxveams.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.common.UnifiedLogger;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.dto.VncInfoDto;
import com.chuqiyun.proxmoxveams.entity.*;
import com.chuqiyun.proxmoxveams.service.*;
import com.chuqiyun.proxmoxveams.utils.ClientApiUtil;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * @author mryunqi
 * @date 2023/11/24
 */
@Service("vncService")
public class VncServiceImpl implements VncService {
    @Resource
    private VncnodeService vncnodeService;
    @Resource
    private VncinfoService vncinfoService;
    @Resource
    private VncdataService vncdataService;
    @Resource
    private VmhostService vmhostService;
    @Resource
    private MasterService masterService;
    @Resource
    private ConfigService configService;

    /**
    * @Author: mryunqi
    * @Description: 获取虚拟机的VNC连接信息
    * @DateTime: 2023/11/24 16:43
    * @Params: Long hostId 虚拟机ID Integer page 当前页 Integer limit 每页条数
    * @Return UnifiedResultDto<Object> 统一返回结果
    */
    @Override
    public UnifiedResultDto<Object> getVncInfo(Long hostId, Integer page, Integer limit) {
        // 获取虚拟机信息
        Vmhost vmhost = vmhostService.getById(hostId);
        // 如果虚拟机不存在
//        if (vmhost == null){
//            // 将vmHostId作为为vmid
//            vmhost = vmhostService.getVmhostByVmId(Math.toIntExact(hostId));
//        }
        // 判空
        if (vmhost == null){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_VM_NOT_EXIST, null);
        }

        // 判断虚拟机是否为禁用状态
        if (vmhost.getStatus() == 4){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_VM_IS_DISABLED, null);
        }

        // 判断虚拟机是否为开机状态
        if (vmhost.getStatus() != 0){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_VM_IS_NOT_RUNNING, null);
        }
        Master node = masterService.getById(vmhost.getNodeid());
        // 判空
        if (node == null){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NODE_NOT_EXIST, null);
        }

        // 分页获取激活状态的VNC控制器节点
        QueryWrapper<Vncnode> vncnodeQueryWrapper = new QueryWrapper<>();
        vncnodeQueryWrapper.eq("status", 0);
        Page<Vncnode> vncnodePage = vncnodeService.selectVncnodePage(page, limit, vncnodeQueryWrapper);
        // 判空
        if (vncnodePage == null || vncnodePage.getRecords().size() == 0){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_VNC_NODE_NOT_EXIST, null);
        }

        // 获取虚拟机VNC连接信息
        Vncinfo vncinfo = vncinfoService.selectVncinfoByHostId(Long.valueOf(vmhost.getId()));
        // 判空
        if (vncinfo == null){
            // 创建虚拟机VNC连接信息
            vncinfo = new Vncinfo();
            vncinfo.setHostId(Long.valueOf(vmhost.getId()));
            vncinfo.setVmid(Long.valueOf(vmhost.getVmid()));
            vncinfo.setHost(node.getHost());
            vncinfo.setPort(this.calculateVncPort(node.getHost()));
            vncinfo.setUsername(vmhost.getHostname());
            vncinfo.setPassword(vmhost.getPassword());
            vncinfoService.addVncinfo(vncinfo);
        } else {
            refreshVncInfoIfNodeChanged(vncinfo, vmhost, node);
        }

        // 获取虚拟机VNC连接数据
        Vncdata vncdata = vncdataService.selectVncdataByVncinfoIdAndStatusOk(Long.valueOf(vncinfo.getId()));
        // 判空
        if (vncdata == null){
            int vncExpiryTime = configService.getVncExpire(); // 分钟
            String token = configService.getToken();
            long nowTime = System.currentTimeMillis();
            long expiryTime = nowTime + (long) vncExpiryTime * 60 * 1000;
            vncdata = new Vncdata();
            vncdata.setVncId(Long.valueOf(vncinfo.getId()));
            vncdata.setHostId(vmhost.getId());
            vncdata.setNodeId(node.getId());
            vncdata.setPort(vncinfo.getPort());
            vncdata.setStatus(0);
            vncdata.setCreateDate(nowTime);
            vncdata.setExpirationTime(expiryTime);
            vncdataService.addVncdata(vncdata);
            // 与被控通讯创建VNC连接
            Boolean consoleResult = ClientApiUtil.createVncService(node.getHost(),token,vmhost.getVmid(),vncinfo.getPort(),vncinfo.getUsername(),vncinfo.getPassword(),node.getControllerPort(),node.getHost(),vncExpiryTime);
            if (!consoleResult){
                return new UnifiedResultDto<>(UnifiedResultCode.ERROR_CREATE_VNC_CONNECTION, null);
            }
            this.syncVncInfo(vncinfo);
        }
        List<Object> vncInfoList = new ArrayList<>();
        HashMap<String,String> vncUrlMap = this.getVncUrlMap(vncinfo,vncnodePage);
        vncInfoList.add(vncUrlMap);
        VncInfoDto vncInfoDto = new VncInfoDto();
        vncInfoDto.setTotal(vncnodePage.getTotal());
        vncInfoDto.setCurrent(vncnodePage.getCurrent());
        vncInfoDto.setPages(vncnodePage.getPages());
        vncInfoDto.setSize(vncnodePage.getSize());
        vncInfoDto.setRecords(vncInfoList);
        return new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, vncInfoDto);
    }

    /**
    * @Author: mryunqi
    * @Description: 生成VNC连接url地址
    * @DateTime: 2023/11/24 20:57
    * @Params: [vncinfo, vncnodePage]
    * @Return
    */
    @Override
    public HashMap<String,String> getVncUrlMap(Vncinfo vncinfo, Page<Vncnode> vncnodePage) {
        HashMap<String,String> map = new HashMap<>();
        for (Vncnode vncnode : vncnodePage.getRecords()) {
            String pageHost = getVncPageHost(vncnode);
            String websocketHost = trimUrlHost(vncinfo.getHost());
            Integer websocketPort = vncinfo.getPort();

            String token = buildLegacyVncToken(websocketHost, websocketPort);
            String url = "https://" + pageHost + "/vnc.html"
                    + "?token=" + encodeUrlParam(token)
                    + "&encrypt=1"
                    + "&path=" + encodeUrlParam("websockify");
            map.put(vncnode.getName(),url);
        }
        return map;
    }

    private String getVncPageHost(Vncnode vncnode) {
        String host = trimUrlHost(vncnode.getHost());
        String domain = trimUrlHost(vncnode.getDomain());
        if (domain != null && !domain.equals(host)) {
            return domain;
        }
        return formatHostWithPort(host, 6080);
    }

    private String formatHostWithPort(String host, int port) {
        if (host == null) {
            return ":" + port;
        }
        if (host.contains(":") && !host.startsWith("[")) {
            return "[" + host + "]:" + port;
        }
        return host + ":" + port;
    }

    private String trimUrlHost(String value) {
        if (value == null) {
            return null;
        }
        String result = value.trim();
        if (result.startsWith("http://")) {
            result = result.substring("http://".length());
        } else if (result.startsWith("https://")) {
            result = result.substring("https://".length());
        }
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result.isEmpty() ? null : result;
    }

    private String encodeUrlParam(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String buildLegacyVncToken(String host, Integer port) {
        JSONObject tokenJson = new JSONObject();
        tokenJson.put("host", host);
        tokenJson.put("port", port);
        return Base64.getEncoder().encodeToString(tokenJson.toJSONString().getBytes(StandardCharsets.UTF_8));
    }

    private void refreshVncInfoIfNodeChanged(Vncinfo vncinfo, Vmhost vmhost, Master node) {
        String currentHost = trimUrlHost(node.getHost());
        String vncHost = trimUrlHost(vncinfo.getHost());
        if (currentHost == null || currentHost.equals(vncHost)) {
            return;
        }

        invalidateCurrentVncData(vncinfo, node);

        vncinfo.setHost(currentHost);
        vncinfo.setPort(this.calculateVncPort(currentHost));
        vncinfo.setVmid(Long.valueOf(vmhost.getVmid()));
        vncinfo.setUsername(vmhost.getHostname());
        vncinfo.setPassword(vmhost.getPassword());
        vncinfoService.updateVncinfo(vncinfo);
    }

    private void invalidateCurrentVncData(Vncinfo vncinfo, Master node) {
        Vncdata currentVncdata = vncdataService.selectVncdataByVncinfoIdAndStatusOk(Long.valueOf(vncinfo.getId()));
        if (currentVncdata == null) {
            return;
        }

        try {
            String token = configService.getToken();
            String oldHost = trimUrlHost(vncinfo.getHost());
            Integer oldPort = currentVncdata.getPort() == null ? vncinfo.getPort() : currentVncdata.getPort();
            if (oldHost != null && oldPort != null) {
                ClientApiUtil.stopVncService(oldHost, token, oldPort, node.getControllerPort());
            }
        } catch (Exception e) {
            UnifiedLogger.warn(UnifiedLogger.LogType.SYSTEM, "Stop old VNC service failed when node host changed: " + e.getMessage());
        }

        currentVncdata.setStatus(1);
        vncdataService.updateVncdata(currentVncdata);
    }

    /**
    * @Author: mryunqi
    * @Description: 所用VNC控制器同步VNC连接信息
    * @DateTime: 2023/11/24 20:39
    * @Params: vncinfo VNC连接信息
    * @Return void
    */
    @Override
    public void syncVncInfo(Vncinfo vncinfo) {
        // 分页获取激活状态的VNC控制器节点
        QueryWrapper<Vncnode> vncnodeQueryWrapper = new QueryWrapper<>();
        vncnodeQueryWrapper.eq("status", 0);
        String token = configService.getToken();
        int vncExpiryTime = configService.getVncExpire(); // 分钟
        int page = 1;
        int limit = 100;
        while (true){
            Page<Vncnode> vncnodePage = vncnodeService.selectVncnodePage(page, limit, vncnodeQueryWrapper);
            // 判空
            if (vncnodePage == null || vncnodePage.getRecords().size() == 0){
                break;
            }
            // 遍历同步VNC连接信息
            for (Vncnode vncnode : vncnodePage.getRecords()) {
                String vncNodeHost = trimUrlHost(vncnode.getHost());
                String vncInfoHost = trimUrlHost(vncinfo.getHost());
                // 判断是否为同一节点
                if (vncNodeHost == null || vncNodeHost.equals(vncInfoHost) || vncnode.getPort() == null){
                    continue;
                }
                // 与被控通讯同步VNC连接信息
                try {
                    Boolean consoleResult = ClientApiUtil.importVncService(vncNodeHost,token, Math.toIntExact(vncinfo.getVmid()),vncinfo.getPort(),vncinfo.getUsername(),vncinfo.getPassword(),vncnode.getPort(),vncinfo.getHost(),vncExpiryTime);
                    if (!consoleResult){
                        continue;
                    }
                } catch (Exception e) {
                    UnifiedLogger.warn(UnifiedLogger.LogType.SYSTEM, "Sync VNC info to node failed: " + vncNodeHost + ":" + vncnode.getPort() + ", " + e.getMessage());
                    continue;
                }
            }
            // 判断是否为最后一页
            if (vncnodePage.getPages() == page){
                break;
            }
            page++;
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 计算vnc端口
    * @DateTime: 2023/11/24 18:21
    * @Params: host 节点IP
    * @Return Integer 返回未被使用的端口
    */
    @Override
    public Integer calculateVncPort(String host) {
        // 获取所有指定host的VNC配置信息
        QueryWrapper<Vncinfo> vncinfoQueryWrapper = new QueryWrapper<>();
        vncinfoQueryWrapper.eq("host", host);
        int page = 1;
        int limit = 100;
        List<Integer> vncPorts = new ArrayList<>();
        while (true){
            Page<Vncinfo> vncinfoPage = vncinfoService.selectVncinfoPage(page, limit, vncinfoQueryWrapper);
            // 判空
            if (vncinfoPage == null || vncinfoPage.getRecords().size() == 0){
                break;
            }
            // 获取VNC端口
            for (Vncinfo vncinfo : vncinfoPage.getRecords()) {
                vncPorts.add(vncinfo.getPort());
            }
            page++;
        }
        // 将端口列表排序
        Collections.sort(vncPorts);
        // 返回未被使用的端口
        return findUnusedPort(vncPorts);
    }

    /**
    * @Author: mryunqi
    * @Description: 查找未被使用的端口
    * @DateTime: 2023/11/24 18:46
    * @Params: usedPorts 已被使用的端口列表
    * @Return  Integer 返回未被使用的端口
    */
    private int findUnusedPort(List<Integer> usedPorts) {
        int minPort = 59000; // 如果数据库中的端口为空，则从59000开始
        int maxPort = 65535; // 最大端口号

        for (int port : usedPorts) {
            if (port > minPort) {
                // 找到第一个未被使用的端口
                return minPort;
            }
            minPort = port + 1;
        }

        // 如果列表中的端口都被使用，返回下一个可用的端口
        return (minPort <= maxPort) ? minPort : -1; // 返回 -1 表示没有可用端口
    }

    public boolean resetVmVncPassword(Integer vmHostId,String newPassword) {
        // 获取虚拟机VNC连接信息
        Vncinfo currentVncinfo = vncinfoService.selectVncinfoByHostId(Long.valueOf(vmHostId));
        // 如果虚拟机没有vnc信息
        if (currentVncinfo != null){
            currentVncinfo.setPassword(newPassword);
            // 修改vnc密码
            if (vncinfoService.updateVncinfo(currentVncinfo)){
                Vncinfo vncinfo = vncinfoService.selectVncinfoByHostId(Long.valueOf(vmHostId));
                // 判空
                if (vncinfo != null){
                    Vncdata vncdata = vncdataService.selectVncdataByVncinfoIdAndStatusOk(Long.valueOf(vncinfo.getId()));
                    // 判空
                    if (vncdata != null){
                        vncdata.setStatus(1);
                        vncdataService.updateVncdata(vncdata);
                        return true;
                    }
                }
                return true;
            }
        }
        return false;
    }

    /**
    * @Author: mryunqi
    * @Description: 二分查找未被使用的端口
    * @DateTime: 2023/11/24 18:46
    */
    /*private int findUnusedPort(List<Integer> usedPorts) {
        int minPort = 59000; // 如果数据库中的端口为空，则从59000开始
        int maxPort = 65535; // 最大端口号

        int low = 0;
        int high = usedPorts.size() - 1;

        while (low <= high) {
            int mid = low + (high - low) / 2;
            int midVal = usedPorts.get(mid);

            if (midVal > minPort) {
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }

        // low现在是第一个大于minPort的索引
        // 如果列表中的端口都被使用，返回下一个可用的端口
        return (low <= usedPorts.size() - 1) ? usedPorts.get(low) : (minPort <= maxPort) ? minPort : -1;
    }*/

}
