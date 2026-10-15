package com.labmanage.module.space.vo;

import java.util.List;

/**
 * 场地批量启停结果。
 * updated 为成功变更行数；missing 为未命中的场地 ID（不存在或已删除）。
 */
public record SpaceBatchStatusVO(
        int updated,
        List<String> missing
) {
}
