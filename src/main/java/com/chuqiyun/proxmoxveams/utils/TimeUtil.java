package com.chuqiyun.proxmoxveams.utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    /**
    * @Author: mryunqi
    * @Description: 将10位时间戳转换为13位时间戳
    * @DateTime: 2023/7/17 22:37
    * @Params: long timestamp 10位时间戳
    * @Return long 13位时间戳
    */
    public static long tenToThirteen(long timestamp){
        return timestamp * 1000;
    }

    /**
    * @Author: mryunqi
    * @Description: 判断时间戳是否小于当前时间戳30天
    * @DateTime: 2023/12/6 18:27
    * @Params: long timestamp 时间戳
    * @Return boolean true:小于30天 false:大于30天
    */
    public static boolean isLessThanThirtyDays(long timestamp){
        long now = System.currentTimeMillis();
        // 判断该时间戳是否是本月的
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault());// 转换为 LocalDateTime

        // 获取当前月份的第一天
        LocalDateTime firstDayOfMonth = LocalDateTime.of(dateTime.getYear(), dateTime.getMonth(), 1, 0, 0);

        // 转换为时间戳
        long firstDayOfMonthTimestamp = firstDayOfMonth.toInstant(ZoneId.systemDefault().getRules().getOffset(firstDayOfMonth)).toEpochMilli();
        return timestamp < firstDayOfMonthTimestamp;
    }

    /**
     * @Author: 星禾
     * @Description: 判断时间戳是否为当天
     * @DateTime: 2025/12/1 15:52
     * @Params: long timestamp 时间戳
     * @Return boolean true:是当天 false:不是当天
     */
    public static boolean isSameDay(long timestamp) {
        // 获取当前时间的毫秒数
        long now = System.currentTimeMillis();
        // 将时间戳转换为LocalDate（系统默认时区）
        LocalDate inputDate = Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        LocalDate currentDate = Instant.ofEpochMilli(now)
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        // 比较两个日期是否相同
        return inputDate.equals(currentDate);
    }
}
