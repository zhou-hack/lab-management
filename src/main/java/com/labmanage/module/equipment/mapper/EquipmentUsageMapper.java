package com.labmanage.module.equipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.equipment.entity.EquipmentUsage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 器材借出归还流水 Mapper
 */
@Mapper
public interface EquipmentUsageMapper extends BaseMapper<EquipmentUsage> {
}
