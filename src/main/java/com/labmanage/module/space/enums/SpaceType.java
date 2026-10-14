package com.labmanage.module.space.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * 场地类型（biz_space.space_type）。
 * <p>
 * 一期按业务确认定稿为两值：教室 / 实训室；如需扩展（机房、多媒体教室等）
 * 后续在枚举与建表 CHECK 中同步登记。code 落库 VARCHAR(32)，接口以 code 序列化。
 */
public enum SpaceType {

    CLASSROOM("CLASSROOM", "教室"),
    TRAINING_ROOM("TRAINING_ROOM", "实训室");

    @EnumValue
    private final String code;

    private final String displayName;

    SpaceType(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 非法 code 返回 null，由参数校验（@NotNull）转 40000。
     */
    @JsonCreator
    public static SpaceType fromCode(String code) {
        return Arrays.stream(values())
                .filter(t -> t.code.equals(code))
                .findFirst()
                .orElse(null);
    }
}
