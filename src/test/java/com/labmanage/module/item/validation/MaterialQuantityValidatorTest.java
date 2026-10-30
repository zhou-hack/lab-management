package com.labmanage.module.item.validation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaterialQuantityValidatorTest {

    private final MaterialQuantityValidator quantityValidator = new MaterialQuantityValidator();

    @Test
    void acceptsIntegerZeroAndSignedValuesWithoutChangingThem() {
        assertThatCode(() -> {
            quantityValidator.requireInteger(new BigDecimal("1"), "数量");
            quantityValidator.requireInteger(new BigDecimal("1.000"), "数量");
            quantityValidator.requireInteger(BigDecimal.ZERO, "数量");
            quantityValidator.requireInteger(new BigDecimal("-2"), "数量");
            quantityValidator.requirePositiveInteger(new BigDecimal("1.000"), "数量");
            quantityValidator.requireNonNegativeInteger(BigDecimal.ZERO, "数量");
        }).doesNotThrowAnyException();
    }

    @Test
    void rejectsFractionsInsteadOfRoundingOrTruncating() {
        assertThatThrownBy(() -> quantityValidator.requireInteger(new BigDecimal("1.5"), "物料数量"))
                .hasMessage("物料数量必须为整数");
        assertThatThrownBy(() -> quantityValidator.requirePositiveInteger(new BigDecimal("1.5"), "物料数量"))
                .hasMessage("物料数量必须为整数");
        assertThatThrownBy(() -> quantityValidator.requireNonNegativeInteger(new BigDecimal("0.5"), "物料数量"))
                .hasMessage("物料数量必须为整数");
    }

    @Test
    void enforcesFifteenIntegerDigitsAndPositiveOrNonNegativeRules() {
        assertThatCode(() -> {
            quantityValidator.requireInteger(new BigDecimal("999999999999999"), "数量");
            quantityValidator.requireInteger(new BigDecimal("-999999999999999"), "数量");
        }).doesNotThrowAnyException();
        assertThatThrownBy(() -> quantityValidator.requireInteger(new BigDecimal("1000000000000000"), "数量"))
                .hasMessage("数量整数位数不能超过15位");
        assertThatThrownBy(() -> quantityValidator.requirePositiveInteger(BigDecimal.ZERO, "数量"))
                .hasMessage("数量必须大于0");
        assertThatThrownBy(() -> quantityValidator.requirePositiveInteger(BigDecimal.ONE.negate(), "数量"))
                .hasMessage("数量必须大于0");
        assertThatThrownBy(() -> quantityValidator.requireNonNegativeInteger(BigDecimal.ONE.negate(), "数量"))
                .hasMessage("数量不能为负数");
    }

    @Test
    void beanValidationAcceptsNullAndIntegerFormsAndSupportsConfiguredDigits() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        assertThat(validator.validate(new DefaultDigits(null))).isEmpty();
        assertThat(validator.validate(new DefaultDigits(new BigDecimal("1.000")))).isEmpty();
        assertThat(validator.validate(new DefaultDigits(new BigDecimal("1.5"))))
                .singleElement().satisfies(violation -> assertThat(violation.getMessage()).contains("必须为整数"));
        assertThat(validator.validate(new DefaultDigits(new BigDecimal("1000000000000000")))).hasSize(1);
        assertThat(validator.validate(new ThreeDigits(new BigDecimal("999")))).isEmpty();
        assertThat(validator.validate(new ThreeDigits(new BigDecimal("1000")))).hasSize(1);
    }

    private record DefaultDigits(@IntegerQuantity BigDecimal quantity) {
    }

    private record ThreeDigits(@IntegerQuantity(integer = 3) BigDecimal quantity) {
    }
}
