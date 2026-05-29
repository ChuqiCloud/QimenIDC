package com.chuqiyun.proxmoxveams.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chuqiyun.proxmoxveams.entity.VmResourceRank;

import java.util.List;

/**
 * VM resource rank service.
 *
 * @author codex
 * @since 2026-05-29
 */
public interface VmResourceRankService extends IService<VmResourceRank> {
    void refreshRank();

    List<VmResourceRank> getRank(String rankType);
}
