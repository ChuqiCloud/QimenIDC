package com.chuqiyun.proxmoxveams.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chuqiyun.proxmoxveams.dto.IpParams;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.entity.Ipstatus;

import java.util.List;

/**
 * (Ipstatus)表服务接口
 *
 * @author mryunqi
 * @since 2023-07-02 23:16:38
 */
public interface IpstatusService extends IService<Ipstatus> {

    Integer insertIpstatus(IpParams ipParams);

    Page<Ipstatus> getIpstatusPage(Integer page, Integer limit);

    Page<Ipstatus> getIpstatusPage(Integer page, Integer limit, QueryWrapper<Ipstatus> queryWrapper);

    boolean updateIpStatus(Ipstatus ipstatus);

    List<Integer> getAllId();

    default Ipstatus getIpStatusMaxByNodeId(Integer nodeId, Integer natippool, Integer excludeId) {
        return null;
    }

    UnifiedResultDto<Object> deleteIppoolById(Long id);
}

