package com.labmanage.module.space.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.Ids;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.module.space.dto.CampusCreateRequest;
import com.labmanage.module.space.dto.CampusPageQuery;
import com.labmanage.module.space.dto.CampusUpdateRequest;
import com.labmanage.module.space.entity.Campus;
import com.labmanage.module.space.mapper.CampusMapper;
import com.labmanage.module.space.service.CampusService;
import com.labmanage.module.space.vo.CampusVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 校区服务实现
 */
@Service
public class CampusServiceImpl implements CampusService {

    private final CampusMapper campusMapper;

    public CampusServiceImpl(CampusMapper campusMapper) {
        this.campusMapper = campusMapper;
    }

    @Override
    public PageResult<CampusVO> page(CampusPageQuery query) {
        LambdaQueryWrapper<Campus> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword();
            wrapper.and(w -> w.like(Campus::getCampusCode, keyword).or().like(Campus::getCampusName, keyword));
        }
        wrapper.orderByAsc(Campus::getId);
        Page<Campus> page = campusMapper.selectPage(PageUtils.toPage(query), wrapper);
        List<CampusVO> records = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(records, page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public void create(CampusCreateRequest request) {
        Long exists = campusMapper.selectCount(new LambdaQueryWrapper<Campus>()
                .eq(Campus::getCampusCode, request.campusCode()));
        if (exists > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "校区编码已存在");
        }
        Campus campus = new Campus();
        campus.setCampusCode(request.campusCode());
        campus.setCampusName(request.campusName());
        campus.setAddress(request.address());
        campus.setEnabled(1);
        campusMapper.insert(campus);
    }

    @Override
    public void update(String id, CampusUpdateRequest request) {
        Campus campus = requireCampus(id);
        campus.setCampusName(request.campusName());
        campus.setAddress(request.address());
        campus.setEnabled(request.enabled());
        campusMapper.updateById(campus);
    }

    @Override
    public void delete(String id) {
        Campus campus = requireCampus(id);
        if (campusMapper.countBuildings(campus.getId()) > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "校区下存在楼栋，无法删除");
        }
        if (campusMapper.countDataScopes(campus.getId()) > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "校区已被数据范围引用，无法删除");
        }
        campusMapper.deleteById(campus.getId());
    }

    private Campus requireCampus(String id) {
        Campus campus = campusMapper.selectById(Ids.parse(id));
        if (campus == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "校区不存在");
        }
        return campus;
    }

    private CampusVO toVO(Campus campus) {
        return new CampusVO(
                campus.getId().toString(),
                campus.getCampusCode(),
                campus.getCampusName(),
                campus.getAddress(),
                campus.getEnabled()
        );
    }
}
