package com.labmanage.roles.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.roles.dto.PolicyPageQueryDTO;
import com.labmanage.roles.dto.PolicyPublishDTO;
import com.labmanage.roles.vo.PolicyPageItemVO;

/**
 * 业务策略管理服务（版本化，发布后不可修改）
 */
public interface SysPolicyService {

    /** 策略分页列表（全部版本，按编码升序、版本倒序） */
    PageResult<PolicyPageItemVO> page(PolicyPageQueryDTO query);

    /**
     * 发布新版本：编码须已登记；区间不重叠；版本号同码递增；
     * 发布成功后刷新策略读取缓存。
     *
     * @return 新版本号
     */
    int publish(PolicyPublishDTO dto);
}
