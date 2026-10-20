package com.labmanage.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.item.entity.MaterialRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 物料申领单 Mapper（状态迁移均为条件更新 CAS，保证只能执行一次；
 * 两级审核：SELF 经 DRAFT→TEACHER_APPROVED（老师审核）→APPROVED（管理员终审），
 * PROXY 学生代理代领创建即 TEACHER_APPROVED，直接管理员终审）
 */
@Mapper
public interface MaterialRequestMapper extends BaseMapper<MaterialRequest> {

    /**
     * 草稿 → 老师已审核（老师审核阶段，逐行写批准量）
     */
    @Update("UPDATE material_request SET status = 'TEACHER_APPROVED', update_time = UTC_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status = 'DRAFT' AND deleted = 0")
    int casTeacherApprove(@Param("id") Long id);

    /**
     * 老师已审核 → 已批准（管理员终审；PENDING 保留给审批流阶段，不作为终审入口）
     */
    @Update("UPDATE material_request SET status = 'APPROVED', update_time = UTC_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status = 'TEACHER_APPROVED' AND deleted = 0")
    int casApprove(@Param("id") Long id);

    /**
     * 草稿 → 已拒绝（老师审核拒绝）
     */
    @Update("UPDATE material_request SET status = 'REJECTED', update_time = UTC_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status = 'DRAFT' AND deleted = 0")
    int casTeacherReject(@Param("id") Long id);

    /**
     * 老师已审核 → 已拒绝（管理员终审拒绝，含学生代理代领单）
     */
    @Update("UPDATE material_request SET status = 'REJECTED', update_time = UTC_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status = 'TEACHER_APPROVED' AND deleted = 0")
    int casReject(@Param("id") Long id);

    /**
     * 未实发状态 → 已取消（服务端先校验无实发量；取消/拒绝的代领单释放源行占用）
     */
    @Update("UPDATE material_request SET status = 'CANCELLED', update_time = UTC_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status IN ('DRAFT', 'PENDING', 'TEACHER_APPROVED', 'APPROVED') AND deleted = 0")
    int casCancel(@Param("id") Long id);

    /**
     * 已批准 → 部分实发（首次实发后由过账事务推进）
     */
    @Update("UPDATE material_request SET status = 'PARTIALLY_FULFILLED', update_time = UTC_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status = 'APPROVED' AND deleted = 0")
    int casMarkPartiallyFulfilled(@Param("id") Long id);

    /**
     * 已批准/部分实发 → 全部实发（全部行实发量达批准量时推进）
     */
    @Update("UPDATE material_request SET status = 'FULFILLED', update_time = UTC_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status IN ('APPROVED', 'PARTIALLY_FULFILLED') AND deleted = 0")
    int casMarkFulfilled(@Param("id") Long id);
}
