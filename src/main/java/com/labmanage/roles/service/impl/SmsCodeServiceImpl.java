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
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 短信验证码服务实现：一期单实例内存存储（重启失效），模拟通道写日志；
 * 多实例部署时迁移至共享存储。限流与有效期参数取自 business_policy。
 * <p>
 * 校验采用 compute 原子更新（B7），发送时惰性清理过期条目防止内存增长（B10），
 * 每日发送上限按业务时区 Asia/Shanghai 的日历日计数（B11）。
 */
@Service
public class SmsCodeServiceImpl implements SmsCodeService {

    private static final Logger log = LoggerFactory.getLogger(SmsCodeServiceImpl.class);

    /** 业务时区：每日发送上限按上海日历日统计 */
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    /** 惰性清理触发阈值：条目数超过该值时顺手移除已过期条目 */
    private static final int PURGE_THRESHOLD = 1024;

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
        LocalDate today = LocalDate.now(SHANGHAI);
        String code = String.format("%06d", random.nextInt(1_000_000));
        purgeExpired(now);

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
        // B7：compute 原子完成「读取-判定-更新」，避免并发校验竞态导致错误计数失真；
        // 错误消息经 holder 携带出 compute 后再抛出，保证失败计数先落库（内存）再报错
        String[] errorMsg = new String[1];
        codes.compute(phone, (key, entry) -> {
            if (entry == null || !entry.expiresAt().isAfter(Instant.now())) {
                errorMsg[0] = "验证码已失效，请重新获取";
                return null;
            }
            if (entry.code().equals(code)) {
                return null;
            }
            int failCount = entry.failCount() + 1;
            if (failCount >= maxFail) {
                errorMsg[0] = "验证码错误次数过多，已作废，请重新获取";
                return null;
            }
            errorMsg[0] = "验证码错误，还可尝试 " + (maxFail - failCount) + " 次";
            return new CodeEntry(entry.code(), entry.expiresAt(), failCount,
                    entry.lastSentAt(), entry.countDate(), entry.sentToday());
        });
        if (errorMsg[0] != null) {
            throw new BusinessException(40100, errorMsg[0]);
        }
    }

    /** B10：惰性清理过期条目，条目数超阈值时在发送路径顺手移除，避免长期运行内存增长 */
    private void purgeExpired(Instant now) {
        if (codes.size() <= PURGE_THRESHOLD) {
            return;
        }
        int before = codes.size();
        codes.entrySet().removeIf(e -> !e.getValue().expiresAt().isAfter(now));
        int removed = before - codes.size();
        if (removed > 0) {
            log.info("[短信验证码] 惰性清理过期条目 {} 条，剩余 {} 条", removed, codes.size());
        }
    }
}
