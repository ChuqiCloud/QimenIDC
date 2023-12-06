package com.chuqiyun.proxmoxveams.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.dao.MasterDao;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import com.chuqiyun.proxmoxveams.utils.VmUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * (Master)表服务实现类
 *
 * @author mryunqi
 * @since 2023-06-10 01:18:18
 */
@Service("masterService")
public class MasterServiceImpl extends ServiceImpl<MasterDao, Master> implements MasterService {
    /**
    * @Author: mryunqi
    * @Description: 获取节点总数
    * @DateTime: 2023/11/26 21:09
    * @Return Long 节点总数
    */
    @Override
    public Long getMasterCount() {
        return this.count();
    }

    /**
    * @Author: mryunqi
    * @Description: 获取ProxmoxVE集群节点列表
    * @DateTime: 2023/6/19 20:16
    * @Params: Integer page 页数
    * @Params: Integer size 每页数据
    * @Return  Page<Master>
    */
    @Override
    public Page<Master> getMasterList(Integer page, Integer size) {
        Page<Master> masterPage = new Page<>(page, size);
        return this.page(masterPage);
    }
    /**
     * @Author: mryunqi
     * @Description: 获取ProxmoxVE集群节点列表
     * @DateTime: 2023/6/19 20:16
     * @Params: Integer page 页数
     * @Params: Integer size 每页数据
     * @Params: QueryWrapper<Master> queryWrapper 查询条件
     * @Return  Page<Master>
     */
    @Override
    public Page<Master>getMasterList(Integer page, Integer size, QueryWrapper<Master> queryWrapper) {
        Page<Master> masterPage = new Page<>(page, size);
        return this.page(masterPage,queryWrapper);
    }

    /**
    * @Author: mryunqi
    * @Description: 获取ProxmoxVE集群节点的cookie
    * @DateTime: 2023/6/20 15:09
    * @Params: Integer id 节点id
    * @Return  HashMap<String,String>
    */
    @Override
    public HashMap<String,String> getMasterCookieMap(Integer id){
        Master master = this.getById(id);
        HashMap<String, String> cookieMap = new HashMap<>();
        cookieMap.put("CSRFPreventionToken",master.getCsrfToken());
        cookieMap.put("cookie","PVEAuthCookie="+master.getTicket());
        return cookieMap;
    }

    /**
    * @Author: mryunqi
    * @Description: 获取节点磁盘名称列表
    * @DateTime: 2023/6/21 20:07
    */
    @Override
    public ArrayList<JSONObject> getDiskList(Integer id) {
        // 获取master
        Master master = this.getById(id);
        // 获取cookie
        HashMap<String, String> cookieMap = this.getMasterCookieMap(id);
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        JSONObject vmJson = proxmoxApiUtil.getNodeApi(master,cookieMap,"/nodes/"+master.getNodeName()+"/storage",new HashMap<>());
        JSONArray jsonArray = vmJson.getJSONArray("data");
        ArrayList<JSONObject> diskList = new ArrayList<>();
        // 将jsonArray转换为ArrayList<JSONObject>
        for (int i = 0; i < jsonArray.size(); i++) {
            JSONObject tempJsonObject = jsonArray.getJSONObject(i);
            diskList.add(tempJsonObject);
        }
        return diskList;
    }

    /**
    * @Author: mryunqi
    * @Description: 获取虚拟机信息
    * @DateTime: 2023/6/22 0:31
    */
    @Override
    public JSONObject getVmInfo(Integer nodeId, Integer vmid) {
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        Master node = this.getById(nodeId);
        // 获取cookie
        HashMap<String, String> authentications = getMasterCookieMap(nodeId);
        return proxmoxApiUtil.getNodeApi(node,authentications, "/nodes/"+node.getNodeName()+"/qemu/"+vmid+"/config", new HashMap<>()).getJSONObject("data");
    }
    /**
    * @Author: mryunqi
    * @Description: 获取虚拟机当前状态
    * @DateTime: 2023/7/18 15:44
    * @Params: Integer nodeId 节点ID, Integer vmid 虚拟机ID
    * @Return JSONObject 虚拟机当前状态
    */
    @Override
    public JSONObject getVmStatusCurrent(Integer nodeId, Integer vmid) {
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        Master node = this.getById(nodeId);
        // 获取cookie
        HashMap<String, String> authentications = getMasterCookieMap(nodeId);
        return proxmoxApiUtil.getNodeApi(node,authentications, "/nodes/"+node.getNodeName()+"/qemu/"+vmid+"/status/current", new HashMap<>()).getJSONObject("data");
    }

    /**
    * @Author: mryunqi
    * @Description: 获取所有节点id
    * @DateTime: 2023/7/14 23:33
    */
    @Override
    public ArrayList<Integer> getAllNodeIdList() {
        ArrayList<Integer> nodeIds = new ArrayList<>();
        List<Master> masters = this.list();
        for (Master master : masters) {
            nodeIds.add(master.getId());
        }
        return nodeIds;
    }

    /**
    * @Author: mryunqi
    * @Description: 获取指定节点所有虚拟机基础数据
    * @DateTime: 2023/7/19 21:47
    * @Params: Integer nodeId 节点ID
    * @Return JSONObject 虚拟机基础数据
    */
    @Override
    public JSONObject getNodeVmInfoJsonList(Integer nodeId) {
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        Master node = this.getById(nodeId);
        // 获取cookie
        HashMap<String, String> authentications = getMasterCookieMap(nodeId);
        try {
            return proxmoxApiUtil.getNodeApi(node,authentications, "/nodes/"+node.getNodeName()+"/qemu", new HashMap<>());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 判断节点是否在线
    * @DateTime: 2023/12/6 13:42
    * @Params: Integer nodeId 节点ID
    * @Return Boolean 节点是否在线
    */
    @Override
    public Boolean isNodeOnline(Integer nodeId) {
        Master node = this.getById(nodeId);
        // 先判断数据库中节点状态是否为在线
        if (node.getStatus() != 0){
            return false;
        }
        // 获取cookie
        HashMap<String, String> authentications = getMasterCookieMap(nodeId);
        try {
            ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
            proxmoxApiUtil.getNodeApi(node,authentications, "/nodes/"+node.getNodeName()+"/qemu", new HashMap<>());
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 更新所有节点cookie
    * @DateTime: 2023/7/20 0:51
    */
    @Override
    public void updateAllNodeCookie() {
        int i = 1;
        while (true){
            QueryWrapper<Master> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("status",0);
            // 分页获取100行节点实例
            Page<Master> page = this.getMasterList(i,100,queryWrapper);
            List<Master> nodes = page.getRecords();
            // 如果获取到的节点实例为空，则跳出循环
            if (nodes.size() == 0){
                break;
            }
            // 遍历节点实例，更新cookie
            for (Master node : nodes) {
                updateCookie(node);
            }
            // 如果当前页数等于总页数则跳出循环
            if (i == page.getPages()){
                break;
            }
            i++;
        }
    }
    
    /**
    * @Author: mryunqi
    * @Description: 更新指定节点cookie
    * @DateTime: 2023/8/4 22:08
    * @Params: Integer nodeId 节点ID
    * @Return void
    */
    @Override
    public void updateNodeCookie(Integer nodeId) {
        Master node = this.getById(nodeId);
        updateCookie(node);
    }

    private void updateCookie(Master node) {
        String url = "https://"+node.getHost()+":"+node.getPort();
        HashMap<String,String> user = new HashMap<>();
        user.put("url",url);
        user.put("username",node.getUsername());
        user.put("password",node.getPassword());
        user.put("realm",node.getRealm());
        ProxmoxApiUtil pveApi = new ProxmoxApiUtil();
        try {
            HashMap<String, String> authentications = pveApi.loginAndGetCookie(user);
            node.setTicket(authentications.get("ticket"));
            node.setCsrfToken(authentications.get("csrfToken"));
            this.updateById(node);
        }catch (Exception e){
            e.printStackTrace();
            // 出现异常则将节点状态设置为离线
            node.setStatus(1);
            this.updateById(node);
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 获取虚拟机状态码
     * 0=运行中、1=已关机、2=挂起、3=恢复中、4=暂停、5=到期、6=未知
    * @DateTime: 2023/7/20 23:09
    * @Params: Integer nodeId 节点ID, Integer vmid 虚拟机ID
    * @Return Integer 虚拟机状态码
    */
    @Override
    public Integer getVmStatusCode(Integer nodeId, Integer vmid) {
        JSONObject vmStatusCurrent = this.getVmStatusCurrent(nodeId, vmid);
        if (vmStatusCurrent == null){
            return 6;
        }
        String strStatus = vmStatusCurrent.getString("status");
        // 转换为int
        int initStatus = VmUtil.getVmStatusNumByStr(strStatus);
        // 判断是否存在lock字段
        if (vmStatusCurrent.containsKey("lock")){
            // 如果为suspending，则将状态设置为2
            if ("suspending".equals(vmStatusCurrent.getString("lock"))){
                initStatus = 2;
            }
            // 如果为suspended，也为2
            if ("suspended".equals(vmStatusCurrent.getString("lock"))){
                initStatus = 2;
            }
        }
        return initStatus;
    }

    /**
    * @Author: mryunqi
    * @Description: 删除指定节点
    * @DateTime: 2023/10/22 22:44
    * @Params:  Integer nodeId 节点ID
    * @Return UnifiedResultDto<Object> 删除结果
    */
    @Override
    public UnifiedResultDto<Object> deleteNode(VmhostService vmhostService,Integer nodeId) {
        // 获取节点实例
        Master node = this.getById(nodeId);
        // 判断是否存在
        if (node == null){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NODE_NOT_EXIST, null);
        }
        // 获取节点下所有虚拟机
        Page<Vmhost> vmhostPage = vmhostService.selectPage(1, 1, new QueryWrapper<Vmhost>().eq("nodeid", nodeId));
        List<Vmhost> vmhostList = vmhostPage.getRecords();
        // 判断节点下是否存在虚拟机
        if (vmhostList.size() > 0){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NODE_HAS_VM, null);
        }
        // 删除节点
        boolean deleteNode = this.removeById(nodeId);
        if (deleteNode){
            return new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, null);
        }else {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_DELETE_VM_UNKNOWN, null);
        }
    }
}

