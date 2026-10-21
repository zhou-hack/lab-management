package com.labmanage.roles.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

/**
 * 助理授权范围表（assistant_grant_scope）
 * MATERIAL 类型表示「物料领取」（无目标，action 固定 ISSUE_RETURN）；
 * OFFERING/LAB 为预留类型（恰一目标非空），由数据库 CHECK 保证；生成列 scope_target_id 不映射。
 */
@TableName("assistant_grant_scope")
public class AssistantGrantScope extends BaseEntity {

    private Long grantId;

    /** 范围类型：OFFERING/LAB */
    private String scopeType;

    private Long offeringId;

    private Long labId;

    /** 动作：ISSUE/RETURN */
    private String actionCode;

    public Long getGrantId() {
        return grantId;
    }

    public void setGrantId(Long grantId) {
        this.grantId = grantId;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public Long getOfferingId() {
        return offeringId;
    }

    public void setOfferingId(Long offeringId) {
        this.offeringId = offeringId;
    }

    public Long getLabId() {
        return labId;
    }

    public void setLabId(Long labId) {
        this.labId = labId;
    }

    public String getActionCode() {
        return actionCode;
    }

    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
    }
}
