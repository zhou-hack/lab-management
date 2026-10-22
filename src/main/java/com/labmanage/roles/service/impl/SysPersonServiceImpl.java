package com.labmanage.roles.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.roles.dto.PersonPageQueryDTO;
import com.labmanage.roles.mapper.SysPersonRosterMapper;
import com.labmanage.roles.service.SysPersonService;
import com.labmanage.roles.vo.PersonPageItemVO;
import org.springframework.stereotype.Service;

/**
 * 统一人员视图服务实现
 */
@Service
public class SysPersonServiceImpl implements SysPersonService {

    private final SysPersonRosterMapper rosterMapper;

    public SysPersonServiceImpl(SysPersonRosterMapper rosterMapper) {
        this.rosterMapper = rosterMapper;
    }

    @Override
    public PageResult<PersonPageItemVO> page(PersonPageQueryDTO query) {
        Page<PersonPageItemVO> page = rosterMapper.selectPersonPage(PageUtils.toPage(query), query);
        return PageUtils.toPageResult(page);
    }
}
