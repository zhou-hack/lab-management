package com.labmanage.module.space.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;
import com.labmanage.module.space.enums.SpaceStatus;
import com.labmanage.module.space.enums.SpaceType;

import java.time.LocalTime;

/**
 * 场地实体（biz_space，单表）。
 * <p>
 * 场地名称 space_name 全局唯一（含逻辑删除记录，删除后不复用，由
 * UNIQUE 索引兜底 + 服务层含删除记录的重复校验保证）；
 * open_start/open_end 为上海本地日历时间（TIME(0)，无时区语义），
 * create_time/update_time 为 UTC 时间点（Instant，公共序列化器输出）。
 */
@TableName("biz_space")
public class BizSpace extends BaseEntity {

    /** 场地名称（全局唯一，删除后不复用） */
    private String spaceName;

    /** 场地类型：CLASSROOM/TRAINING_ROOM */
    private SpaceType spaceType;

    /** 位置（自由文本，如"A 楼七层 A703"） */
    private String location;

    /** 容纳人数（>0） */
    private Integer capacity;

    /** 配套设备清单（自由文本） */
    private String facilities;

    /** 每日开放开始（上海本地时间，默认 08:00） */
    private LocalTime openStart;

    /** 每日开放结束（上海本地时间，默认 21:00，须晚于 openStart） */
    private LocalTime openEnd;

    /** 状态：AVAILABLE/MAINTAINING/CLOSED（落库） */
    private SpaceStatus status;

    public String getSpaceName() {
        return spaceName;
    }

    public void setSpaceName(String spaceName) {
        this.spaceName = spaceName;
    }

    public SpaceType getSpaceType() {
        return spaceType;
    }

    public void setSpaceType(SpaceType spaceType) {
        this.spaceType = spaceType;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public String getFacilities() {
        return facilities;
    }

    public void setFacilities(String facilities) {
        this.facilities = facilities;
    }

    public LocalTime getOpenStart() {
        return openStart;
    }

    public void setOpenStart(LocalTime openStart) {
        this.openStart = openStart;
    }

    public LocalTime getOpenEnd() {
        return openEnd;
    }

    public void setOpenEnd(LocalTime openEnd) {
        this.openEnd = openEnd;
    }

    public SpaceStatus getStatus() {
        return status;
    }

    public void setStatus(SpaceStatus status) {
        this.status = status;
    }
}
