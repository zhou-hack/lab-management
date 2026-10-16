package com.labmanage.roles.vo;

/**
 * 可授权管理员下拉选项
 */
public record AdminOptionVO(
        String userId,
        String realName,
        String accountName
) {
}
