package com.labmanage.module.item.dto;

import com.labmanage.common.core.BasePageQuery;
import com.labmanage.module.item.enums.MoveDirection;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 库存台账（流水）分页查询参数
 */
@Schema(description = "库存台账分页查询参数")
public class StockMovementQuery extends BasePageQuery {

    @Schema(description = "库位 ID", example = "1234567890123456789")
    private String locationId;

    @Schema(description = "物料 ID", example = "1234567890123456789")
    private String skuId;

    @Schema(description = "物料分类 ID", example = "1234567890123456789")
    private String categoryId;

    @Schema(description = "单据 ID", example = "1234567890123456789")
    private String stockDocumentId;

    @Schema(description = "申领单 ID（查该申领发放产生的全部出入库流水）", example = "1234567890123456789")
    private String materialRequestId;

    @Schema(description = "方向：IN / OUT", example = "IN")
    private MoveDirection direction;

    @Schema(description = "发生时间起（带偏移量 ISO 8601）", example = "2026-10-01T00:00:00+08:00")
    private OffsetDateTime occurredFrom;

    @Schema(description = "发生时间止（带偏移量 ISO 8601）", example = "2026-10-31T23:59:59+08:00")
    private OffsetDateTime occurredTo;

    public String getLocationId() {
        return locationId;
    }

    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }

    public String getSkuId() {
        return skuId;
    }

    public void setSkuId(String skuId) {
        this.skuId = skuId;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getStockDocumentId() {
        return stockDocumentId;
    }

    public void setStockDocumentId(String stockDocumentId) {
        this.stockDocumentId = stockDocumentId;
    }

    public String getMaterialRequestId() {
        return materialRequestId;
    }

    public void setMaterialRequestId(String materialRequestId) {
        this.materialRequestId = materialRequestId;
    }

    public MoveDirection getDirection() {
        return direction;
    }

    public void setDirection(MoveDirection direction) {
        this.direction = direction;
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
