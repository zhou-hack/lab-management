package com.labmanage.module.item.service;

import com.labmanage.module.item.dto.MaterialCategoryExportQuery;
import com.labmanage.module.item.dto.MaterialSkuExportQuery;
import com.labmanage.module.item.transfer.FileFormat;
import com.labmanage.module.item.transfer.TransferFile;
import com.labmanage.module.item.vo.ImportResultVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 物料批量导入/导出服务
 */
public interface MaterialTransferService {

    /** 导入物料（逐行校验，编码冲突/非法行跳过并记明细） */
    ImportResultVO importSkus(MultipartFile file);

    /** 导入分类（父分类行须排在子分类行之前） */
    ImportResultVO importCategories(MultipartFile file);

    /** 按筛选条件导出物料（含表头与全部匹配行） */
    TransferFile exportSkus(MaterialSkuExportQuery query);

    /** 按筛选条件导出分类 */
    TransferFile exportCategories(MaterialCategoryExportQuery query);

    /** 物料导入模板（仅表头） */
    TransferFile skuImportTemplate(FileFormat format);

    /** 分类导入模板（仅表头） */
    TransferFile categoryImportTemplate(FileFormat format);
}
