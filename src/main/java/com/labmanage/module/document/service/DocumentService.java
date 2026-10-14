package com.labmanage.module.document.service;

import com.labmanage.module.document.entity.BizDocument;
import com.labmanage.module.document.enums.ActorType;
import com.labmanage.module.document.enums.DocumentType;
import com.labmanage.module.document.enums.EventType;

/**
 * 统一单据服务（内部域，由各业务模块在同事务内调用）
 */
public interface DocumentService {

    /**
     * 创建单据：预生成雪花 ID，单据编号 = 类型前缀 + ID；
     * 由调用方保证事务边界与幂等（uk(owner, type, idempotency_key)）
     */
    BizDocument create(DocumentType documentType, Long ownerUserId, Long departmentId, String idempotencyKey);

    /**
     * 按主键查询单据，不存在抛业务异常
     */
    BizDocument requireDocument(Long documentId);

    /**
     * 追加单据事件（不可变历史）；
     * request_key 与单据组合唯一，重复写入由唯一键拦截
     */
    void appendEvent(Long documentId, EventType eventType, String fromStatus, String toStatus,
                     Long actorId, ActorType actorType, String reason, Long taskId, String requestKey);
}
