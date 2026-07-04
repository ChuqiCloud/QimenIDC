package com.chuqiyun.proxmoxveams.service;

import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.entity.VmInitScript;
import com.chuqiyun.proxmoxveams.entity.VmInitScriptRecord;

import java.util.List;

public interface VmInitScriptBusinessService {
    UnifiedResultDto<Object> addScript(VmInitScript script);

    UnifiedResultDto<Object> updateScript(VmInitScript script);

    UnifiedResultDto<Object> deleteScript(Integer scriptId);

    UnifiedResultDto<Object> validateScripts(List<Integer> scriptIds);

    void createRunTasks(Integer hostId, Integer vmid, Integer nodeId, List<Integer> scriptIds, String triggerType);

    UnifiedResultDto<Object> createManualRunTasks(Integer hostId, List<Integer> scriptIds);

    boolean executeTask(Task task);

    List<Integer> normalizeScriptIds(Integer scriptId, List<Integer> scriptIds);

    List<VmInitScriptRecord> getRecords(Integer hostId, Integer scriptId);
}
