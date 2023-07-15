package com.chuqiyun.proxmoxveams.utils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * @author mryunqi
 * @date 2023/5/12
 */
public class TimeUtil {
    public static long timeDifference(long regDate,long nextDueDate) {

        // 时间戳转换为LocalDate对象
        LocalDate date1 = LocalDate.ofEpochDay(regDate / (24 * 60 * 60));
        LocalDate date2 = LocalDate.ofEpochDay(nextDueDate / (24 * 60 * 60));

        // 计算天数差异
        long daysBetween = ChronoUnit.DAYS.between(date1, date2);

        // 处理不足一天的情况
        if (regDate % (24 * 60 * 60) > regDate % (24 * 60 * 60)) {
            daysBetween++;
        }

        return daysBetween;
    }
}
