package com.labmanage.module.item.vo;

public record StockLocationOptionVO(
        String id,
        String locationCode,
        String locationName,
        Integer enabled
) {
}
