package com.labmanage.module.item.dto;

import com.labmanage.common.core.BasePageQuery;
import com.labmanage.module.item.enums.MovementType;
import com.labmanage.module.item.enums.StockDocumentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 库存单据分页查询参数
 */
@Schema(description = "库存单据分页查询参数")
public class StockDocumentQuery extends BasePageQuery {

    @Schema(description = "业务类型", example = "PURCHASE_IN")
    private MovementType movementType;

    @Schema(description = "单据状态", example = "POSTED")
    private StockDocumentStatus status;

    @Schema(description = "业务发生时间起（带偏移量 ISO 8601）", example = "2026-10-01T00:00:00+08:00")
    private OffsetDateTime occurredFrom;

    @Schema(description = "业务发生时间止（带偏移量 ISO 8601）", example = "2026-10-31T23:59:59+08:00")
    private OffsetDateTime occurredTo;

    public MovementType getMovementType() {
        return movementType;
    }

    public void setMovementType(MovementType movementType) {
        this.movementType = movementType;
    }

    public StockDocumentStatus getStatus() {
        return status;
    }

    public void setStatus(StockDocumentStatus status) {
        this.status = status;
    }

    public OffsetDateTime getOccurredFrom() {
        return occurredFrom;
    }

    public void setOccurredFrom(OffsetDateTime occurredFrom) {
        this.occurredFrom = occurredFrom;
    }

    public OffsetDateTime getOccurredTo() {
        return occurredTo;
    }

    public void setOccurredTo(OffsetDateTime occurredTo) {
        this.occurredTo = occurredTo;
    }
}
