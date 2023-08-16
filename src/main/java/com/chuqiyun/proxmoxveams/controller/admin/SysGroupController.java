package com.chuqiyun.proxmoxveams.controller.admin;

import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import com.chuqiyun.proxmoxveams.entity.Group;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.service.GroupService;
import com.chuqiyun.proxmoxveams.service.MasterService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * @author mryunqi
 * @date 2023/8/14
 */
@RestController
public class SysGroupController {
    @Value("${config.admin_path}")
    private String ADMIN_PATH;
    @Resource
    private GroupService groupService;
    @Resource
    private MasterService masterService;

    /**
    * @Author: mryunqi
    * @Description: 增加地区
    * @DateTime: 2023/8/15 23:16
    */
    @AdminApiCheck
    @PostMapping("/{adminPath}/addArea")
    public ResponseResult<String> addArea(@PathVariable("adminPath") String adminPath,
                                          @RequestBody Group group) throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
        // 插入地区
        if (groupService.save(group)){
            return ResponseResult.ok("添加成功");
        }
        return ResponseResult.fail("添加失败");
    }

    /**
    * @Author: mryunqi
    * @Description: 删除地区
    * @DateTime: 2023/8/15 23:20
    */
    @AdminApiCheck
    @DeleteMapping("/{adminPath}/deleteArea/{id}")
    public ResponseResult<String> deleteArea(@PathVariable("adminPath") String adminPath,
                                             @PathVariable("id") Integer id) throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
        // 判断是否有子节点
        if (groupService.isExistChild(id)){
            return ResponseResult.fail("该地区下存在子地区，无法删除");
        }
        groupService.updateGroupBindNode(id);
        // 删除地区
        if (groupService.removeById(id)){
            return ResponseResult.ok("删除成功");
        }
        return ResponseResult.fail("删除失败");
    }

    /**
    * @Author: mryunqi
    * @Description: 修改地区
    * @DateTime: 2023/8/15 23:40
    */
    @AdminApiCheck
    @RequestMapping(value = "/{adminPath}/updateArea",method = {RequestMethod.PUT,RequestMethod.POST})
    public ResponseResult<String> updateArea(@PathVariable("adminPath") String adminPath,
                                             @RequestBody Group group) throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
        // 修改地区
        if (groupService.updateById(group)){
            return ResponseResult.ok("修改成功");
        }
        return ResponseResult.fail("修改失败");
    }

    /**
    * @Author: mryunqi
    * @Description: 分页查询地区
    * @DateTime: 2023/8/15 23:43
    */
    @AdminApiCheck
    @GetMapping("/{adminPath}/getAreaList")
    public ResponseResult<Object> getAreaList(@PathVariable("adminPath") String adminPath,
                                              @RequestParam(value = "page",defaultValue = "1") Integer page,
                                              @RequestParam(value = "limit",defaultValue = "20") Integer limit) throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
        return ResponseResult.ok(groupService.selectGroupPage(page,limit));
    }

    /**
    * @Author: mryunqi
    * @Description: 查询指定id的地区
    * @DateTime: 2023/8/15 23:44
    */
    @AdminApiCheck
    @GetMapping("/{adminPath}/getArea")
    public ResponseResult<Object> getArea(@PathVariable("adminPath") String adminPath,
                                          @RequestParam(name = "id") Integer id) throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
        return ResponseResult.ok(groupService.getById(id));
    }

    /**
    * @Author: mryunqi
    * @Description: 添加某节点到指定地区
    * @DateTime: 2023/8/16 16:36
    */
    @AdminApiCheck
    @RequestMapping(value = "/{adminPath}/addNodeToArea",method = {RequestMethod.PUT,RequestMethod.POST})
    public ResponseResult<String> addNodeToArea(@PathVariable("adminPath") String adminPath,
                                                @RequestBody Master node) throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
        // 判断节点是否存在
        Integer nodeId = node.getId();
        if (masterService.getById(nodeId) == null){
            return ResponseResult.fail("节点不存在");
        }
        // 判断地区是否存在
        Integer groupId = node.getGroup();
        if (groupService.getById(groupId) == null){
            return ResponseResult.fail("地区不存在");
        }
        // 更新节点地区
        if (masterService.updateById(node)){
            return ResponseResult.ok("添加成功");
        }
        return ResponseResult.fail("添加失败");
    }
}
