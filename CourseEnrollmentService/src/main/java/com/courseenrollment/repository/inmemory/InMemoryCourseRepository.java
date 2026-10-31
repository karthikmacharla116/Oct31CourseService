package com.courseenrollment.repository.inmemory;

import com.courseenrollment.domain.Course;
import com.courseenrollment.repository.CourseRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryCourseRepository implements CourseRepository {
    private final ConcurrentMap<Long, Course> courses = new ConcurrentHashMap<>();

    @Override
    public Optional<Course> findById(long courseId) {
        return Optional.ofNullable(courses.get(courseId));
    }

    @Override
    public void save(Course course) {
        courses.put(course.courseId(), course);
    }

    @Override
    public List<Course> findAll() {
        return new ArrayList<>(courses.values());
    }
}
