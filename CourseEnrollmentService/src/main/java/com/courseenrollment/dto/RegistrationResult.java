package com.courseenrollment.dto;

/** Result returned to the CLI (or a future REST adapter) for a registration command. */
public record RegistrationResult(
        RegistrationOutcome outcome,
        String message,
        Long enrollmentId,
        Integer waitlistPosition) {
}
