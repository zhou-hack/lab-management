package com.labmanage.testsupport;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 测试专用登录端点（仅存在于 test classpath，不进入生产构建产物）
 * <p>
 * auth 模块尚未落地，集成测试通过该端点为指定用户换取 Sa-Token 令牌，
 * 走真实 HTTP 栈保证鉴权链路与生产一致。
 */
@RestController
@RequestMapping("/test/login")
public class LoginTestController {

    @PostMapping("/{userId}")
    public Map<String, String> login(@PathVariable Long userId) {
        StpUtil.login(userId);
        return Map.of("token", StpUtil.getTokenValue());
    }
}
