package com.labmanage.common.security;

import tools.jackson.databind.ObjectMapper;
import com.labmanage.common.result.Result;
import com.labmanage.common.result.ResultCode;
import com.labmanage.roles.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * JWT 认证过滤器：/api/v1/** 默认要求有效 access token（Authorization: Bearer），
 * 仅放行公开端点（登录/取盐/刷新）。装配 AuthContext 后放行，
 * 角色与权限每请求实时加载（权限变更即时生效，无会话缓存陈旧问题）。
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String PREFIX = "/api/v1/";
    private static final String BEARER = "Bearer ";

    /** 公开端点（无需 token） */
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/salt",
            "/api/v1/auth/refresh"
    );

    private final JwtService jwtService;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(JwtService jwtService, AuthService authService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith(PREFIX) || PUBLIC_PATHS.contains(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        JwtService.Parsed parsed = header != null && header.startsWith(BEARER)
                ? jwtService.parse(header.substring(BEARER.length()).trim(), JwtService.TYPE_ACCESS)
                : null;
        if (parsed == null) {
            writeUnauthorized(response);
            return;
        }
        LoginUser user = authService.buildLoginUser(parsed.userId(), parsed.activeRole());
        if (user == null) {
            writeUnauthorized(response);
            return;
        }
        AuthContext.set(user);
        try {
            chain.doFilter(request, response);
        } finally {
            AuthContext.clear();
        }
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.error(ResultCode.UNAUTHORIZED.getCode(), ResultCode.UNAUTHORIZED.getMsg())));
    }
}
