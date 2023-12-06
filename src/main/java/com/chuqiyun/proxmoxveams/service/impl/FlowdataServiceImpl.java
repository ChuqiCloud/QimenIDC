package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.FlowdataDao;
import com.chuqiyun.proxmoxveams.entity.Flowdata;
import com.chuqiyun.proxmoxveams.service.FlowdataService;
import org.springframework.stereotype.Service;

/**
 * (Flowdata)表服务实现类
 *
 * @author mryunqi
 * @since 2023-12-03 20:21:54
 */
@Service("flowdataService")
public class FlowdataServiceImpl extends ServiceImpl<FlowdataDao, Flowdata> implements FlowdataService {
    /**
    * @Author: mryunqi
    * @Description: 插入流量临表数据
    * @DateTime: 2023/12/3 20:32
    * @Params: Flowdata flowdata
    * @Return Boolean true=成功;false=失败
    */
    @Override
    public Boolean insertFlowdata(Flowdata flowdata) {
        return this.save(flowdata);
    }

    /**
    * @Author: mryunqi
    * @Description: 根据ID删除流量临表数据
    * @DateTime: 2023/12/3 20:32
    * @Params: Integer id
    * @Return Boolean true=成功;false=失败
    */
    @Override
    public Boolean deleteFlowdataById(Integer id) {
        return this.removeById(id);
    }

    /**
    * @Author: mryunqi
    * @Description: 更新流量临表数据
    * @DateTime: 2023/12/3 20:33
    * @Params: Flowdata flowdata
    * @Return Boolean true=成功;false=失败
    */
    @Override
    public Boolean updateFlowdata(Flowdata flowdata) {
        return this.updateById(flowdata);
    }

    /**
    * @Author: mryunqi
    * @Description: 分页查询流量临表数据
    * @DateTime: 2023/12/3 20:34
    * @Params: Integer page,Integer size
    * @Return  Page<Flowdata>
    */
    @Override
    public Page<Flowdata> selectFlowdataByPage(Integer page, Integer size) {
        Page<Flowdata> flowdataPage = new Page<>(page, size);
        return this.page(flowdataPage);
    }

    /**
    * @Author: mryunqi
    * @Description: 分页带条件查询流量临表数据
    * @DateTime: 2023/12/3 20:35
    * @Params: Integer page,Integer size,QueryWrapper<Flowdata> queryWrapper
    * @Return Page<Flowdata>
    */
    @Override
    public Page<Flowdata> selectFlowdataByPageAndWrapper(Integer page, Integer size, QueryWrapper<Flowdata> queryWrapper) {
        Page<Flowdata> flowdataPage = new Page<>(page, size);
        return this.page(flowdataPage,queryWrapper);
    }

    /**
    * @Author: mryunqi
    * @Description: 获取指定hostid的最新流量数据
    * @DateTime: 2023/12/3 21:12
    * @Params: Integer hostid
    * @Return Flowdata
    */
    @Override
    public Flowdata selectFlowdataByHostid(Integer hostid) {
        QueryWrapper<Flowdata> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("hostid",hostid);
        queryWrapper.orderByDesc("create_date");//按照create_date降序排序
        queryWrapper.last("limit 1");
        return this.getOne(queryWrapper);
    }

}

