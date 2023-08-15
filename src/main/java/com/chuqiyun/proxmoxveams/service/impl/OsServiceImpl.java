package com.chuqiyun.proxmoxveams.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.OsDao;
import com.chuqiyun.proxmoxveams.dto.OsNodeStatus;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Os;
import com.chuqiyun.proxmoxveams.dto.OsParams;
import com.chuqiyun.proxmoxveams.service.ConfigService;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.OsService;
import com.chuqiyun.proxmoxveams.utils.ClientApiUtil;
import com.chuqiyun.proxmoxveams.utils.ModUtil;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * (Os)表服务实现类
 *
 * @author mryunqi
 * @since 2023-07-08 15:58:22
 */
@Service("osService")
public class OsServiceImpl extends ServiceImpl<OsDao, Os> implements OsService {
    @Resource
    private MasterService masterService;
    @Resource
    private ConfigService configService;
    /**
    * @Author: mryunqi
    * @Description: 查询指定名称os
    * @DateTime: 2023/7/14 21:20
    * @Params: String name 系统名称
    * @Return  Os
    */
    @Override
    public Os selectOsByName(String name) {
        QueryWrapper<Os> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("name",name);
        return this.getOne(queryWrapper);
    }
    /**
    * @Author: mryunqi
    * @Description: 查询指定fileName的os
    * @DateTime: 2023/7/21 23:45
    * @Params: String fileName 文件名
    * @Return Os
    */
    @Override
    public Os selectOsByFileName(String fileName) {
        QueryWrapper<Os> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("file_name",fileName);
        return this.getOne(queryWrapper);
    }
    /**
    * @Author: mryunqi
    * @Description: 查询指定名称的os是否存在
    * @DateTime: 2023/7/21 23:40
    * @Params: String name 系统名称（别称）
    * @Return boolean
    */
    @Override
    public boolean isExistOsByName(String name){
        return this.selectOsByName(name)!=null;
    }
    /**
    * @Author: mryunqi
    * @Description: 查询指定fileName的os是否存在
    * @DateTime: 2023/7/21 23:45
    * @Params: String fileName 文件名
    * @Return boolean
    */
    @Override
    public boolean isExistOsByFileName(String fileName){
        return this.selectOsByFileName(fileName)!=null;
    }
    /**
    * @Author: mryunqi
    * @Description: 获取指定名称的os的所有节点信息
    * @DateTime: 2023/7/16 12:20
    * @Params: String osName 系统名称
    * @Return JSONArray
    */
    @Override
    public JSONArray selectOsByOsName(String osName){
        JSONArray jsonArray = new JSONArray();
        Os os = selectOsByFileName(osName);
        if (os!=null){
            // 已安装
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("status",os.getStatus());
            jsonObject.put("osId",os.getId());
            jsonObject.put("size",os.getSize());
            jsonObject.put("path",os.getPath());
            jsonObject.put("createTime",os.getCreateTime());
            jsonArray.add(jsonObject);
        }
        return jsonArray;
    }
    /**
    * @Author: mryunqi
    * @Description: 插入os
    * @DateTime: 2023/7/21 23:35
    * @Params: OsParams osParams 系统参数
    * @Return HashMap<String,Object> resultMap
    */
    @Override
    public HashMap<String, Object> insertOs(OsParams osParams){
        HashMap<String,Object> resultMap = new HashMap<>();
        // 判断name是否已存在
        String osNameAlias = osParams.getName();
        if (this.isExistOsByName(osNameAlias)){
            resultMap.put("code",1);
            resultMap.put("msg","系统名称已存在");
            return resultMap;
        }
        // 判断fileName是否存在
        String fileName = osParams.getFileName();
        if (this.isExistOsByFileName(fileName)){
            resultMap.put("code",1);
            resultMap.put("msg","文件名已存在");
            return resultMap;
        }
        // 判断type是否为空
        String type = osParams.getType();
        if (type==null|| "".equals(type)){
            resultMap.put("code", 1);
            resultMap.put("msg", "OS类型不能为空");
            return resultMap;
        }
        // 如果镜像架构为空，则默认为x86_64
        String arch = osParams.getArch();
        if (arch==null|| "".equals(arch)){
            arch = "x86_64";
        }
        // 判断镜像架构是否为x86_64,aarch64
        if (!"x86_64".equals(arch)&&!"aarch64".equals(arch)){
            resultMap.put("code", 1);
            resultMap.put("msg", "镜像架构参数错误");
            return resultMap;
        }
        // 如果type为linux，镜像操作类型不能为空
        String osType = osParams.getOsType();
        if ("linux".equals(type)){
            if (osType==null|| "".equals(osType)){
                resultMap.put("code", 1);
                resultMap.put("msg", "镜像操作类型不能为空");
                return resultMap;
            }
        }
        // 如果为自动下载，则判断url是否为空
        int downType = osParams.getDownType();
        // 先判断是否为0或者1
        if (downType != 0 && downType != 1){
            resultMap.put("code", 1);
            resultMap.put("msg", "下载类型错误");
            return resultMap;
        }
        String url = osParams.getUrl();
        String size = "0MB";
        if (downType==0){
            if (url==null|| "".equals(url)){
                resultMap.put("code", 1);
                resultMap.put("msg", "url不能为空");
                return resultMap;
            }
            // 获取文件大小
            size = ModUtil.formatFileSize(ModUtil.getUrlFileSize(url));
        }
        // 判断path是否为空或者为default
        String path = osParams.getPath();
        if (path==null|| "".equals(path)||"default".equals(path)){
            path = "/home/images";
        }
        // 默认cloud为0
        int cloud = osParams.getCloud();
        if (cloud != 0 && cloud != 1){
            cloud = 0;
        }
        // 创建os实体
        Os os = new Os();
        os.setName(osNameAlias);
        os.setFileName(fileName);
        os.setType(type);
        os.setArch(arch);
        os.setOsType(osType);
        os.setDownType(downType);
        os.setUrl(url);
        os.setSize(size);
        os.setPath(path);
        os.setStatus(0);
        os.setCloud(cloud);
        os.setCreateTime(System.currentTimeMillis());
        // 插入数据库
        boolean result = this.save(os);
        if (result){
            resultMap.put("code",0);
            resultMap.put("msg","添加成功");
            return resultMap;
        }else {
            resultMap.put("code",1);
            resultMap.put("msg","添加失败");
            return resultMap;
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 分页查询os
    * @DateTime: 2023/7/22 21:32
    * @Params: int page 页码 int limit 每页数量
    * @Return Page<Os> osPage 分页对象
    */
    @Override
    public Page<Os> selectOsByPage(int page, int limit){
        Page<Os> osPage = new Page<>(page,limit);
        QueryWrapper<Os> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("create_time");
        return this.page(osPage,queryWrapper);
    }
    /**
    * @Author: mryunqi
    * @Description: 分页查询os附加条件
    * @DateTime: 2023/7/22 21:35
    * @Params: int page 页码 int limit 每页数量 QueryWrapper<Os> osQueryWrapper 条件构造器
    * @Return Page<Os> osPage 分页对象
    */
    @Override
    public Page<Os> selectOsByPage(int page, int limit, QueryWrapper<Os> osQueryWrapper) {
        Page<Os> osPage = new Page<>(page,limit);
        osQueryWrapper.orderByDesc("create_time");
        return this.page(osPage,osQueryWrapper);
    }

    /**
    * @Author: mryunqi
    * @Description: 下载os到节点
    * @DateTime: 2023/7/29 22:23
    * @Params: Integer osId 镜像id ，Integer nodeId 节点id
    * @Return boolean result 下载结果
    */
    @Override
    public boolean downloadOs(Integer osId,Integer nodeId){
        Os os = this.getById(osId);
        Master node = masterService.getById(nodeId);
        if (os==null||node==null){
            return false;
        }
        String osUrl = os.getUrl();
        String osPath = "/home/images/";
        String ip = node.getHost();
        String token = configService.getToken();
        return ClientApiUtil.downloadFile(ip,token,osUrl,osPath);
    }

    /**
    * @Author: mryunqi
    * @Description: 获取下载进度
    * @DateTime: 2023/8/14 16:48
    * @Params:  Integer osId 镜像id ，Integer nodeId 节点id
    * @Return  JSONObject 下载进度
    */
    @Override
    public JSONObject getDownloadProgress(Integer osId,Integer nodeId){
        Os os = this.getById(osId);
        Master node = masterService.getById(nodeId);
        if (os==null||node==null){
            return null;
        }
        String osUrl = os.getUrl();
        String osPath = "/home/images/";
        String ip = node.getHost();
        String token = configService.getToken();
        return ClientApiUtil.getDownloadProgress(ip,token,osUrl,osPath).getJSONObject("data");
    }

    /**
    * @Author: mryunqi
    * @Description: 删除宿主机上的os
    * @DateTime: 2023/8/15 19:46
    * @Params: String osName 镜像名称，Integer nodeId 节点id
    * @Return boolean result 删除结果
    */
    @Override
    public boolean deleteNodeOs(String osName, Integer nodeId){
        Master node = masterService.getById(nodeId);
        String ip = node.getHost();
        String token = configService.getToken();
        return ClientApiUtil.deleteOsFile(ip,token,osName);
    }
    
    /**
    * @Author: mryunqi
    * @Description: 删除数据库中的os
    * @DateTime: 2023/8/15 19:54
    * @Params: Integer osId 镜像id
    * @Return boolean result 删除结果
    */
    @Override
    public boolean deleteOs(Integer osId){
        Os os = this.getById(osId);
        if (os==null){
            return false;
        }
        Map<String,Object> map = os.getNodeStatus();
        if (map!=null){
            // 删除宿主机上的os
            String osName = os.getFileName();
            for (String key : map.keySet()) {
                Object osNodeStatusObj = map.get(key);
                OsNodeStatus osNodeStatus = JSONObject.parseObject(JSONObject.toJSONString(osNodeStatusObj), OsNodeStatus.class);
                // 如果为下载中，返回false
                if (osNodeStatus.getStatus()==1){
                    return false;
                }
                // 如果为2,3则删除宿主机上的os
                if (osNodeStatus.getStatus()==2||osNodeStatus.getStatus()==3){
                    boolean result = this.deleteNodeOs(osName,osNodeStatus.getNodeId());
                    if (!result){
                        return false;
                    }
                }
            }
        }
        return this.removeById(osId);
    }

}

