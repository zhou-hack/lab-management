package com.labmanage.module.item.dto;

import com.labmanage.common.core.BasePageQuery;

public class StockLocationResponsibleHistoryPageQuery extends BasePageQuery {

    private Long locationId;

    public StockLocationResponsibleHistoryPageQuery() {
        setPageSize(10);
    }

    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
}
