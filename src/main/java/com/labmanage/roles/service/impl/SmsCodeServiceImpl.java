package com.labmanage.roles.service.impl;

import com.labmanage.common.exception.BusinessException;
import com.labmanage.roles.service.PolicyService;
import com.labmanage.roles.service.SmsCodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 短信验证码服务实现：一期单实例内存存储（重启失效），模拟通道写日志；
 * 多实例部署时迁移至共享存储。限流与有效期参数取自 business_policy。
 */
@Service
public class SmsCodeServiceImpl implements SmsCodeService {

    private static final Logger log = LoggerFactory.getLogger(SmsCodeServiceImpl.class);

    private final PolicyService policyService;

    private final SecureRandom random = new SecureRandom();

    private final Map<String, CodeEntry> codes = new ConcurrentHashMap<>();

    public SmsCodeServiceImpl(PolicyService policyService) {
        this.policyService = policyService;
    }

    private record CodeEntry(String code, Instant expiresAt, int failCount,
                             Instant lastSentAt, LocalDate countDate, int sentToday) {
    }

    @Override
    public int send(String phone) {
        int ttl = policyService.getInt("SMS_CODE_TTL_SECONDS", "ttlSeconds", 300);
        int interval = policyService.getInt("SMS_RESEND_INTERVAL_SECONDS", "intervalSeconds", 60);
        int dailyLimit = policyService.getInt("SMS_DAILY_LIMIT", "dailyLimit", 10);
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String code = String.format("%06d", random.nextInt(1_000_000));

        codes.compute(phone, (key, old) -> {
            int sentToday = old != null && old.countDate().equals(today) ? old.sentToday() : 0;
            if (old != null && old.lastSentAt().plusSeconds(interval).isAfter(now)) {
                throw new BusinessException(40900, "验证码发送过于频繁，请稍后再试");
            }
            if (sentToday >= dailyLimit) {
                throw new BusinessException(40900, "今日验证码发送次数已达上限");
            }
            return new CodeEntry(code, now.plusSeconds(ttl), 0, now, today, sentToday + 1);
        });

        log.info("【模拟短信通道】手机号 {} 验证码：{}（{} 秒内有效）", phone, code, ttl);
        return ttl;
    }

    @Override
    public void verify(String phone, String code) {
        int maxFail = policyService.getInt("SMS_CODE_MAX_FAIL", "maxFailCount", 5);
        CodeEntry entry = codes.get(phone);
        if (entry == null || !entry.expiresAt().isAfter(Instant.now())) {
            throw new BusinessException(40100, "验证码已失效，请重新获取");
        }
        if (entry.code().equals(code)) {
            codes.remove(phone, entry);
            return;
        }
        int failCount = entry.failCount() + 1;
        if (failCount >= maxFail) {
            codes.remove(phone, entry);
            throw new BusinessException(40100, "验证码错误次数过多，已作废，请重新获取");
        }
        codes.put(phone, new CodeEntry(entry.code(), entry.expiresAt(), failCount,
                entry.lastSentAt(), entry.countDate(), entry.sentToday()));
        throw new BusinessException(40100, "验证码错误，还可尝试 " + (maxFail - failCount) + " 次");
    }
}
