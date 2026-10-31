package com.courseenrollment.dto;

import java.util.List;

public record WithdrawalResult(
        WithdrawalOutcome outcome,
        String message,
        Long enrollmentId,
        List<Long> promotedUserIds) {
    public WithdrawalResult {
        promotedUserIds = promotedUserIds == null ? List.of() : List.copyOf(promotedUserIds);
    }
}
