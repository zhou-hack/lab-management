package com.labmanage.module.space.dto;

import com.labmanage.common.core.BasePageQuery;
import jakarta.validation.constraints.Size;

/**
 * 校区分页查询参数
 */
public class CampusPageQueryDTO extends BasePageQuery {

    /** 关键字：模糊匹配校区编码 / 名称 */
    @Size(max = 64, message = "关键字长度不能超过 64")
    private String keyword;

    /** 启用状态精确筛选：0/1 */
    private Integer enabled;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
