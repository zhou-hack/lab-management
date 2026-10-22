package com.labmanage.roles.dto;

import com.labmanage.common.core.BasePageQuery;
import jakarta.validation.constraints.Size;

/**
 * 数据范围分页查询参数（GET 查询串绑定，需 JavaBean 形态）
 */
public class ScopePageQueryDTO extends BasePageQuery {

    /** 关键字：模糊匹配管理员姓名 / 登录账号 */
    @Size(max = 64, message = "关键字长度不能超过 64")
    private String keyword;

    /** 范围类型精确筛选：ALL/DEPARTMENT */
    @Size(max = 32, message = "范围类型长度不能超过 32")
    private String scopeType;

    /** 操作级别精确筛选：READ/WRITE */
    @Size(max = 32, message = "操作级别长度不能超过 32")
    private String accessMode;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public String getAccessMode() {
        return accessMode;
    }

    public void setAccessMode(String accessMode) {
        this.accessMode = accessMode;
    }
}
