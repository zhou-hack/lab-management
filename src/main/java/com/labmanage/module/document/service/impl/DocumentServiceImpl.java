package com.labmanage.module.document.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.ResultCode;
import com.labmanage.module.document.entity.BizDocument;
import com.labmanage.module.document.entity.DocumentEvent;
import com.labmanage.module.document.enums.ActorType;
import com.labmanage.module.document.enums.DocumentType;
import com.labmanage.module.document.enums.EventType;
import com.labmanage.module.document.mapper.BizDocumentMapper;
import com.labmanage.module.document.mapper.DocumentEventMapper;
import com.labmanage.module.document.service.DocumentService;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 统一单据服务实现
 */
@Service
public class DocumentServiceImpl implements DocumentService {

    private final BizDocumentMapper documentMapper;
    private final DocumentEventMapper eventMapper;

    public DocumentServiceImpl(BizDocumentMapper documentMapper, DocumentEventMapper eventMapper) {
        this.documentMapper = documentMapper;
        this.eventMapper = eventMapper;
    }

    @Override
    public BizDocument create(DocumentType documentType, Long ownerUserId, Long departmentId, String idempotencyKey) {
        long id = IdWorker.getId();
        BizDocument document = new BizDocument();
        document.setId(id);
        document.setDocumentNo(documentType.buildDocumentNo(id));
        document.setDocumentType(documentType);
        document.setOwnerUserId(ownerUserId);
        document.setDepartmentId(departmentId);
        document.setSubmittedAt(Instant.now());
        document.setIdempotencyKey(idempotencyKey);
        documentMapper.insert(document);
        return document;
    }

    @Override
    public BizDocument requireDocument(Long documentId) {
        BizDocument document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "单据不存在");
        }
        return document;
    }

    @Override
    public void appendEvent(Long documentId, EventType eventType, String fromStatus, String toStatus,
                            Long actorId, ActorType actorType, String reason, Long taskId, String requestKey) {
        DocumentEvent event = new DocumentEvent();
        event.setDocumentId(documentId);
        event.setEventType(eventType);
        event.setFromStatus(fromStatus);
        event.setToStatus(toStatus);
        event.setActorId(actorId);
        event.setActorType(actorType);
        event.setOccurredAt(Instant.now());
        event.setReason(reason);
        event.setTaskId(taskId);
        event.setRequestKey(requestKey);
        eventMapper.insert(event);
    }
}
