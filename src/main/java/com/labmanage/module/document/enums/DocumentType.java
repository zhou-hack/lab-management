package com.labmanage.module.document.enums;

/**
 * 单据类型（后续扩展 LAB_BOOKING 等）
 */
public enum DocumentType {

    /** 器材借用单 */
    EQUIPMENT_BOOKING("EQ");

    private final String docNoPrefix;

    DocumentType(String docNoPrefix) {
        this.docNoPrefix = docNoPrefix;
    }

    public String getDocNoPrefix() {
        return docNoPrefix;
    }

    /**
     * 生成单据编号：前缀 + 雪花 ID
     */
    public String buildDocumentNo(long id) {
        return docNoPrefix + id;
    }
}
