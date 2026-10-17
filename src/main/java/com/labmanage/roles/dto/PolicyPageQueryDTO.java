package com.labmanage.roles.dto;

import com.labmanage.common.core.BasePageQuery;
import jakarta.validation.constraints.Size;

/**
 * 策略分页查询参数（GET 查询串绑定，需 JavaBean 形态）
 */
public class PolicyPageQueryDTO extends BasePageQuery {

    /** 关键字：模糊匹配策略编码、策略名称 */
    @Size(max = 64, message = "关键字长度不能超过 64")
    private String keyword;

    /** 策略编码精确筛选 */
    @Size(max = 64, message = "策略编码长度不能超过 64")
    private String policyCode;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getPolicyCode() {
        return policyCode;
    }

    public void setPolicyCode(String policyCode) {
        this.policyCode = policyCode;
    }
}
