package com.labmanage.module.item.vo;

import java.util.List;

public record AssetImportPreviewVO(
        String sheetName,
        int materialCount,
        int assetCount,
        int existingAssetCount,
        long consumableQuantity,
        boolean canImport,
        List<Row> rows,
        List<Issue> issues
) {
    public record Row(
            int rowNum,
            String materialName,
            String materialType,
            String specification,
            String baseUnit,
            int quantity,
            String skuCode,
            String firstAssetCode,
            String lastAssetCode,
            Integer existingAssetCount
    ) {
    }

    public record Issue(int rowNum, String message) {
    }
}
