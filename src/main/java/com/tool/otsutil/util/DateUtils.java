package com.tool.otsutil.util;

import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;

import java.util.Date;

/**
 * 日期工具类，封装常用的日期计算方法
 */
public class DateUtils {
    
    private DateUtils() {
        // 私有构造方法，防止实例化
    }
    
    /**
     * 获取当前月份，格式：yyyy-MM
     * @return 当前月份
     */
    public static String getCurrentMonth() {
        return DateUtil.format(new Date(), "yyyy-MM");
    }
    
    /**
     * 获取当前日期，格式：yyyy-MM-dd
     * @return 当前日期
     */
    public static String getCurrentDate() {
        return DateUtil.format(new Date(), "yyyy-MM-dd");
    }
    
    /**
     * 获取指定天数前的日期，格式：yyyy-MM-dd
     * @param days 天数，正数表示未来，负数表示过去
     * @return 指定天数前的日期
     */
    public static String getDateBefore(int days) {
        return DateUtil.format(DateUtil.offset(new Date(), DateField.DAY_OF_MONTH, -days), "yyyy-MM-dd");
    }
    
    /**
     * 获取昨天的日期，格式：yyyy-MM-dd
     * @return 昨天的日期
     */
    public static String getYesterday() {
        return getDateBefore(1);
    }
    
    /**
     * 获取上周一的日期，格式：yyyy-MM-dd
     * @return 上周一的日期
     */
    public static String getLastMonday() {
        DateTime currentDate = DateUtil.date();
        return DateUtil.format(DateUtil.offsetDay(DateUtil.beginOfWeek(currentDate, true), -7), "yyyy-MM-dd");
    }
    
    /**
     * 获取上周日的日期，格式：yyyy-MM-dd
     * @return 上周日的日期
     */
    public static String getLastSunday() {
        DateTime currentDate = DateUtil.date();
        return DateUtil.format(DateUtil.offsetDay(DateUtil.endOfWeek(currentDate, true), -7), "yyyy-MM-dd");
    }
    
    /**
     * 获取指定日期的月份，格式：yyyy-MM
     * @param date 日期对象
     * @return 指定日期的月份
     */
    public static String getMonth(Date date) {
        return DateUtil.format(date, "yyyy-MM");
    }
    
    /**
     * 获取指定日期的格式化为yyyy-MM-dd
     * @param date 日期对象
     * @return 格式化后的日期字符串
     */
    public static String formatDate(Date date) {
        return DateUtil.format(date, "yyyy-MM-dd");
    }
}
