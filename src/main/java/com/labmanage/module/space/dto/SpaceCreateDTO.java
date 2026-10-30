package com.labmanage.module.space.dto;

import com.labmanage.module.space.enums.SpaceStatus;
import com.labmanage.module.space.enums.SpaceType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新建场地入参。
 * <p>
 * 客户端不可指定 id/审计字段，由服务端写入；
 * openStart/openEnd 可空，缺省分别取 08:00 / 22:00；
 * status 可空，缺省 AVAILABLE。
 */
public record SpaceCreateDTO(

        @NotBlank(message = "场地名称不能为空")
        @Size(max = 128, message = "场地名称长度不能超过 128")
        String labName,

        @NotNull(message = "场地类型不能为空")
        SpaceType labType,

        @Size(max = 128, message = "位置长度不能超过 128")
        String locationDetail,

        @NotNull(message = "容纳人数不能为空")
        @Min(value = 1, message = "容纳人数必须大于 0")
        Integer capacity,

        @Size(max = 2000, message = "配套设备清单长度不能超过 2000")
        String facilities,

        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "开放开始时间格式须为 HH:mm")
        String openStart,

        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "开放结束时间格式须为 HH:mm")
        String openEnd,

        SpaceStatus status
) {
}
