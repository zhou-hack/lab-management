package com.labmanage.module.item.dto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class StockDocumentPageQuery extends ItemPageQuery {

    private String documentNo;
    private String documentType;
    private String businessScene;
    private Long businessChainId;
    private Long locationId;
    private Long operatorId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    public String getDocumentNo() { return documentNo; }
    public void setDocumentNo(String documentNo) { this.documentNo = documentNo; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public String getBusinessScene() { return businessScene; }
    public void setBusinessScene(String businessScene) { this.businessScene = businessScene; }
    public Long getBusinessChainId() { return businessChainId; }
    public void setBusinessChainId(Long businessChainId) { this.businessChainId = businessChainId; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
}
