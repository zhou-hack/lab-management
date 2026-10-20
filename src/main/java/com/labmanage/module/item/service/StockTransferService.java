package com.labmanage.module.item.service;

import com.labmanage.module.item.dto.StockBalanceExportQuery;
import com.labmanage.module.item.transfer.FileFormat;
import com.labmanage.module.item.transfer.TransferFile;
import com.labmanage.module.item.vo.ImportResultVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 库存余额批量导入/导出服务
 * <p>
 * 库存数量受「余额可从流水重建」审计约束：期初导入不直接改写余额，
 * 而是把有效行组装为一张 OPENING 期初入库草稿单，走既有审批过账流程。
 */
public interface StockTransferService {

    /** 期初余额批量导入：有效行组装为一张 OPENING 草稿单，返回结果含草稿单号 */
    ImportResultVO importOpeningBalances(MultipartFile file);

    /** 按筛选条件导出库存余额报表 */
    TransferFile exportBalances(StockBalanceExportQuery query);

    /** 期初导入模板（仅表头） */
    TransferFile openingImportTemplate(FileFormat format);
}
