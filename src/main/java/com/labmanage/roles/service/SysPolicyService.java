package com.labmanage.roles.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.roles.dto.PolicyPageQueryDTO;
import com.labmanage.roles.dto.PolicyPublishDTO;
import com.labmanage.roles.vo.PolicyPageItemVO;
import com.labmanage.roles.vo.PolicySchemaFieldVO;

import java.util.List;
import java.util.Map;

/**
 * 业务策略管理服务（版本化，发布后不可修改）
 */
public interface SysPolicyService {

    /** 策略分页列表（全部版本，按编码升序、版本倒序） */
    PageResult<PolicyPageItemVO> page(PolicyPageQueryDTO query);

    /** 全部已登记策略编码的参数字段元数据（前端结构化表单用） */
    Map<String, List<PolicySchemaFieldVO>> schemas();

    /**
     * 发布新版本：编码须已登记；区间不重叠；参数按元数据做字段级校验；
     * 版本号同码递增；发布成功后刷新策略读取缓存。
     *
     * @return 新版本号
     */
    int publish(PolicyPublishDTO dto);
}
