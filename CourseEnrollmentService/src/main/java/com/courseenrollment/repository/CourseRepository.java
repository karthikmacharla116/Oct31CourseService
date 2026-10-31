package com.courseenrollment.repository;

import com.courseenrollment.domain.Course;

import java.util.List;
import java.util.Optional;

/** Storage contract for the course catalogue. */
public interface CourseRepository {
    Optional<Course> findById(long courseId);
    void save(Course course);
    List<Course> findAll();
}
