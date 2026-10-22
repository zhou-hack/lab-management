package com.labmanage.module.booking.dto;

import com.labmanage.common.core.BasePageQuery;
import com.labmanage.module.booking.enums.SeriesStatus;

/**
 * 周期预约分页查询参数（「我的周期预约」与「周期预约审批」共用）。
 */
public class SeriesPageQuery extends BasePageQuery {

    /** 状态过滤（如 PENDING 待审批） */
    private SeriesStatus status;

    /** 场地过滤 */
    private Long spaceId;

    public SeriesStatus getStatus() {
        return status;
    }

    public void setStatus(SeriesStatus status) {
        this.status = status;
    }

    public Long getSpaceId() {
        return spaceId;
    }

    public void setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
    }
}