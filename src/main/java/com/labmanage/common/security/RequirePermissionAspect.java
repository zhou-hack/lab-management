package com.labmanage.common.security;

import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.ResultCode;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * @RequirePermission 校验切面：未登录 40100、无权限 40300。
 */
@Aspect
@Component
public class RequirePermissionAspect {

    @Before("@annotation(requirePermission)")
    public void check(RequirePermission requirePermission) {
        LoginUser user = AuthContext.get();
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), ResultCode.UNAUTHORIZED.getMsg());
        }
        boolean pass = requirePermission.mode() == RequirePermission.Mode.OR
                ? anyMatch(user, requirePermission.value())
                : allMatch(user, requirePermission.value());
        if (!pass) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), ResultCode.FORBIDDEN.getMsg());
        }
    }

    private boolean anyMatch(LoginUser user, String[] permissions) {
        for (String permission : permissions) {
            if (user.hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    private boolean allMatch(LoginUser user, String[] permissions) {
        for (String permission : permissions) {
            if (!user.hasPermission(permission)) {
                return false;
            }
        }
        return true;
    }
}
