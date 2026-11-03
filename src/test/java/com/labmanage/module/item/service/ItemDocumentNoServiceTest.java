package com.labmanage.module.item.service;

import com.labmanage.common.exception.BusinessException;
import com.labmanage.module.document.mapper.BizDocumentMapper;
import com.labmanage.module.item.entity.MaterialClaim;
import com.labmanage.module.item.mapper.MaterialClaimMapper;
import com.labmanage.module.item.mapper.MaterialDocumentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ItemDocumentNoServiceTest {

    private BizDocumentMapper bizDocumentMapper;
    private MaterialDocumentMapper documentMapper;
    private MaterialClaimMapper claimMapper;
    private SecureRandom random;

    @BeforeEach
    void setUp() {
        bizDocumentMapper = mock(BizDocumentMapper.class);
        documentMapper = mock(MaterialDocumentMapper.class);
        claimMapper = mock(MaterialClaimMapper.class);
        random = mock(SecureRandom.class);
    }

    @Test
    void usesShanghaiDateAndPadsRandomNumber() {
        when(random.nextInt(100000)).thenReturn(7);
        ItemDocumentNoService service = serviceAt("2026-10-31T16:00:00Z");

        assertThat(service.nextDocumentNo("SL", Instant.parse("2026-10-31T16:00:00Z")))
                .isEqualTo("SL2026110100007");
    }

    @Test
    void retriesWhenCandidateDocumentNoExists() {
        Instant now = Instant.parse("2026-10-31T00:30:00Z");
        when(random.nextInt(100000)).thenReturn(1, 1, 2);
        when(bizDocumentMapper.countByDocumentNo("CK2026103100001")).thenReturn(1L);

        assertThat(serviceAt("2026-10-31T00:30:00Z").nextDocumentNo("CK", now))
                .isEqualTo("CK2026103100002");
    }

    @Test
    void generatesAndResolvesExternalBusinessChainNoWithLegacyFallback() {
        Instant now = Instant.parse("2026-10-31T00:30:00Z");
        when(random.nextInt(100000)).thenReturn(9);
        ItemDocumentNoService service = serviceAt("2026-10-31T00:30:00Z");
        assertThat(service.nextBusinessChainNo(now)).isEqualTo("ALL2026103100009");

        MaterialClaim current = new MaterialClaim();
        current.setId(99L);
        current.setBusinessChainNo("ALL2026103100009");
        when(claimMapper.selectByBusinessChainNo("ALL2026103100009")).thenReturn(current);
        assertThat(service.resolveBusinessChainId("ALL2026103100009")).isEqualTo(99L);
        assertThat(ItemDocumentNoService.displayBusinessChainId(current)).isEqualTo("ALL2026103100009");

        MaterialClaim legacy = new MaterialClaim();
        legacy.setId(88L);
        assertThat(ItemDocumentNoService.displayBusinessChainId(legacy)).isEqualTo("88");
        assertThat(service.resolveBusinessChainId("88")).isEqualTo(88L);
        assertThat(service.resolveBusinessChainId("ALL2099010100000")).isEqualTo(-1L);
    }

    @Test
    void rejectsExhaustedOrInvalidNumbers() {
        Instant now = Instant.parse("2026-10-31T00:30:00Z");
        when(random.nextInt(100000)).thenReturn(1);
        when(bizDocumentMapper.countByDocumentNo(anyString())).thenReturn(1L);
        ItemDocumentNoService service = serviceAt("2026-10-31T00:30:00Z");

        assertThatThrownBy(() -> service.nextDocumentNo("BF", now))
                .isInstanceOf(BusinessException.class)
                .hasMessage("单据编号生成冲突，请重试");
        assertThatThrownBy(() -> service.resolveBusinessChainId("invalid"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("业务链ID格式不正确");
    }

    private ItemDocumentNoService serviceAt(String instant) {
        Clock clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
        return new ItemDocumentNoService(bizDocumentMapper, documentMapper, claimMapper,
                new ItemTimeService(null, clock), random);
    }
}
