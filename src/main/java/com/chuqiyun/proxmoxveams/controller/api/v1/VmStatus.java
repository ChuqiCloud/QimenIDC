package com.chuqiyun.proxmoxveams.controller.api.v1;

import com.chuqiyun.proxmoxveams.annotation.PublicSysApiCheck;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import com.chuqiyun.proxmoxveams.utils.ResponseResult;
import com.chuqiyun.proxmoxveams.utils.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;

/**
 * @author mryunqi
 * @date 2023/7/18
 */
@Slf4j
@RestController
public class VmStatus {
    @Resource
    private VmhostService vmhostService;
    @PublicSysApiCheck
    @PutMapping("/api/v1/power/{hostId}/{action}")
    public ResponseResult<String> putVmStatus(@PathVariable(name = "hostId") Integer hostId,
                                              @PathVariable(name = "action") String action) throws UnauthorizedException {
        // 判断是否存在该虚拟机
        if (vmhostService.getById(hostId) == null) {
            return ResponseResult.fail("虚拟机不存在");
        }
        // 判断action是否合法
        if (!"start".equals(action) && !"stop".equals(action) && !"shutdown".equals(action) && !"reboot".equals(action)) {
            return ResponseResult.fail("action不合法");
        }
        HashMap<String, Object> result = vmhostService.power(hostId, action);
        if (result == null) {
            return ResponseResult.fail("操作失败");
        }
        Boolean status = (Boolean) result.get("status");
        if (!status) {
            return ResponseResult.fail(result.get("message").toString());
        }
        return ResponseResult.ok();
    }
}
