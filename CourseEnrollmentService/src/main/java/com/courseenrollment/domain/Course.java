package com.courseenrollment.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** Immutable course catalogue information. Live seat and waitlist data lives in CourseRegistrationState. */
public record Course(long courseId, String courseName, BigDecimal price, int capacity) {
    public Course {
        if (courseId <= 0) {
            throw new IllegalArgumentException("courseId must be positive");
        }
        if (courseName == null || courseName.isBlank()) {
            throw new IllegalArgumentException("courseName is required");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        price = Objects.requireNonNullElse(price, BigDecimal.ZERO);
        if (price.signum() < 0) {
            throw new IllegalArgumentException("price cannot be negative");
        }
    }
}
