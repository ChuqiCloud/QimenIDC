package com.chuqiyun.proxmoxveams.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.MasterDao;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.VmParams;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import com.chuqiyun.proxmoxveams.utils.OsTypeUtil;
import com.chuqiyun.proxmoxveams.utils.VmUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
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
    * @Description: 生成最新vmid
    * @DateTime: 2023/6/21 15:21
    * @Params: Integer id 节点id
    * @Return Integer
    */
    @Override
    public Integer getNewVmid(Integer id) {
        // 获取master
        Master master = this.getById(id);
        // 获取cookie
        HashMap<String, String> cookieMap = this.getMasterCookieMap(id);
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        // 查询vm列表 {"data":[{'vmid':100,'name':'test'},{'vmid':101,'name':'test2'}]}
        JSONObject vmJson = proxmoxApiUtil.getNodeApi(master,cookieMap,"/nodes/"+master.getNodeName()+"/qemu",new HashMap<>());
        JSONArray jsonArray = vmJson.getJSONArray("data");
        // 判断是否为空
        if (jsonArray == null || jsonArray.size() == 0) {
            return 100;
        }
        ArrayList<Integer> vmidList = new ArrayList<>();

        for (int i = 0; i < jsonArray.size(); i++) {
            JSONObject tempJsonObject = jsonArray.getJSONObject(i);
            int vmid = tempJsonObject.getIntValue("vmid");
            vmidList.add(vmid);
        }

        vmidList.sort(Comparator.naturalOrder());
        int maxVmid = vmidList.get(vmidList.size() - 1);
        return maxVmid+1;
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
    * @Description: 创建虚拟机
    * @DateTime: 2023/6/21 23:41
    */
    @Override
    public Integer createVm(VmParams vmParams){
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        Master node = this.getById(vmParams.getNodeid());
        // 创建虚拟机可选参数
        HashMap<String, Object> param = new HashMap<>();
        int vmId = getNewVmid(vmParams.getNodeid());
        param.put("vmid", vmId);
        param.put("name", vmParams.getHostname());
        // 设置CPU插槽
        param.put("sockets", vmParams.getSockets());
        // 设置CPU
        param.put("cores", vmParams.getCores());
        // 判断是否开启嵌套虚拟化
        if (Boolean.TRUE.equals(vmParams.getNested())){
            // 判断是否为windows系统
            if ("win".equals(vmParams.getOsType())){
                // 设置CPU为max，开启嵌套虚拟化
                param.put("cpu", "max");
                param.put("args", "-cpu max,-hypervisor,+kvm_pv_unhalt,+kvm_pv_eoi,hv_spinlocks=0x1fff,hv_vapic,hv_time,hv_reset,hv_vpindex,hv_runtime,hv_relaxed,kvm=off,hv_vendor_id=intel,hv_synic,hv_stimer,hv_frequencies,hv_tlbflush,hv_ipi");
            }else {
                // 设置CPU为max，开启嵌套虚拟化
                param.put("cpu", "max");
                param.put("args", "-cpu max,+kvm_nested,+kvm_pv_unhalt,+kvm_pv_eoi,hv_vendor_id=proxmox,hv_spinlocks=0x1fff,hv_vapic,hv_time,hv_reset,hv_vpindex,hv_runtime,hv_relaxed,hv_synic,hv_stimer,hv_frequencies,hv_tlbflush,hv_ipi");
            }

            //param.put("args", "-cpu host,+kvm_nested,+kvm_pv_unhalt,+kvm_pv_eoi,hv_vendor_id=proxmox,hv_spinlocks=0x1fff,hv_vapic,hv_time,hv_reset,hv_vpindex,hv_runtime,hv_relaxed,hv_synic,hv_stimer,hv_frequencies,hv_tlbflush,hv_ipi");

        }else {
            // 设置CPU与宿主机相同，不开启嵌套虚拟化
            param.put("args", "-cpu host,-vmx");
        }
        int ipCount = vmParams.getIpConfig().size();
        for (int i = 0; i < ipCount; i++) {
            param.put("ipconfig"+i, vmParams.getIpConfig().get(String.valueOf(i+1)));
        }
        //param.put("ipconfig0", "ip=23.94.247.39/28,gw=23.94.247.33");
        // 设置DNS
        param.put("nameserver", vmParams.getDns1());
        // 设置虚拟机osType
        param.put("ostype", OsTypeUtil.getOsType(vmParams.getOs(),vmParams.getOsType()));
        // 开机启动
        param.put("onboot", 1);
        // 设置内存
        param.put("memory", vmParams.getMemory());
        // 开启QEMU Agent
        param.put("agent", 1);
        // 设置虚拟机citype
        if ("win".equals(vmParams.getOsType())){
            param.put("citype","configdrive2");
        }
        if ("linux".equals(vmParams.getOsType())){
            param.put("citype","nocloud");
        }
        // 设置虚拟机账号
        param.put("ciuser",vmParams.getUsername());
        // 设置虚拟机密码
        param.put("cipassword",vmParams.getPassword());
        // 设置cloud-init
        if (vmParams.getStorage() == null){
            param.put("ide2", "local-lvm:cloudinit");
        }else if ("auto".equals(vmParams.getStorage())) {
            param.put("ide2", "local-lvm:cloudinit");
        }else {
            param.put("ide2", vmParams.getStorage()+":cloudinit");
        }
        // 设置网络
        if (vmParams.getBridge() == null) {
            param.put("net0", "virtio,bridge=vmbr0,rate="+vmParams.getBandwidth());
        }else {
            param.put("net0", "virtio,bridge="+vmParams.getBridge());
        }
        // 获取cookie
        HashMap<String, String> authentications = getMasterCookieMap(vmParams.getNodeid());
        JSONObject jsonObject =  proxmoxApiUtil.postNodeApi(node,authentications, "/nodes/"+node.getNodeName()+"/qemu", param);
        if (jsonObject.containsKey("data")){
            return vmId;
        }else {
            return 0;
        }
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

}

