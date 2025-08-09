package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.dao.IpstatusDao;
import com.chuqiyun.proxmoxveams.dto.IpParams;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.entity.Ippool;
import com.chuqiyun.proxmoxveams.entity.Ipstatus;
import com.chuqiyun.proxmoxveams.service.IppoolService;
import com.chuqiyun.proxmoxveams.service.IpstatusService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * (Ipstatus)表服务实现类
 *
 * @author mryunqi
 * @since 2023-07-02 23:16:38
 */
@Service("ipstatusService")
public class IpstatusServiceImpl extends ServiceImpl<IpstatusDao, Ipstatus> implements IpstatusService {
    @Resource
    private IppoolService ippoolService;
    /**
    * @Author: mryunqi
    * @Description: 插入IP组信息
    * @DateTime: 2023/7/2 23:20
    * @Params: IpParams ipParams
    * @Return Integer
    */
    @Override
    public Integer insertIpstatus(IpParams ipParams) {
        Ipstatus ipstatus = new Ipstatus();
        ipstatus.setName(ipParams.getPoolName());
        ipstatus.setIpType(ipParams.getIpType());
        ipstatus.setGateway(ipParams.getGateway());
        ipstatus.setMask(ipParams.getMask());
        ipstatus.setDns1(ipParams.getDns1());
        ipstatus.setDns2(ipParams.getDns2());
        ipstatus.setNodeid(ipParams.getNodeId());
        return this.save(ipstatus) ? ipstatus.getId() : null;
    }

    /**
    * @Author: mryunqi
    * @Description: 分页获取IP组信息
    * @DateTime: 2023/7/4 14:15
    * @Params: Integer page, Integer limit
    * @Return Page<Ipstatus>
    */
    @Override
    public Page<Ipstatus> getIpstatusPage(Integer page, Integer limit) {
        Page<Ipstatus> ipstatusPage = new Page<>(page,limit);
        return this.page(ipstatusPage);
    }

    /**
    * @Author: mryunqi
    * @Description: 分页获取IP组信息附加条件
    * @DateTime: 2023/7/4 14:16
    * @Params: Integer page, Integer limit, QueryWrapper<Ipstatus> queryWrapper
    * @Return Page<Ipstatus>
    */
    @Override
    public Page<Ipstatus> getIpstatusPage(Integer page, Integer limit, QueryWrapper<Ipstatus> queryWrapper){
        Page<Ipstatus> ipstatusPage = new Page<>(page,limit);
        return this.page(ipstatusPage,queryWrapper);
    }
    /**
    * @Author: mryunqi
    * @Description: 修改IP池信息
    * @DateTime: 2023/7/4 16:37
    * @Params: Ipstatus ipstatus
    * @Return boolean
    */
    @Override
    public boolean updateIpStatus(Ipstatus ipstatus) {
        return this.updateById(ipstatus);
    }
    /**
    * @Author: mryunqi
    * @Description: 获取所有ID
    * @DateTime: 2023/7/4 21:41
    */
    @Override
    public List<Integer> getAllId() {
        return this.lambdaQuery().select(Ipstatus::getId).list().stream().map(Ipstatus::getId).collect(Collectors.toList());
    }
    /**
    * @Author: mryunqi
    * @Description: 获取指定nodeID下available最大的IP组
    * @DateTime: 2023/7/6 18:24
    * @Params: Integer nodeId
    * @Return Ipstatus natippool
    */
    @Override
    public Ipstatus getIpStatusMaxByNodeId(Integer nodeId, Integer natippool, Integer excludeId) {
        if (natippool == null)
        {
            if (excludeId != null) {
                return this.lambdaQuery().eq(Ipstatus::getNodeid, nodeId).ne(Ipstatus::getId, excludeId).orderByDesc(Ipstatus::getAvailable).last("limit 1").one();
            } else {
                return this.lambdaQuery().eq(Ipstatus::getNodeid, nodeId).orderByDesc(Ipstatus::getAvailable).last("limit 1").one();
            }
        }
        else {
            return this.lambdaQuery().eq(Ipstatus::getNodeid,nodeId).eq(Ipstatus::getId,natippool).orderByDesc(Ipstatus::getAvailable).last("limit 1").one();
        }
    }

    /**
     * @Author: mryunqi
     * @Description: 根据id删除ip池
     * @DateTime: 2023/10/31 22:17
     * @Params: Long id IP池id
     * @Return UnifiedResultDto<Object> 删除结果
     */
    @Override
    public UnifiedResultDto<Object> deleteIppoolById(Long id) {
        QueryWrapper<Ipstatus> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", id);
        // 判断是否存在
        Ipstatus ipStatus = this.getOne(queryWrapper);
        if (ipStatus == null) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_IP_POOL_NOT_EXIST, null);
        }
        QueryWrapper<Ippool> ippoolQueryWrapper = new QueryWrapper<>();
        ippoolQueryWrapper.eq("status", 1); // 1为被使用
        int count = ippoolService.getIpCountByCondition(ippoolQueryWrapper); // 获取被使用的IP数量
        if (count > 0) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_IP_POOL_HAS_USED, null);
        }
        ippoolQueryWrapper.clear(); // 清空条件
        ippoolQueryWrapper.eq("pool_id", ipStatus.getId()); // 匹配该IP池下的所有IP
        // 删除该IP池下的所有IP
        try {
            ippoolService.deleteIppoolByCondition(ippoolQueryWrapper);
        } catch (Exception e) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_DELETE_IP_POOL_IP_LIST_FAILED, e.getMessage());
        }

        boolean remove = this.remove(queryWrapper);
        if (remove) {
            return new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, null);
        } else {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_DELETE_IP_POOL_FAILED, null);
        }
    }


}

