package com.labmanage.module.space.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * 场地状态（biz_space.status，落库）。
 * <p>
 * 单表模式下状态直接持久化（取代旧四表设计的"派生不入库"方案）：
 * AVAILABLE 可正常预约使用；MAINTAINING 维护中禁止新预约；CLOSED 关闭（停用）。
 * 派生占用情况（某时段是否被预约占用）由预约模块按时间窗判断，不属于本字段。
 */
public enum SpaceStatus {

    AVAILABLE("AVAILABLE", "可用"),
    MAINTAINING("MAINTAINING", "维护中"),
    CLOSED("CLOSED", "关闭");

    @EnumValue
    private final String code;

    private final String displayName;

    SpaceStatus(String code, String displayName) {
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
    public static SpaceStatus fromCode(String code) {
        return Arrays.stream(values())
                .filter(t -> t.code.equals(code))
                .findFirst()
                .orElse(null);
    }
}
