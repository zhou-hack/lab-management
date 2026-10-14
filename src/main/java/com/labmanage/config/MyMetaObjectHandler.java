package com.labmanage.config;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * MyBatis-Plus 字段自动填充
 * 时间归一化到毫秒精度；操作者取 Sa-Token 当前登录人，未登录或系统任务为 NULL。
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Long currentUserId = currentUserId();
        this.strictInsertFill(metaObject, "createTime", Instant.class, now);
        this.strictInsertFill(metaObject, "updateTime", Instant.class, now);
        this.strictInsertFill(metaObject, "deleted", Integer.class, 0);
        this.strictInsertFill(metaObject, "createUserId", Long.class, currentUserId);
        this.strictInsertFill(metaObject, "updateUserId", Long.class, currentUserId);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", Instant.class, Instant.now().truncatedTo(ChronoUnit.MILLIS));
        this.strictUpdateFill(metaObject, "updateUserId", Long.class, currentUserId());
    }

    private Long currentUserId() {
        try {
            return StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
