package com.labmanage.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import com.labmanage.common.result.Result;
import com.labmanage.common.result.ResultCode;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    /**
     * 业务异常（HTTP 状态随错误码映射：40000→400、40100→401、40300→403、
     * 40400→404、40900→409、50000→500）
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        return ResponseEntity.status(resolveHttpStatus(e.getCode()))
                .body(Result.error(e.getCode(), e.getMessage()));
    }

    private HttpStatus resolveHttpStatus(Integer code) {
        if (code == null) {
            return HttpStatus.CONFLICT;
        }
        return switch (code / 100) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            case 500 -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.CONFLICT;
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
     * 404 - 路径不存在（Spring 6.1+ 静态资源链抛 NoResourceFoundException）
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoHandlerFoundException(Exception e) {
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
     * 唯一键冲突（业务层未先行拦截的并发重复提交兜底；
     * 幂等场景由服务层捕获后查原单返回，不走到这里）
     */
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<Void> handleDuplicateKeyException(DuplicateKeyException e) {
        return Result.error(ResultCode.CONFLICT.getCode(), ResultCode.CONFLICT.getMsg());
    }

    /**
     * 其他未捕获异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        return Result.error(ResultCode.INTERNAL_ERROR.getCode(), ResultCode.INTERNAL_ERROR.getMsg());
    }
}
