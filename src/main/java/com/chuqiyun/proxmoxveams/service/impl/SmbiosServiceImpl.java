package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.SmbiosDao;
import com.chuqiyun.proxmoxveams.entity.Smbios;
import com.chuqiyun.proxmoxveams.service.SmbiosService;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * (Smbios)表服务实现类
 *
 * @author mryunqi
 * @since 2023-08-19 13:00:19
 */
@Service("smbiosService")
public class SmbiosServiceImpl extends ServiceImpl<SmbiosDao, Smbios> implements SmbiosService {
    /**
    * @Author: mryunqi
    * @Description: 新增smbios信息模型
    * @DateTime: 2023/8/20 15:17
    * @Params: Smbios smbios smbios信息模型
    * @Return Boolean
    */
    @Override
    public Boolean addSmbiosInfo(Smbios smbios) {
        return this.save(smbios);
    }

    /**
    * @Author: mryunqi
    * @Description: 分页查询smbios信息模型
    * @DateTime: 2023/8/20 15:21
    * @Params: Integer page 当前页码, Integer limit 每页条数
    * @Return Page<Smbios> 分页对象
    */
    @Override
    public Page<Smbios> selectSmbiosInfoPage(Integer page, Integer limit) {
        Page<Smbios> smbiosPage = new Page<>(page,limit);
        return this.page(smbiosPage);
    }

    /**
     * @Author: mryunqi
     * @Description: 将smbios信息模型转换为smbios信息实体字符串
     * @DateTime: 2023/8/20 17:07
     * @Params: Smbios smbios smbios信息模型
     * @Return StringBuilder smbios信息实体字符串
     */
    @Override
    public StringBuilder smbiosToStringArgs(Smbios smbios){
        StringBuilder args = new StringBuilder();
        args.append(" -smbios ").append("type=").append(smbios.getType()).append(',');
        Map<String, String> smbiosMap = smbios.getModel();
        for (Map.Entry<String, String> entry : smbiosMap.entrySet()) {
            appendField(args, entry.getKey(), entry.getValue());
        }
        return args;
    }
    private void appendField(StringBuilder builder, String fieldName, String fieldValue) {
        if (fieldValue != null && !fieldValue.isEmpty()) {
            if (fieldName != null) {
                builder.append(fieldName).append('=');
            }
            builder.append(fieldValue).append(',');
        }
    }

}

