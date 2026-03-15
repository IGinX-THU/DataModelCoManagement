package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据源创建请求校验测试。
 */
class DataSourceCreateRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /**
     * 验证名称与类型为必填项。
     */
    @Test
    void validate_shouldRequireNameAndSourceType() {
        DataSourceCreateRequest request = new DataSourceCreateRequest();

        Set<ConstraintViolation<DataSourceCreateRequest>> violations = validator.validate(request);
        boolean hasNameViolation = violations.stream()
            .anyMatch(v -> "name".equals(String.valueOf(v.getPropertyPath())));
        boolean hasTypeViolation = violations.stream()
            .anyMatch(v -> "sourceType".equals(String.valueOf(v.getPropertyPath())));

        assertTrue(hasNameViolation);
        assertTrue(hasTypeViolation);
    }
}
