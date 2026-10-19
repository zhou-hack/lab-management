package com.labmanage.module.item.transfer;

import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.ResultCode;

/**
 * 导入导出文件格式
 */
public enum FileFormat {

    /** Excel xlsx */
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    /** CSV（UTF-8 含 BOM） */
    CSV("csv", "text/csv");

    private final String extension;
    private final String contentType;

    FileFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    /**
     * 按文件扩展名判定格式，未识别抛 40000
     */
    public static FileFormat fromFileName(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        for (FileFormat format : values()) {
            if (lower.endsWith("." + format.extension)) {
                return format;
            }
        }
        throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "仅支持 .xlsx 或 .csv 文件");
    }
}
