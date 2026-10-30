package com.labmanage.module.item.dto;

import jakarta.validation.constraints.Size;

public record ClaimDelegationDTO(
        @Size(max = 32, message = "学生ID长度不能超过32") String studentId
) {
}
