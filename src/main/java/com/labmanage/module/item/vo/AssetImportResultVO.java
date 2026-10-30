package com.labmanage.module.item.vo;

public record AssetImportResultVO(
        int createdMaterialCount,
        int importedAssetCount,
        int skippedAssetCount,
        long importedConsumableQuantity,
        String documentId,
        String documentNo
) {
}
