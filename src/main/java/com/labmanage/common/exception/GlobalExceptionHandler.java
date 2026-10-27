package com.labmanage.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.exception.NotSafeException;
import cn.dev33.satoken.exception.SaTokenException;
import com.labmanage.common.result.Result;
import com.labmanage.common.result.ResultCode;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 业务异常：HTTP 状态按错误码映射（40000→400、40100→401、40300→403、
     * 40400→404、50000→500，其余/空→409），保证 HTTP 状态反映真实结果。
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e, HttpServletResponse response) {
        int httpStatus = toHttpStatus(e.getCode());
        response.setStatus(httpStatus);
        // B16：业务异常留痕——5xx 映射告警级，40900 等业务冲突 info 级（排查占用/状态竞争现场）
        if (httpStatus >= 500) {
            log.warn("[业务异常] code={}, msg={}", e.getCode(), e.getMessage());
        } else {
            log.info("[业务异常] code={}, msg={}", e.getCode(), e.getMessage());
        }
        return Result.error(e.getCode(), e.getMessage());
    }

    private int toHttpStatus(Integer code) {
        if (code == null) {
            return HttpStatus.CONFLICT.value();
        }
        return switch (code) {
            case 40000 -> HttpStatus.BAD_REQUEST.value();
            case 40100 -> HttpStatus.UNAUTHORIZED.value();
            case 40300, 40301 -> HttpStatus.FORBIDDEN.value();
            case 40400 -> HttpStatus.NOT_FOUND.value();
            case 50000 -> HttpStatus.INTERNAL_SERVER_ERROR.value();
            default -> HttpStatus.CONFLICT.value();
        };
    }

    /**
     * 参数校验异常 - @Valid / @Validated
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(BindException e) {
        String message = e.getBindingResult().getFieldError() != null
                ? e.getBindingResult().getFieldError().getDefaultMessage()
                : ResultCode.BAD_REQUEST.getMsg();
        // 关键日志：字段级校验失败留痕（如容量超 9999 上限），便于排查前端绕过/并发写冲突
        log.warn("[参数校验] 拦截：字段={}, 原因={}",
                e.getBindingResult().getFieldError() != null ? e.getBindingResult().getFieldError().getField() : "-",
                message);
        return Result.error(ResultCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * 参数校验异常 - @RequestParam / @PathVariable
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().iterator().next().getMessage();
        return Result.error(ResultCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * 请求参数缺失或类型不匹配
     */
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBadRequestException(Exception e) {
        return Result.error(ResultCode.BAD_REQUEST.getCode(), ResultCode.BAD_REQUEST.getMsg());
    }

    /**
     * 唯一键冲突（并发/重复提交撞唯一索引，如场地名称 uk_lab_name 并发撞名）：
     * 统一映射 40900，避免落入兜底 500（清单 B18）。全局生效，预约、授权等模块同样受益。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<Void> handleDuplicateKeyException(DuplicateKeyException e) {
        log.warn("[数据冲突] 唯一键约束拦截：{}", e.getMessage());
        return Result.error(ResultCode.CONFLICT.getCode(), "数据已存在（可能与他人操作冲突），请刷新后重试");
    }

    /**
     * 上传文件超过配置上限（场地导入单文件 10MB / 请求 12MB）
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("[上传超限] {}", e.getMessage());
        return Result.error(ResultCode.BAD_REQUEST.getCode(), "上传文件过大，单文件不能超过 10MB");
    }

    /**
     * 请求方法不支持
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<Void> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        return Result.error(ResultCode.BAD_REQUEST.getCode(), "请求方法不支持");
    }

    /**
     * 404 - 路径不存在
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoHandlerFoundException(NoHandlerFoundException e) {
        return Result.error(ResultCode.NOT_FOUND.getCode(), ResultCode.NOT_FOUND.getMsg());
    }

    /**
     * 404 - 未匹配资源（Spring Framework 6.1+ 默认抛出）
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoResourceFoundException(NoResourceFoundException e) {
        return Result.error(ResultCode.NOT_FOUND.getCode(), ResultCode.NOT_FOUND.getMsg());
    }

    /**
     * Sa-Token 未登录
     */
    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleNotLoginException(NotLoginException e) {
        return Result.error(ResultCode.UNAUTHORIZED.getCode(), ResultCode.UNAUTHORIZED.getMsg());
    }

    /**
     * Sa-Token 无权限
     */
    @ExceptionHandler(NotPermissionException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleNotPermissionException(NotPermissionException e) {
        return Result.error(ResultCode.FORBIDDEN.getCode(), ResultCode.FORBIDDEN.getMsg());
    }

    /**
     * Sa-Token 无角色（B6：如文档端点的 ADMIN 角色校验失败）
     */
    @ExceptionHandler(NotRoleException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleNotRoleException(NotRoleException e) {
        return Result.error(ResultCode.FORBIDDEN.getCode(), ResultCode.FORBIDDEN.getMsg());
    }

    /**
     * Sa-Token 二级认证未通过（B3：敏感操作未在 openSafe 有效期内）。
     * 返回独立错误码 40301，前端据此弹出密码确认框后自动重试原请求。
     */
    @ExceptionHandler(NotSafeException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleNotSafeException(NotSafeException e) {
        log.info("[二级认证] 敏感操作未通过二级认证：{}", e.getMessage());
        return Result.error(ResultCode.NEED_SAFE_AUTH.getCode(), "敏感操作需先完成密码确认");
    }

    /**
     * Sa-Token 其余异常（B6：伪造/畸形 token、二级认证校验失败等）统一按未认证处理，
     * 不落入兜底 500，也不泄露 token 校验内部细节
     */
    @ExceptionHandler(SaTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleSaTokenException(SaTokenException e) {
        log.warn("[认证异常] {}：{}", e.getClass().getSimpleName(), e.getMessage());
        return Result.error(ResultCode.UNAUTHORIZED.getCode(), ResultCode.UNAUTHORIZED.getMsg());
    }

    /**
     * 其他未捕获异常：记录堆栈便于排查，响应不泄露内部实现
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("[系统异常] 未捕获异常：{} - {}", e.getClass().getSimpleName(), e.getMessage(), e);
        return Result.error(ResultCode.INTERNAL_ERROR.getCode(), ResultCode.INTERNAL_ERROR.getMsg());
    }
}
