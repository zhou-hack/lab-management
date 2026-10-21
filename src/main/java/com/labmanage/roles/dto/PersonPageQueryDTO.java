package com.labmanage.roles.dto;

import com.labmanage.common.core.BasePageQuery;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 统一人员视图分页查询（GET 查询串绑定，需 JavaBean 形态）
 */
public class PersonPageQueryDTO extends BasePageQuery {

    /** 关键字：模糊匹配学工号/姓名/手机号/登录账号 */
    @Size(max = 32, message = "关键字长度不能超过 32")
    private String keyword;

    /** 身份：STUDENT/TEACHER/ADMIN */
    @Pattern(regexp = "STUDENT|TEACHER|ADMIN", message = "身份类型无效")
    private String personType;

    /** 部门 ID */
    private Long departmentId;

    /** 状态：UNFILED 未建档 / NORMAL 正常 / DISABLED 停用（含名单停用与账号停用） */
    @Pattern(regexp = "UNFILED|NORMAL|DISABLED", message = "状态无效")
    private String status;

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

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
