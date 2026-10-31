package com.courseenrollment.repository;

import com.courseenrollment.domain.Enrollment;

import java.util.List;
import java.util.Optional;

/** Stores enrollment history and exposes lookups for currently active enrollment attempts. */
public interface EnrollmentRepository {
    void saveNew(Enrollment enrollment, String identityNumber);
    Optional<Enrollment> findById(long enrollmentId);
    Optional<Enrollment> findActiveByCourseAndUser(long courseId, long userId);
    Optional<Enrollment> findActiveByCourseAndIdentity(long courseId, String identityNumber);
    Optional<Enrollment> findLatestByCourseAndUser(long courseId, long userId);
    void removeFromActiveIndexes(Enrollment enrollment);
    List<Enrollment> findAllByCourseId(long courseId);
}
