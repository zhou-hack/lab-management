package com.labmanage.roles.dto;

import com.labmanage.common.core.BasePageQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;

/**
 * 操作日志分页查询参数（GET 查询串绑定，需 JavaBean 形态）
 */
public class LogPageQueryDTO extends BasePageQuery {

    /** 关键字：模糊匹配操作者姓名、模块码、动作码 */
    @Size(max = 64, message = "关键字长度不能超过 64")
    private String keyword;

    /** 模块码精确筛选 */
    @Size(max = 64, message = "模块码长度不能超过 64")
    private String moduleCode;

    /** 结果筛选：1成功 0失败 */
    @Min(value = 0, message = "结果仅支持 0/1")
    @Max(value = 1, message = "结果仅支持 0/1")
    private Integer success;

    /** 发生时间下界（UTC，含） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant startAt;

    /** 发生时间上界（UTC，不含） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant endAt;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getModuleCode() {
        return moduleCode;
    }

    public void setModuleCode(String moduleCode) {
        this.moduleCode = moduleCode;
    }

    public Integer getSuccess() {
        return success;
    }

    public void setSuccess(Integer success) {
        this.success = success;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public void setStartAt(Instant startAt) {
        this.startAt = startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public void setEndAt(Instant endAt) {
        this.endAt = endAt;
    }
}
