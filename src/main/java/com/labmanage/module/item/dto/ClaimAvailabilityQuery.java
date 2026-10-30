package com.labmanage.module.item.dto;

import com.labmanage.module.item.enums.MaterialType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class ClaimAvailabilityQuery extends ItemPageQuery {

    @NotNull(message = "预约日期不能为空")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate bookingDate;

    @NotBlank(message = "起始节次不能为空")
    @Pattern(regexp = "^[1-9]\\d*$", message = "起始节次ID不合法")
    private String startPeriodId;

    @NotBlank(message = "结束节次不能为空")
    @Pattern(regexp = "^[1-9]\\d*$", message = "结束节次ID不合法")
    private String endPeriodId;

    private MaterialType materialType;
    private String materialName;

    @Pattern(regexp = "^[1-9]\\d*$", message = "申领ID不合法")
    private String claimId;

    public ClaimAvailabilityQuery() {
        setPageSize(10);
    }

    public LocalDate getBookingDate() { return bookingDate; }
    public void setBookingDate(LocalDate bookingDate) { this.bookingDate = bookingDate; }
    public String getStartPeriodId() { return startPeriodId; }
    public void setStartPeriodId(String startPeriodId) { this.startPeriodId = startPeriodId; }
    public String getEndPeriodId() { return endPeriodId; }
    public void setEndPeriodId(String endPeriodId) { this.endPeriodId = endPeriodId; }
    public MaterialType getMaterialType() { return materialType; }
    public void setMaterialType(MaterialType materialType) { this.materialType = materialType; }
    public String getMaterialName() { return materialName; }
    public void setMaterialName(String materialName) { this.materialName = materialName; }
    public String getClaimId() { return claimId; }
    public void setClaimId(String claimId) { this.claimId = claimId; }
}
