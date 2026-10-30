package com.labmanage.module.item.dto;

import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class OverduePageQuery extends ItemPageQuery {

    @Pattern(regexp = "UNRETURNED|RETURNED", message = "逾期类型仅支持UNRETURNED/RETURNED")
    private String type;

    private Long claimId;
    private Long sourceDocumentId;
    private Long locationId;
    private Long applicantId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Long getClaimId() { return claimId; }
    public void setClaimId(Long claimId) { this.claimId = claimId; }
    public Long getSourceDocumentId() { return sourceDocumentId; }
    public void setSourceDocumentId(Long sourceDocumentId) { this.sourceDocumentId = sourceDocumentId; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public Long getApplicantId() { return applicantId; }
    public void setApplicantId(Long applicantId) { this.applicantId = applicantId; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
}
