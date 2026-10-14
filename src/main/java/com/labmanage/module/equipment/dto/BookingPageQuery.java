package com.labmanage.module.equipment.dto;

import com.labmanage.common.core.BasePageQuery;
import com.labmanage.module.equipment.enums.BookingStatus;

/**
 * 器材借用单分页查询参数
 * <p>
 * scope 取值：MINE（本人单据，默认）/ PENDING（待我处理：老师=待我审批，
 * 管理员=待管理员审批）/ ALL（全量，仅管理员）；
 * 学生/老师请求由服务端强制仅本人相关数据。
 */
public class BookingPageQuery extends BasePageQuery {

    /** 查询范围：MINE/PENDING/ALL */
    private String scope = "MINE";

    /** 状态过滤 */
    private BookingStatus status;

    /** 设备过滤 */
    private Long assetId;

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
    }
}
