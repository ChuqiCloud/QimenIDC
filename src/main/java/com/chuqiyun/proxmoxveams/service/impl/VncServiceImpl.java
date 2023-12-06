package com.chuqiyun.proxmoxveams.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.dto.VncInfoDto;
import com.chuqiyun.proxmoxveams.entity.*;
import com.chuqiyun.proxmoxveams.service.*;
import com.chuqiyun.proxmoxveams.utils.ClientApiUtil;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
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
        if (vmhost == null){
            // 将vmHostId作为为vmid
            vmhost = vmhostService.getVmhostByVmId(Math.toIntExact(hostId));
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
            // http://192.168.36.155:6080/vnc.html?path=websockify/?token=vm100
            String http = "http://";
            if (vncnode.getProtocol() == 1){
                http = "https://";
            }
            String domain = vncnode.getDomain();
            // 判空
            if (domain == null || domain.equals("")){
                domain = vncnode.getHost();
            }

            JSONObject tokenJson = new JSONObject();
            tokenJson.put("host",vncinfo.getHost());
            tokenJson.put("port",vncinfo.getPort());
            // 将token信息转为base64编码
            String token = Base64.encodeBase64String(tokenJson.toJSONString().getBytes());
            String url = "";
            // 判断是开启代理
            if (vncnode.getProxy() == 1) {
                // 不带端口
                url = http + domain + "/vnc.html?token=" + token;
            }else {
                // 带端口
                url = http + domain + ":6080" + "/vnc.html?token=" + token;
            }
            map.put(vncnode.getName(),url);
        }
        return map;
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
                // 判断是否为同一节点
                if (vncnode.getHost().equals(vncinfo.getHost())){
                    continue;
                }
                // 与被控通讯同步VNC连接信息
                Boolean consoleResult = ClientApiUtil.importVncService(vncinfo.getHost(),token, Math.toIntExact(vncinfo.getVmid()),vncinfo.getPort(),vncinfo.getUsername(),vncinfo.getPassword(),vncinfo.getPort(),vncinfo.getHost(),vncExpiryTime);
                if (!consoleResult){
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
