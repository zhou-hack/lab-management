package com.labmanage.roles.dto;

import com.labmanage.common.core.BasePageQuery;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 助理授权分页查询参数（GET 查询串绑定，需 JavaBean 形态）
 */
public class GrantPageQueryDTO extends BasePageQuery {

    /** 关键字：模糊匹配学生/老师姓名、学工号 */
    @Size(max = 32, message = "关键字长度不能超过 32")
    private String keyword;

    /** 展示状态：ACTIVE生效中/REVOKED已撤销/EXPIRED已过期（按时间窗口计算） */
    @Pattern(regexp = "ACTIVE|REVOKED|EXPIRED", message = "状态无效")
    private String status;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
