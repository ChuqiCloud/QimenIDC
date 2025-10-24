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
import java.util.Map;

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
                    // 获取镜像大小
                    long size = ModUtil.getUrlFileSize(url);
                    // 如果为0，说明url不可用
                    if (size == 0) {
                        // 设置镜像状态为2，2为异常
                        os.setStatus(2);
                        // 记录异常信息
                        os.setReason("镜像下载地址不可用");
                        osService.updateById(os);
                        continue;
                    }
                    // 设置镜像大小
                    os.setSize(ModUtil.formatFileSize(size));
                    // 如果镜像状态为2，说明之前镜像下载地址不可用，现在可用了，所以设置镜像状态为0，并清空异常信息
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
                    for (String key : map.keySet()) {
                        Object osNodeStatusObj = map.get(key);
                        // 转换为OsNodeStatus对象
                        OsNodeStatus osNodeStatus = JSONObject.parseObject(JSONObject.toJSONString(osNodeStatusObj), OsNodeStatus.class);
                        // 判断节点状态
                        Master node = masterService.getById(osNodeStatus.getNodeId());
                        if (node == null) {
                            continue;
                        }
                        if (node.getControllerStatus() != 0) {
                            continue;
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
                        // 如果进度为100.0%，则设置节点状态为1，1为下载完成
                        if ("100.0%".equals(progress)){
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

}
