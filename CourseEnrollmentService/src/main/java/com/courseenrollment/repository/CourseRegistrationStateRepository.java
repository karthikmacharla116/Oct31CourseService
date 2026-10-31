package com.courseenrollment.repository;

import com.courseenrollment.domain.CourseRegistrationState;

import java.util.Optional;

/** Provides the mutable capacity and waitlist state associated with each course. */
public interface CourseRegistrationStateRepository {
    Optional<CourseRegistrationState> findByCourseId(long courseId);
    void createForCourse(long courseId, int capacity, int waitlistLimit);
}
