package com.labmanage.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * MyBatis-Plus 字段自动填充
 * <p>
 * 审计时间由服务端 UTC 时钟填充（JVM 统一 -Duser.timezone=UTC），
 * 审计人取当前登录用户；系统任务等无登录上下文场景保持 NULL。
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        Instant now = Instant.now();
        this.strictInsertFill(metaObject, "createTime", Instant.class, now);
        this.strictInsertFill(metaObject, "updateTime", Instant.class, now);
        this.strictInsertFill(metaObject, "deleted", Integer.class, 0);
        Long userId = currentUserId();
        if (userId != null) {
            this.strictInsertFill(metaObject, "createUserId", Long.class, userId);
            this.strictInsertFill(metaObject, "updateUserId", Long.class, userId);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", Instant.class, Instant.now());
        Long userId = currentUserId();
        if (userId != null) {
            this.strictUpdateFill(metaObject, "updateUserId", Long.class, userId);
        }
    }

    /**
     * 当前登录用户 ID；未登录（系统任务、匿名请求）返回 NULL
     */
    private Long currentUserId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (NotLoginException e) {
            return null;
        }
    }
}
