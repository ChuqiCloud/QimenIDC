package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.CpuinfoDao;
import com.chuqiyun.proxmoxveams.entity.Cpuinfo;
import com.chuqiyun.proxmoxveams.service.CpuinfoService;
import org.springframework.stereotype.Service;

/**
 * (Cpuinfo)表服务实现类
 *
 * @author mryunqi
 * @since 2023-08-19 12:59:59
 */
@Service("cpuinfoService")
public class CpuinfoServiceImpl extends ServiceImpl<CpuinfoDao, Cpuinfo> implements CpuinfoService {
    /**
    * @Author: mryunqi
    * @Description: 新增cpu信息模型
    * @DateTime: 2023/8/19 23:13
    * @Params: Cpuinfo cpuinfo cpu信息模型
    * @Return Boolean
    */
    @Override
    public Boolean addCpuInfo(Cpuinfo cpuinfo) {
        return this.save(cpuinfo);
    }

    /**
    * @Author: mryunqi
    * @Description: 分页查询cpu信息模型
    * @DateTime: 2023/8/19 23:21
    * @Params: Integer page 当前页码, Integer limit 每页条数
    * @Return Page<Cpuinfo> 分页对象
    */
    @Override
    public Page<Cpuinfo> selectCpuInfoPage(Integer page, Integer limit) {
        Page<Cpuinfo> cpuinfoPage = new Page<>(page,limit);
        return this.page(cpuinfoPage);
    }

    /**
    * @Author: mryunqi
    * @Description: 将cpu信息模型转换为cpu信息实体字符串
    * @DateTime: 2023/8/20 17:07
    * @Params: Cpuinfo cpuinfo cpu信息模型
    * @Return String cpu信息实体字符串
    */
    @Override
    public String cpuinfoToString(Cpuinfo cpuinfo) {
        StringBuilder args = new StringBuilder();

        appendField(args, "model_id", cpuinfo.getName());
        appendIntegerField(args, "family", cpuinfo.getFamily());
        appendIntegerField(args, "model", cpuinfo.getModel());
        appendIntegerField(args, "stepping", cpuinfo.getStepping());
        appendField(args, "level", cpuinfo.getLevel());
        appendField(args, "xlevel", cpuinfo.getXlevel());
        appendField(args, "vendor", cpuinfo.getVendor());
        appendBooleanField(args, "l3_cache", cpuinfo.getL3Cache());
        appendField(args, null, cpuinfo.getOther());

        // 删除最后一个逗号
        if (args.length() > 0 && args.charAt(args.length() - 1) == ',') {
            args.setLength(args.length() - 1);
        }

        return args.toString();
    }

    private void appendField(StringBuilder builder, String fieldName, String fieldValue) {
        if (fieldValue != null && !fieldValue.isEmpty()) {
            if (fieldName != null) {
                builder.append(fieldName).append('=');
            }
            builder.append(fieldValue).append(',');
        }
    }
    private void appendIntegerField(StringBuilder builder, String fieldName, Integer fieldValue) {
        if (fieldValue != null) {
            if (fieldName != null) {
                builder.append(fieldName).append('=');
            }
            builder.append(fieldValue).append(',');
        }
    }
    private void appendBooleanField(StringBuilder builder, String fieldName, boolean fieldValue) {
        if (fieldName != null) {
            builder.append(fieldName).append('=');
            builder.append(fieldValue ? "true" : "false").append(',');
        }
    }

}

