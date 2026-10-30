package com.labmanage.module.item.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record StockInboundCreateDTO(
        @NotEmpty(message = "入库行不能为空") List<@NotNull(message = "入库行不能为空") @Valid StockInboundLineDTO> lines,
        @Size(max = 128, message = "幂等键长度不能超过128") String idempotencyKey,
        @Size(max = 512, message = "备注长度不能超过512") String remark
) {
}
