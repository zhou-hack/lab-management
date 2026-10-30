package com.labmanage.module.item.dto;

public class TransferPageQuery extends ItemPageQuery {

    private Long fromLocationId;
    private Long toLocationId;
    private String transferStatus;

    public Long getFromLocationId() { return fromLocationId; }
    public void setFromLocationId(Long fromLocationId) { this.fromLocationId = fromLocationId; }
    public Long getToLocationId() { return toLocationId; }
    public void setToLocationId(Long toLocationId) { this.toLocationId = toLocationId; }
    public String getTransferStatus() { return transferStatus; }
    public void setTransferStatus(String transferStatus) { this.transferStatus = transferStatus; }
}
