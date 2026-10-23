package com.labmanage.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.item.entity.MaterialRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 物料申领单 Mapper（状态迁移均为条件更新 CAS，保证只能执行一次；
 * 2026-10-22 学生预约下线后仅一级终审：PROXY/TEACHER 均创建即 TEACHER_APPROVED，
 * 由管理员终审 → APPROVED）
 */
@Mapper
public interface MaterialRequestMapper extends BaseMapper<MaterialRequest> {

    /**
     * 老师已审核 → 已批准（管理员终审）
     */
    @Update("UPDATE material_request SET status = 'APPROVED', update_time = UTC_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status = 'TEACHER_APPROVED' AND deleted = 0")
    int casApprove(@Param("id") Long id);

    /**
     * 老师已审核 → 已拒绝（管理员终审拒绝，含学生代理代领单）
     */
    @Update("UPDATE material_request SET status = 'REJECTED', update_time = UTC_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status = 'TEACHER_APPROVED' AND deleted = 0")
    int casReject(@Param("id") Long id);

    /**
     * 未实发状态 → 已取消（服务端先按申领行校验无实发量；取消/拒绝的代领单释放源行占用）
     */
    @Update("UPDATE material_request SET status = 'CANCELLED', update_time = UTC_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status IN ('TEACHER_APPROVED', 'APPROVED') AND deleted = 0")
    int casCancel(@Param("id") Long id);

    /**
     * 已批准 → 全部实发（全部行实发量达批准量时由过账事务推进；
     * 部分实发不设派生态，保持 APPROVED，进度看申领行 fulfilled_qty）
     */
    @Update("UPDATE material_request SET status = 'FULFILLED', update_time = UTC_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status = 'APPROVED' AND deleted = 0")
    int casMarkFulfilled(@Param("id") Long id);
}
