package com.labmanage.module.item.vo;

import java.util.List;

public record ClaimTemplateVO(
        String purpose,
        List<ClaimTemplateLineVO> lines
) {
}
