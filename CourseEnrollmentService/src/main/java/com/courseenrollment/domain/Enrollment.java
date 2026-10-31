package com.courseenrollment.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One registration attempt linking a user and a course.
 * A re-registration after withdrawal creates a new Enrollment so prior history is retained.
 */
public final class Enrollment {
    private final long enrollmentId;
    private final long userId;
    private final long courseId;
    private final LocalDateTime createdAt;
    private volatile EnrollmentStatus status;

    public Enrollment(long enrollmentId, long userId, long courseId, EnrollmentStatus status) {
        if (enrollmentId <= 0 || userId <= 0 || courseId <= 0) {
            throw new IllegalArgumentException("Enrollment identifiers must be positive");
        }
        this.enrollmentId = enrollmentId;
        this.userId = userId;
        this.courseId = courseId;
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = LocalDateTime.now();
    }

    public long getEnrollmentId() { return enrollmentId; }
    public long getUserId() { return userId; }
    public long getCourseId() { return courseId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public EnrollmentStatus getStatus() { return status; }

    /** Moves an active enrollment or waitlist entry to its terminal withdrawn state. */
    public void withdraw() {
        if (status == EnrollmentStatus.WITHDRAWN) {
            throw new IllegalStateException("Enrollment is already withdrawn");
        }
        status = EnrollmentStatus.WITHDRAWN;
    }

    /** Promotes a waitlisted enrollment after a seat becomes available. */
    public void promote() {
        if (status != EnrollmentStatus.WAITLISTED) {
            throw new IllegalStateException("Only a waitlisted enrollment can be promoted");
        }
        status = EnrollmentStatus.ENROLLED;
    }
}
