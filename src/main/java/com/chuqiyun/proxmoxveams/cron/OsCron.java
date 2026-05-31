package com.chuqiyun.proxmoxveams.cron;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.dto.OsNodeStatus;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Os;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.OsService;
import com.chuqiyun.proxmoxveams.utils.ModUtil;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author mryunqi
 * @date 2023/8/13
 */
@Component
@EnableScheduling
public class OsCron {
    @Resource
    private OsService osService;
    @Resource
    private MasterService masterService;
    // 下载进度超过该时长无推进则视为卡死，自动清理
    private static final long OS_DOWNLOAD_STALE_TIMEOUT = 1000L * 60 * 30;

    /**
    * @Author: mryunqi
    * @Description: 更新检测镜像下载url是否可用，且更新镜像大小
    * @DateTime: 2023/8/13 17:11
    */
    @Async
    @Scheduled(fixedDelay = 1000*60*3) // 3分钟执行一次
    public void updateOsUrlCron() {
        QueryWrapper<Os> queryWrap = new QueryWrapper<>();
        // status不为1，且downType为0的镜像
        queryWrap.ne("status", 1);
        queryWrap.eq("down_type", 0);
        int page = 1;
        while (true) {
            // 获取一个镜像
            Page<Os> osPage = osService.selectOsByPage(page, 200, queryWrap);
            if (osPage.getRecords().size() > 0) {
                for (Os os : osPage.getRecords()) {
                    String url = os.getUrl();
                    long size = 0;
                    // 获取镜像大小
                    try {
                        size = ModUtil.getUrlFileSize(url);
                    } catch (Exception e) {
                        os.setStatus(2);
                        os.setReason("连接超时: " + url + ", 错误: " + e.getMessage());
                        osService.updateById(os);
                        continue;
                    }
                    if (size == 0) {
                        os.setStatus(2);
                        os.setReason("镜像下载地址不可用: " + url);
                        osService.updateById(os);
                        continue;
                    }
                    os.setSize(ModUtil.formatFileSize(size));
                    if (os.getStatus() == 2) {
                        os.setStatus(0);
                        os.setReason("");
                    }
                    osService.updateById(os);
                }
            }else {
                break;
            }
            // 判断是否还有页面
            if (osPage.getPages() == page) {
                break;
            }
            page++;
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 更新镜像下载进度
    * @DateTime: 2023/8/14 23:53
    */
    @Async
    @Scheduled(fixedRate = 1000*5) // 不管上次执行是否完成，每隔5秒执行一次
    public void osDownloadCron(){
        QueryWrapper<Os> queryWrap = new QueryWrapper<>();
        queryWrap.ne("status", 1);
        queryWrap.eq("down_type", 0);
        int page = 1;
        while (true){
            // 获取一个镜像
            Page<Os> osPage = osService.selectOsByPage(page, 200, queryWrap);
            if (osPage.getRecords().size() > 0) {
                for (Os os : osPage.getRecords()) {
                    Map<String,Object> map = os.getNodeStatus();
                    // 如果map为空，说明没有节点在下载
                    if (map == null) {
                        continue;
                    }
                    // 循环遍历节点
                    for (String key : new HashSet<>(map.keySet())) {
                        Object osNodeStatusObj = map.get(key);
                        // 转换为OsNodeStatus对象
                        OsNodeStatus osNodeStatus = JSONObject.parseObject(JSONObject.toJSONString(osNodeStatusObj), OsNodeStatus.class);
                        // 判断节点状态
                        Master node = masterService.getById(osNodeStatus.getNodeId());
                        if (node == null) {
                            // 节点已删除，清理残留的节点状态
                            map.remove(key);
                            osService.updateById(os);
                            continue;
                        }
                        if (node.getControllerStatus() != 0) {
                            continue;
                        }
                        // 下载中/下载失败且长时间无进度推进，自动清理以便重新下载
                        if (osNodeStatus.getStatus() == 1 || osNodeStatus.getStatus() == 3) {
                            Long updateTime = osNodeStatus.getUpdateTime();
                            if (updateTime == null) {
                                osNodeStatus.setUpdateTime(System.currentTimeMillis());
                                os.getNodeStatus().put(key, osNodeStatus);
                                osService.updateById(os);
                            } else if (System.currentTimeMillis() - updateTime > OS_DOWNLOAD_STALE_TIMEOUT) {
                                map.remove(key);
                                osService.updateById(os);
                                continue;
                            }
                        }
                        // 如果节点状态为0,2,3，则直接跳过 0=未下载;1=下载中;2=已下载;3=下载失败
                        if (osNodeStatus.getStatus() == 0 || osNodeStatus.getStatus() == 2 || osNodeStatus.getStatus() == 3) {
                            continue;
                        }
                        JSONObject downloadInfo;
                        try {
                            // 获取节点下载进度
                            downloadInfo = osService.getDownloadProgress(os.getId(), osNodeStatus.getNodeId());
                        } catch (HttpServerErrorException e) {
                            // 专门处理HTTP 500错误
                            System.out.println("Proxmox节点["+node.getId()+"]返回500错误，响应内容: "
                                    + e.getResponseBodyAsString());
                            osNodeStatus.setStatus(3);
                            // 更新节点状态
                            os.getNodeStatus().put(key, osNodeStatus);
                            osService.updateById(os);
                            continue;
                        } catch (Exception e) {
                            System.out.println("获取下载进度异常" + e);
                            continue;
                        }
                        String progress = downloadInfo.getString("progress");
                        if (progress == null) {
                            continue;
                        }

                        // 将progress转换为double类型
                        double progressDouble = Double.parseDouble(progress.substring(0, progress.length() - 1));
                        // 进度有推进则刷新时间戳
                        if (osNodeStatus.getSchedule() == null || progressDouble > osNodeStatus.getSchedule()) {
                            osNodeStatus.setUpdateTime(System.currentTimeMillis());
                        }
                        // 进度达到100%即视为下载完成
                        if (progressDouble >= 100.0){
                            osNodeStatus.setStatus(2);
                        }
                        else {
                            osNodeStatus.setStatus(1);
                        }
                        osNodeStatus.setSchedule(progressDouble);
                        // 更新节点状态
                        os.getNodeStatus().put(key,osNodeStatus);
                        osService.updateById(os);
                    }
                }
            }else {
                break;
            }
            // 判断是否还有页面
            if (osPage.getPages() == page) {
                break;
            }
            page++;
        }
    }

    /**
    * @Author: 星禾
    * @Description: 定时扫描节点上已存在但未登记的镜像，自动写入已下载状态
    * @DateTime: 2026/05/31
    */
    @Async
    @Scheduled(fixedDelay = 1000*60*5) // 5分钟扫描一次
    public void scanNodeOsCron(){
        List<Master> nodes = masterService.list();
        if (nodes == null || nodes.isEmpty()){
            return;
        }
        int page = 1;
        while (true){
            Page<Os> osPage = osService.selectOsByPage(page, 200, new QueryWrapper<>());
            if (osPage.getRecords().isEmpty()){
                break;
            }
            for (Os os : osPage.getRecords()){
                Map<String,Object> map = os.getNodeStatus();
                boolean changed = false;
                // 校验已下载记录对应文件是否还在，被手动删除则清理
                if (map != null){
                    for (String key : new HashSet<>(map.keySet())){
                        OsNodeStatus osNodeStatus = JSONObject.parseObject(JSONObject.toJSONString(map.get(key)), OsNodeStatus.class);
                        if (osNodeStatus.getStatus() != 2){
                            continue;
                        }
                        Master node = masterService.getById(osNodeStatus.getNodeId());
                        // 节点不存在或被控端不在线不校验，避免误删
                        if (node == null || node.getControllerStatus() != 0){
                            continue;
                        }
                        // 文件已不存在（如手动rm）则清理记录，不校验大小与完整性
                        if (!osService.isOsExistOnNode(os.getFileName(), os.getPath(), osNodeStatus.getNodeId())){
                            map.remove(key);
                            changed = true;
                        }
                    }
                }
                for (Master node : nodes){
                    // 被控端不在线跳过
                    if (node.getControllerStatus() != 0){
                        continue;
                    }
                    // 已登记该节点跳过
                    if (containsNode(map, node.getId())){
                        continue;
                    }
                    // 节点上不存在该镜像文件跳过
                    if (!osService.isOsExistOnNode(os.getFileName(), os.getPath(), node.getId())){
                        continue;
                    }
                    // 自动写入已下载状态
                    if (map == null){
                        map = new HashMap<>();
                        os.setNodeStatus(map);
                    }
                    OsNodeStatus osNodeStatus = new OsNodeStatus();
                    osNodeStatus.setNodeId(node.getId());
                    osNodeStatus.setNodeName(node.getNodeName());
                    osNodeStatus.setStatus(2);
                    osNodeStatus.setSchedule(100.00);
                    map.put(ModUtil.nextMapKey(map), osNodeStatus);
                    changed = true;
                }
                if (changed){
                    osService.updateById(os);
                }
            }
            if (osPage.getPages() == page){
                break;
            }
            page++;
        }
    }

    /**
    * @Author: 星禾
    * @Description: 判断nodeStatus中是否已包含指定节点
    */
    private boolean containsNode(Map<String,Object> map, Integer nodeId){
        if (map == null){
            return false;
        }
        for (Object value : map.values()){
            OsNodeStatus osNodeStatus = JSONObject.parseObject(JSONObject.toJSONString(value), OsNodeStatus.class);
            if (Objects.equals(osNodeStatus.getNodeId(), nodeId)){
                return true;
            }
        }
        return false;
    }

}
