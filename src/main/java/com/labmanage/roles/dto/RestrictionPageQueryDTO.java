package com.labmanage.roles.dto;

import com.labmanage.common.core.BasePageQuery;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 预约限制分页查询参数（GET 查询串绑定，需 JavaBean 形态）
 */
public class RestrictionPageQueryDTO extends BasePageQuery {

    /** 关键字：模糊匹配被限制用户姓名 / 账号 / 学工号 */
    @Size(max = 64, message = "关键字长度不能超过 64")
    private String keyword;

    /** 限制类型精确筛选：EQUIPMENT/LAB/ALL */
    @Pattern(regexp = "^(EQUIPMENT|LAB|ALL)$", message = "限制类型仅支持 EQUIPMENT/LAB/ALL")
    private String restrictionType;

    /** 状态精确筛选：ACTIVE 生效中 / PENDING 未开始 / ENDED 已结束 / REVOKED 已撤销 */
    @Pattern(regexp = "^(ACTIVE|PENDING|ENDED|REVOKED)$", message = "状态仅支持 ACTIVE/PENDING/ENDED/REVOKED")
    private String status;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getRestrictionType() {
        return restrictionType;
    }

    public void setRestrictionType(String restrictionType) {
        this.restrictionType = restrictionType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
