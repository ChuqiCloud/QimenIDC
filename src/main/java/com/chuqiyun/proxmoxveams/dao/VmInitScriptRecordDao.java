package com.chuqiyun.proxmoxveams.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chuqiyun.proxmoxveams.entity.VmInitScriptRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VmInitScriptRecordDao extends BaseMapper<VmInitScriptRecord> {
}
