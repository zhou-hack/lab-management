package com.labmanage.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 签发与校验（HS256）。
 * access token 短效（默认 2h）承载身份；refresh token 长效（默认 7d）仅用于换新，
 * 两者均无状态、服务端不存储，注销即客户端丢弃（内网/开发场景接受自然过期）。
 */
@Component
public class JwtService {

    public static final String CLAIM_TYPE = "typ";
    public static final String CLAIM_ACTIVE_ROLE = "role";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final String issuer;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.issuer:lab-management}") String issuer,
                      @Value("${app.jwt.access-ttl-seconds:7200}") long accessTtlSeconds,
                      @Value("${app.jwt.refresh-ttl-seconds:604800}") long refreshTtlSeconds) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt.secret 至少 32 字节（HS256 要求），请通过环境变量 JWT_SECRET 配置");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.accessTtl = Duration.ofSeconds(accessTtlSeconds);
        this.refreshTtl = Duration.ofSeconds(refreshTtlSeconds);
    }

    /** 签发 access token（sub=userId，携带活动角色声明用于多角色身份视图） */
    public String issueAccess(Long userId, String activeRole) {
        return issue(userId, activeRole, TYPE_ACCESS, accessTtl);
    }

    /** 签发 refresh token（sub=userId，仅换 token 用；刷新时保留活动角色） */
    public String issueRefresh(Long userId, String activeRole) {
        return issue(userId, activeRole, TYPE_REFRESH, refreshTtl);
    }

    private String issue(Long userId, String activeRole, String type, Duration ttl) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)));
        if (activeRole != null && !activeRole.isBlank()) {
            builder.claim(CLAIM_ACTIVE_ROLE, activeRole);
        }
        return builder.signWith(key).compact();
    }

    /**
     * 校验并解析 userId；类型不符、签名错误或过期返回 null（由调用方转 40100）。
     */
    public Long parseUserId(String token, String expectType) {
        Parsed parsed = parse(token, expectType);
        return parsed == null ? null : parsed.userId();
    }

    /**
     * 校验并解析 userId 与活动角色声明（角色缺失返回 null，由服务端按默认活动角色兜底）。
     */
    public Parsed parse(String token, String expectType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!expectType.equals(claims.get(CLAIM_TYPE, String.class))) {
                return null;
            }
            return new Parsed(Long.valueOf(claims.getSubject()),
                    claims.get(CLAIM_ACTIVE_ROLE, String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /** 解析结果：userId + 活动角色声明（无角色声明时 activeRole 为 null） */
    public record Parsed(Long userId, String activeRole) {
    }

    public long getAccessTtlSeconds() {
        return accessTtl.toSeconds();
    }
}
