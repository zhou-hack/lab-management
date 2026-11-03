package com.labmanage.module.item.service;

import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.ResultCode;
import com.labmanage.module.document.mapper.BizDocumentMapper;
import com.labmanage.module.item.entity.MaterialClaim;
import com.labmanage.module.item.mapper.MaterialClaimMapper;
import com.labmanage.module.item.mapper.MaterialDocumentMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** 物料外部业务编号生成与业务链编号解析。 */
@Service
public class ItemDocumentNoService {

    private static final int RANDOM_BOUND = 100000;
    private static final int MAX_ATTEMPTS = 16;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final BizDocumentMapper bizDocumentMapper;
    private final MaterialDocumentMapper documentMapper;
    private final MaterialClaimMapper claimMapper;
    private final ItemTimeService timeService;
    private final SecureRandom random;

    @Autowired
    public ItemDocumentNoService(BizDocumentMapper bizDocumentMapper,
                                 MaterialDocumentMapper documentMapper,
                                 MaterialClaimMapper claimMapper,
                                 ItemTimeService timeService) {
        this(bizDocumentMapper, documentMapper, claimMapper, timeService, new SecureRandom());
    }

    ItemDocumentNoService(BizDocumentMapper bizDocumentMapper,
                          MaterialDocumentMapper documentMapper,
                          MaterialClaimMapper claimMapper,
                          ItemTimeService timeService,
                          SecureRandom random) {
        this.bizDocumentMapper = bizDocumentMapper;
        this.documentMapper = documentMapper;
        this.claimMapper = claimMapper;
        this.timeService = timeService;
        this.random = random;
    }

    public String nextDocumentNo(String prefix, Instant now) {
        return nextAvailable(prefix, now, candidate -> bizDocumentMapper.countByDocumentNo(candidate) == 0
                && documentMapper.countByDocumentNo(candidate) == 0);
    }

    public String nextBusinessChainNo(Instant now) {
        return nextAvailable("ALL", now, candidate -> claimMapper.countByBusinessChainNo(candidate) == 0);
    }

    public static String displayBusinessChainId(MaterialClaim claim) {
        return claim.getBusinessChainNo() == null ? String.valueOf(claim.getId()) : claim.getBusinessChainNo();
    }

    public Map<Long, String> businessChainDisplayValues(Collection<Long> claimIds) {
        if (claimIds == null || claimIds.isEmpty()) {
            return Map.of();
        }
        return claimMapper.selectBatchIds(claimIds).stream().collect(Collectors.toMap(
                MaterialClaim::getId, ItemDocumentNoService::displayBusinessChainId));
    }

    /** 新业务链号解析为内部申领主键；历史数值ID保持原查询语义。 */
    public Long resolveBusinessChainId(String value) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }
        if (normalized.startsWith("ALL")) {
            MaterialClaim claim = claimMapper.selectByBusinessChainNo(normalized);
            return claim == null ? -1L : claim.getId();
        }
        try {
            return Long.valueOf(normalized);
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "业务链ID格式不正确");
        }
    }

    private String nextAvailable(String prefix, Instant now, Predicate<String> available) {
        String date = DATE_FORMAT.format(timeService.shanghaiDate(now));
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = prefix + date + String.format(Locale.ROOT, "%05d", random.nextInt(RANDOM_BOUND));
            if (available.test(candidate)) {
                return candidate;
            }
        }
        throw new BusinessException(ResultCode.CONFLICT.getCode(), "单据编号生成冲突，请重试");
    }
}
