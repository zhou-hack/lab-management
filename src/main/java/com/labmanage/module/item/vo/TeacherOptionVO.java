package com.labmanage.module.item.vo;

import com.labmanage.module.item.mapper.SysUserRefMapper;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 老师候选选项 VO（老师预约选择归属老师用）
 */
@Schema(description = "老师候选选项")
public record TeacherOptionVO(
        @Schema(description = "老师用户 ID", example = "1234567890123456789")
        String id,

        @Schema(description = "老师姓名", example = "张老师")
        String name
) {

    public static TeacherOptionVO from(SysUserRefMapper.UserRef ref) {
        return new TeacherOptionVO(String.valueOf(ref.userId()), ref.realName());
    }
}
