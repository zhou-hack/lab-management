package com.labmanage.roles.vo;

/**
 * 可被授权的学生选项（已注册且启用中的 STUDENT 用户）。
 */
public record StudentOptionVO(
        String id,
        String realName,
        String personNo
) {
}
