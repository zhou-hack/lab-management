package com.labmanage.roles.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.roles.dto.RestrictionCreateDTO;
import com.labmanage.roles.dto.RestrictionPageQueryDTO;
import com.labmanage.roles.entity.SysUser;
import com.labmanage.roles.entity.UserBookingRestriction;
import com.labmanage.roles.mapper.SysUserMapper;
import com.labmanage.roles.mapper.UserBookingRestrictionMapper;
import com.labmanage.roles.service.SysRestrictionService;
import com.labmanage.roles.vo.RestrictionPageItemVO;
import com.labmanage.roles.vo.UserOptionVO;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 预约限制服务实现
 */
@Service
public class SysRestrictionServiceImpl implements SysRestrictionService {

    private final UserBookingRestrictionMapper restrictionMapper;

    private final SysUserMapper userMapper;

    public SysRestrictionServiceImpl(UserBookingRestrictionMapper restrictionMapper, SysUserMapper userMapper) {
        this.restrictionMapper = restrictionMapper;
        this.userMapper = userMapper;
    }

    @Override
    public PageResult<RestrictionPageItemVO> page(RestrictionPageQueryDTO query) {
        if (query.getKeyword() != null) {
            query.setKeyword(query.getKeyword().trim());
        }
        Page<RestrictionPageItemVO> page = restrictionMapper.selectRestrictionPage(PageUtils.toPage(query), query);
        return PageUtils.toPageResult(page);
    }

    @Override
    public List<UserOptionVO> userOptions(String keyword) {
        return userMapper.selectUserOptions(keyword == null ? null : keyword.trim());
    }

    @Override
    public void create(RestrictionCreateDTO dto) {
        SysUser target = userMapper.selectById(dto.userId());
        if (target == null) {
            throw new BusinessException(40400, "用户不存在");
        }
        if (dto.endAt() != null && !dto.endAt().isAfter(dto.startAt())) {
            throw new BusinessException(40000, "限制结束时间必须晚于开始时间");
        }
        UserBookingRestriction restriction = new UserBookingRestriction();
        restriction.setUserId(dto.userId());
        restriction.setRestrictionType(dto.restrictionType());
        restriction.setReason(dto.reason());
        restriction.setStartAt(dto.startAt().truncatedTo(ChronoUnit.MILLIS));
        restriction.setEndAt(dto.endAt() == null ? null : dto.endAt().truncatedTo(ChronoUnit.MILLIS));
        restrictionMapper.insert(restriction);
    }

    @Override
    public void revoke(Long id) {
        UserBookingRestriction restriction = restrictionMapper.selectById(id);
        if (restriction == null) {
            throw new BusinessException(40400, "预约限制不存在");
        }
        if (restriction.getRevokedAt() != null) {
            throw new BusinessException(40900, "该限制已撤销");
        }
        restriction.setRevokedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        restriction.setRevokedBy(StpUtil.getLoginIdAsLong());
        restrictionMapper.updateById(restriction);
    }

    @Override
    public boolean hasActiveRestriction(Long userId, String restrictionType, Instant at) {
        return restrictionMapper.countActive(userId, restrictionType, at) > 0;
    }
}
