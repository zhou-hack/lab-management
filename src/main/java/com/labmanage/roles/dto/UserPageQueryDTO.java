package com.labmanage.roles.dto;

import com.labmanage.common.core.BasePageQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 用户分页查询参数（GET 查询串绑定，需 JavaBean 形态）
 */
public class UserPageQueryDTO extends BasePageQuery {

    /** 关键字：模糊匹配姓名/学工号/手机号/账号 */
    @Size(max = 32, message = "关键字长度不能超过 32")
    private String keyword;

    /** 用户类型：STUDENT/TEACHER/ADMIN */
    @Pattern(regexp = "STUDENT|TEACHER|ADMIN", message = "用户类型无效")
    private String userType;

    /** 启用状态：1正常 0停用 */
    @Min(value = 0, message = "启用状态仅支持 0/1")
    @Max(value = 1, message = "启用状态仅支持 0/1")
    private Integer enabled;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
