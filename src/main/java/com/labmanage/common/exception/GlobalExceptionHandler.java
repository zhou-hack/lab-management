package com.labmanage.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotSafeException;
import com.labmanage.common.result.Result;
import com.labmanage.common.result.ResultCode;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        response.setStatus(toHttpStatus(e.getCode()));
        return Result.error(e.getCode(), e.getMessage());
    }

    private int toHttpStatus(Integer code) {
        if (code == null) {
            return HttpStatus.CONFLICT.value();
        }
        return switch (code) {
            case 40000 -> HttpStatus.BAD_REQUEST.value();
            case 40100, 40101 -> HttpStatus.UNAUTHORIZED.value();
            case 40300 -> HttpStatus.FORBIDDEN.value();
            case 40400 -> HttpStatus.NOT_FOUND.value();
            case 50000 -> HttpStatus.INTERNAL_SERVER_ERROR.value();
            default -> HttpStatus.CONFLICT.value();
        };
    }

    /**
     * Sa-Token 二级认证未通过：敏感操作需先完成二次密码确认（40101，前端弹确认框而非跳登录）
     */
    @ExceptionHandler(NotSafeException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleNotSafeException(NotSafeException e) {
        return Result.error(ResultCode.SAFE_AUTH_REQUIRED.getCode(), ResultCode.SAFE_AUTH_REQUIRED.getMsg());
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
     * 上传文件超过大小限制（Excel 导入超限等）
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        return Result.error(ResultCode.BAD_REQUEST.getCode(), "上传文件过大，请分批导入");
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
     * 其他未捕获异常：记录堆栈便于排查，响应不泄露内部实现
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("[系统异常] 未捕获异常：{} - {}", e.getClass().getSimpleName(), e.getMessage(), e);
        return Result.error(ResultCode.INTERNAL_ERROR.getCode(), ResultCode.INTERNAL_ERROR.getMsg());
    }
}
