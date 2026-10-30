package com.labmanage.module.item.validation;

import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.ResultCode;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class MaterialQuantityValidator {

    public static final int DEFAULT_INTEGER_DIGITS = 15;

    public void requireInteger(BigDecimal quantity, String label) {
        requireInteger(quantity, label, DEFAULT_INTEGER_DIGITS);
    }

    public void requirePositiveInteger(BigDecimal quantity, String label) {
        requireInteger(quantity, label);
        if (quantity.signum() <= 0) {
            throw badRequest(label + "必须大于0");
        }
    }

    public void requireNonNegativeInteger(BigDecimal quantity, String label) {
        requireInteger(quantity, label);
        if (quantity.signum() < 0) {
            throw badRequest(label + "不能为负数");
        }
    }

    private void requireInteger(BigDecimal quantity, String label, int integerDigits) {
        if (quantity == null) {
            throw badRequest(label + "不能为空");
        }
        if (quantity.stripTrailingZeros().scale() > 0) {
            throw badRequest(label + "必须为整数");
        }
        if (!hasValidIntegerDigits(quantity, integerDigits)) {
            throw badRequest(label + "整数位数不能超过" + integerDigits + "位");
        }
    }

    private static boolean isValidInteger(BigDecimal quantity, int integerDigits) {
        return quantity.stripTrailingZeros().scale() <= 0
                && hasValidIntegerDigits(quantity, integerDigits);
    }

    private static boolean hasValidIntegerDigits(BigDecimal quantity, int integerDigits) {
        BigDecimal normalized = quantity.stripTrailingZeros();
        return normalized.signum() == 0
                || normalized.precision() - normalized.scale() <= integerDigits;
    }

    private static BusinessException badRequest(String message) {
        return new BusinessException(ResultCode.BAD_REQUEST.getCode(), message);
    }

    public static final class ConstraintAdapter implements ConstraintValidator<IntegerQuantity, BigDecimal> {

        private int integerDigits;

        @Override
        public void initialize(IntegerQuantity constraintAnnotation) {
            this.integerDigits = constraintAnnotation.integer();
        }

        @Override
        public boolean isValid(BigDecimal quantity, ConstraintValidatorContext context) {
            return quantity == null || isValidInteger(quantity, integerDigits);
        }
    }
}
