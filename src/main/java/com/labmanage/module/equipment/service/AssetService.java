package com.labmanage.module.equipment.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.module.equipment.dto.AssetClassifyRequest;
import com.labmanage.module.equipment.dto.AssetCreateRequest;
import com.labmanage.module.equipment.dto.AssetPageQuery;
import com.labmanage.module.equipment.dto.AssetTransferRequest;
import com.labmanage.module.equipment.dto.AssetUpdateRequest;
import com.labmanage.module.equipment.vo.AssetImportResultVO;
import com.labmanage.module.equipment.vo.AssetVO;
import com.labmanage.module.equipment.vo.CustodianOptionVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 设备台账服务
 */
public interface AssetService {

    /**
     * 台账分页查询；无台账维护权限的请求强制收敛为
     * 「在架」目录视图（status = IDLE 且启用）
     */
    PageResult<AssetVO> page(AssetPageQuery query);

    /**
     * 新增台账（初始状态 IDLE，价值分级待定级）
     */
    void create(AssetCreateRequest request);

    /**
     * 修改台账（编号/型号/实验室不可改；位置变迁走迁移登记）
     */
    void update(String id, AssetUpdateRequest request);

    /**
     * 删除台账（逻辑删除）
     */
    void delete(String id);

    /**
     * 迁移登记（仅允许空闲设备；
     * 当前台账与位置历史同事务更新）
     */
    void transfer(String id, AssetTransferRequest request);

    /**
     * 价值定级（登记分级、理由、定级人与时间）
     */
    void classify(String id, AssetClassifyRequest request);

    /**
     * 保管人下拉选项（全部启用用户）
     */
    List<CustodianOptionVO> custodianOptions();

    /**
     * 台账导出（xlsx，按查询条件全量导出；权限收敛规则与分页查询一致）
     */
    byte[] export(AssetPageQuery query);

    /**
     * 台账导入（xlsx；导出文件即模板，按表头列名解析；
     * 行级校验错误跳过并报告，有效行批量入库）
     */
    AssetImportResultVO importAssets(MultipartFile file);
}
