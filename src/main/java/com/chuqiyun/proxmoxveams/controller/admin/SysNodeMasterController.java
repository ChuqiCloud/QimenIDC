package com.chuqiyun.proxmoxveams.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.utils.EncryptUtil;
import com.chuqiyun.proxmoxveams.utils.ResponseResult;
import com.chuqiyun.proxmoxveams.utils.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * @author mryunqi
 * @date 2023/6/18
 */
@RestController
public class SysNodeMasterController {
    @Value("${config.admin_path}")
    private String ADMIN_PATH;
    @Resource
    private MasterService masterService;

    @AdminApiCheck
    @PostMapping("/{adminPath}/insertNodeMaster")
    public ResponseResult<String> insertNodeMaster(@PathVariable("adminPath") String adminPath,
                                           @RequestBody Master master) throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
        // 将master信息存入数据库
        if (masterService.save(master)) {
            return ResponseResult.ok("添加成功！");
        } else {
            return ResponseResult.fail("添加失败！");
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 分页获取集群节点信息
    * @DateTime: 2023/7/22 22:10
    */
    @AdminApiCheck
    @GetMapping("/{adminPath}/selectNodeByPage")
    public ResponseResult<Object> selectNodeByPage(@PathVariable("adminPath") String adminPath,
                                                   @RequestParam(name = "page",defaultValue = "1") Integer page,
                                                   @RequestParam(name = "size", defaultValue = "20") Integer size) throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
        Page<Master> masterPage = masterService.getMasterList(page,size);
        // 将每个master的csrfToken与ticket加***处理
        for (Master master : masterPage.getRecords()) {
            master.setPassword("**********");
            master.setCsrfToken("**********");
            master.setTicket("**********");
            master.setSshPassword("**********");
        }
        return ResponseResult.ok(masterPage);
    }
}
