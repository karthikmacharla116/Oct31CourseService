package com.courseenrollment.repository.inmemory;

import com.courseenrollment.domain.CourseRegistrationState;
import com.courseenrollment.repository.CourseRegistrationStateRepository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryCourseRegistrationStateRepository implements CourseRegistrationStateRepository {
    private final ConcurrentMap<Long, CourseRegistrationState> states = new ConcurrentHashMap<>();

    @Override
    public Optional<CourseRegistrationState> findByCourseId(long courseId) {
        return Optional.ofNullable(states.get(courseId));
    }

    @Override
    public void createForCourse(long courseId, int capacity, int waitlistLimit) {
        CourseRegistrationState state = new CourseRegistrationState(courseId, capacity, waitlistLimit);
        if (states.putIfAbsent(courseId, state) != null) {
            throw new IllegalStateException("Registration state already exists for course " + courseId);
        }
    }
}
