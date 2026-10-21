package com.labmanage.module.booking.vo;

/**
 * 上课老师选项（预约表单「老师」下拉数据源；id 雪花 ID 字符串承载）。
 */
public record TeacherOptionVO(
        String id,
        String realName
) {
}