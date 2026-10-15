package com.labmanage.module.equipment.vo;

/**
 * 保管人下拉选项
 *
 * @param userId    用户 ID（字符串序列化，避免 JS 精度损失）
 * @param realName 姓名
 */
public record CustodianOptionVO(String userId, String realName) {
}
