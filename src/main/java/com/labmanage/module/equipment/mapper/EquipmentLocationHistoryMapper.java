package com.labmanage.module.equipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.equipment.entity.EquipmentLocationHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备位置变迁历史 Mapper（追加型历史）
 */
@Mapper
public interface EquipmentLocationHistoryMapper extends BaseMapper<EquipmentLocationHistory> {
}
