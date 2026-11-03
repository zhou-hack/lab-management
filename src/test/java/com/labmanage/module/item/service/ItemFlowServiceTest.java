package com.labmanage.module.item.service;

import com.labmanage.module.document.entity.BizDocument;
import com.labmanage.module.document.mapper.ApprovalTaskMapper;
import com.labmanage.module.document.mapper.BizDocumentMapper;
import com.labmanage.module.document.mapper.DocumentEventMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItemFlowServiceTest {

    private BizDocumentMapper documentMapper;
    private ItemFlowService flowService;

    @BeforeEach
    void setUp() {
        documentMapper = mock(BizDocumentMapper.class);
        flowService = new ItemFlowService(documentMapper, mock(ApprovalTaskMapper.class),
                mock(DocumentEventMapper.class), Clock.fixed(Instant.parse("2026-10-31T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void sameDocumentNoNeverReusesAnotherIdempotentDocument() {
        BizDocument candidate = document("SL2026103100001", "request-b");
        when(documentMapper.insert(any(BizDocument.class))).thenThrow(new DuplicateKeyException("duplicate document_no"));

        assertThatThrownBy(() -> flowService.createDocumentByOwnerIdempotency(candidate))
                .isInstanceOf(DuplicateKeyException.class);
        verify(documentMapper, never()).selectByDocumentNo(any());
    }

    @Test
    void sameOwnerTypeAndIdempotencyKeyReturnsOriginal() {
        BizDocument existing = document("SL2026103100001", "request-a");
        BizDocument replay = document("SL2026103100002", "request-a");
        when(documentMapper.selectOne(any())).thenReturn(existing);

        assertThat(flowService.createDocumentByOwnerIdempotency(replay)).isSameAs(existing);
        verify(documentMapper, never()).insert(any(BizDocument.class));
    }

    private BizDocument document(String documentNo, String idempotencyKey) {
        BizDocument document = new BizDocument();
        document.setDocumentNo(documentNo);
        document.setDocumentType("ITEM_CLAIM");
        document.setOwnerUserId(21L);
        document.setIdempotencyKey(idempotencyKey);
        return document;
    }
}
