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
import com.chuqiyun.proxmoxveams.service.ProxmoxApiService;
import com.chuqiyun.proxmoxveams.utils.OsTypeUtil;
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
        ProxmoxApiService proxmoxApiService = new ProxmoxApiService();
        // 查询vm列表 {"data":[{'vmid':100,'name':'test'},{'vmid':101,'name':'test2'}]}
        JSONObject vmJson = proxmoxApiService.getNodeApi(master,cookieMap,"/nodes/"+master.getNodeName()+"/qemu",new HashMap<>());
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
        ProxmoxApiService proxmoxApiService = new ProxmoxApiService();
        JSONObject vmJson = proxmoxApiService.getNodeApi(master,cookieMap,"/nodes/"+master.getNodeName()+"/storage",new HashMap<>());
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
        ProxmoxApiService proxmoxApiService = new ProxmoxApiService();
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
        JSONObject jsonObject =  proxmoxApiService.postNodeApi(node,authentications, "/nodes/"+node.getNodeName()+"/qemu", param);
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
        ProxmoxApiService proxmoxApiService = new ProxmoxApiService();
        Master node = this.getById(nodeId);
        // 获取cookie
        HashMap<String, String> authentications = getMasterCookieMap(nodeId);
        return proxmoxApiService.getNodeApi(node,authentications, "/nodes/"+node.getNodeName()+"/qemu/"+vmid+"/config", new HashMap<>()).getJSONObject("data");
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

}

