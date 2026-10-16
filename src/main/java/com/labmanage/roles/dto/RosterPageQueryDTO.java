package com.labmanage.roles.dto;

import com.labmanage.common.core.BasePageQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 名单分页查询参数（GET 查询串绑定，需 JavaBean 形态）
 */
public class RosterPageQueryDTO extends BasePageQuery {

    /** 关键字：模糊匹配学工号/姓名/手机号 */
    @Size(max = 32, message = "关键字长度不能超过 32")
    private String keyword;

    /** 身份类型：STUDENT/TEACHER */
    @Pattern(regexp = "STUDENT|TEACHER", message = "身份类型无效")
    private String personType;

    /** 建档状态：1已建档 0未建档 */
    @Min(value = 0, message = "建档状态仅支持 0/1")
    @Max(value = 1, message = "建档状态仅支持 0/1")
    private Integer matched;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getPersonType() {
        return personType;
    }

    public void setPersonType(String personType) {
        this.personType = personType;
    }

    public Integer getMatched() {
        return matched;
    }

    public void setMatched(Integer matched) {
        this.matched = matched;
    }
}
