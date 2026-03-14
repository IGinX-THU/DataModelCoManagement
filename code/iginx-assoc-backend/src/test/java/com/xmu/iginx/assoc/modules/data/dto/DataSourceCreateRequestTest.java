package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataSourceCreateRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validate_shouldAllowBlankMountPath() {
        DataSourceCreateRequest request = new DataSourceCreateRequest();
        request.setName("demo");
        request.setSourceType("INFLUXDB");
        request.setMountPath("");

        Set<ConstraintViolation<DataSourceCreateRequest>> violations = validator.validate(request);
        boolean hasMountPathViolation = violations.stream()
            .anyMatch(v -> "mountPath".equals(String.valueOf(v.getPropertyPath())));

        assertFalse(hasMountPathViolation);
    }

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
