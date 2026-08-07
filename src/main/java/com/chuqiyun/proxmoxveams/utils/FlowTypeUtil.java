package com.chuqiyun.proxmoxveams.utils;

import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * @Author: 星禾
 * @Description: 虚拟机流量计费类型处理工具
 * @DateTime: 2026/8/7 10:15
 */
public final class FlowTypeUtil {
    public static final String FLOW_TYPE_IN = "in";
    public static final String FLOW_TYPE_OUT = "out";
    public static final String FLOW_TYPE_IN_OUT = "in+out";

    private FlowTypeUtil() {
    }

    public static boolean isValid(String flowType) {
        return StringUtils.isBlank(flowType)
                || FLOW_TYPE_IN.equalsIgnoreCase(flowType.trim())
                || FLOW_TYPE_OUT.equalsIgnoreCase(flowType.trim())
                || FLOW_TYPE_IN_OUT.equalsIgnoreCase(flowType.trim());
    }

    public static String normalize(String flowType) {
        if (StringUtils.isBlank(flowType)) {
            return FLOW_TYPE_IN_OUT;
        }
        String normalizedFlowType = flowType.trim().toLowerCase(Locale.ROOT);
        if (FLOW_TYPE_IN.equals(normalizedFlowType)
                || FLOW_TYPE_OUT.equals(normalizedFlowType)
                || FLOW_TYPE_IN_OUT.equals(normalizedFlowType)) {
            return normalizedFlowType;
        }
        return FLOW_TYPE_IN_OUT;
    }

    public static BigDecimal calculate(BigDecimal netin, BigDecimal netout, String flowType) {
        BigDecimal normalizedNetin = netin == null ? BigDecimal.ZERO : netin;
        BigDecimal normalizedNetout = netout == null ? BigDecimal.ZERO : netout;
        String normalizedFlowType = normalize(flowType);
        if (FLOW_TYPE_IN.equals(normalizedFlowType)) {
            return normalizedNetin;
        }
        if (FLOW_TYPE_OUT.equals(normalizedFlowType)) {
            return normalizedNetout;
        }
        return normalizedNetin.add(normalizedNetout);
    }
}
