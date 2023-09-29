package com.chuqiyun.proxmoxveams.cron;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.common.UnifiedLogger;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Os;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.*;
import com.chuqiyun.proxmoxveams.utils.ClientApiUtil;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import com.chuqiyun.proxmoxveams.utils.VmUtil;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.chuqiyun.proxmoxveams.constant.TaskType.*;

/**
 * @author mryunqi
 * @date 2023/9/2
 */
@Component
@EnableScheduling
public class resetPasswordCron {
    @Resource
    private MasterService masterService;
    @Resource
    private VmhostService vmhostService;
    @Resource
    private TaskService taskService;
    @Resource
    private OsService osService;
    @Resource
    private ConfigService configService;

    /**
    * @Author: mryunqi
    * @Description: 重置虚拟机密码
    * @DateTime: 2023/9/2 14:14
    */
    @Async
    @Scheduled(fixedDelay = 1000)
    public void resetPassword() {
        QueryWrapper<Task> taskQueryWrapper = new QueryWrapper<>();
        taskQueryWrapper.eq("type", RESET_PASSWORD);
        taskQueryWrapper.eq("status", 0);
        Page<Task> taskPage = taskService.getTaskList(1, 1, taskQueryWrapper);
        // 判断是否没有任务
        if (taskPage.getRecords().size() == 0) {
            return;
        }
        Task task = taskPage.getRecords().get(0);

        // 修改任务状态为1
        task.setStatus(1);
        taskService.updateById(task);
        // 获取vm信息
        Vmhost vmhost = vmhostService.getById(task.getHostid());
        List<Integer> statusList = VmUtil.getRecoveringStatusList();
        // 判断虚拟机的状态是否为在其中
        if (statusList.contains(vmhost.getStatus())) {
            // 修改任务状态为0
            task.setStatus(0);
            taskService.updateById(task);
            return;
        }
        Map<Object, Object> params = task.getParams();
        String newPassword = (String) params.get("newPassword");
        // 获取node信息
        Master node = masterService.getById(task.getNodeid());
        HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());

        String osName = vmhost.getOs();
        Os os = osService.selectOsByFileName(osName);
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        // cloud-init
        int cloudInit = os.getCloud();
        if (cloudInit == 1) {
            // 重置密码
            //proxmoxApiUtil.resetVmPassword(node,authentications,vmhost.getVmid(),newPassword);
            try {
                proxmoxApiUtil.resetVmPassword(node,authentications,vmhost.getVmid(),newPassword);
            } catch (Exception e) {
                // 修改任务状态为失败
                task.setStatus(3);
                task.setError("重置密码失败");
                taskService.updateById(task);
                return;
            }
        } else {
            // 通过qemu修改密码
            ClientApiUtil.resetPassword(node.getHost(),configService.getToken(),vmhost.getVmid(),vmhost.getUsername(),newPassword);
        }
        // 修改vmhost密码
        vmhost.setPassword(newPassword);
        vmhostService.updateById(vmhost);
        UnifiedLogger.log(UnifiedLogger.LogType.TASK_RESET_PASSWORD,"重置密码成功: NodeID:{} VM-ID:{}",node.getId(), task.getVmid());


        // 添加开机任务
        Task startTask = new Task();
        startTask.setNodeid(vmhost.getNodeid());
        startTask.setVmid(vmhost.getVmid());
        startTask.setHostid(vmhost.getId());
        startTask.setType(START_VM);
        startTask.setStatus(0);
        startTask.setCreateDate(System.currentTimeMillis());
        if (!taskService.insertTask(startTask)){
            UnifiedLogger.error(UnifiedLogger.LogType.TASK_START_VM,"添加创建开机任务: NodeID:{} VM-ID:{} 失败",node.getId(), task.getVmid());
            // 修改任务状态为失败
            task.setStatus(3);
            task.setError("创建修改启动项任务失败");
            taskService.updateById(task);
        }
        // 修改任务状态为2
        task.setStatus(2);
        taskService.updateById(task);
    }
}
