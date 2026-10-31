package com.courseenrollment.repository.inmemory;

import com.courseenrollment.domain.Enrollment;
import com.courseenrollment.repository.EnrollmentRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Stores all enrollment attempts and maintains indexes for currently active registrations.
 * Identity-number deduplication is scoped to a course. Withdrawn attempts remain in history.
 */
public final class InMemoryEnrollmentRepository implements EnrollmentRepository {
    private final ConcurrentMap<Long, Enrollment> enrollmentsById = new ConcurrentHashMap<>();
    private final ConcurrentMap<UserCourseKey, Long> activeByUserCourse = new ConcurrentHashMap<>();
    private final ConcurrentMap<CourseIdentityKey, Long> activeByCourseIdentity = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, CourseIdentityKey> activeIdentityKeyByEnrollmentId = new ConcurrentHashMap<>();
    private final ConcurrentMap<UserCourseKey, Long> latestByUserCourse = new ConcurrentHashMap<>();

    @Override
    public void saveNew(Enrollment enrollment, String identityNumber) {
        UserCourseKey userCourseKey = new UserCourseKey(enrollment.getCourseId(), enrollment.getUserId());
        CourseIdentityKey identityKey = new CourseIdentityKey(enrollment.getCourseId(), identityNumber);

        if (enrollmentsById.putIfAbsent(enrollment.getEnrollmentId(), enrollment) != null) {
            throw new IllegalStateException("Duplicate enrollment ID: " + enrollment.getEnrollmentId());
        }

        Long priorUserEnrollment = activeByUserCourse.putIfAbsent(userCourseKey, enrollment.getEnrollmentId());
        if (priorUserEnrollment != null) {
            enrollmentsById.remove(enrollment.getEnrollmentId(), enrollment);
            throw new IllegalStateException("An active enrollment already exists for this user and course");
        }

        Long priorIdentityEnrollment = activeByCourseIdentity.putIfAbsent(identityKey, enrollment.getEnrollmentId());
        if (priorIdentityEnrollment != null) {
            activeByUserCourse.remove(userCourseKey, enrollment.getEnrollmentId());
            enrollmentsById.remove(enrollment.getEnrollmentId(), enrollment);
            throw new IllegalStateException("An active enrollment already exists for this identity and course");
        }

        activeIdentityKeyByEnrollmentId.put(enrollment.getEnrollmentId(), identityKey);
        latestByUserCourse.put(userCourseKey, enrollment.getEnrollmentId());
    }

    @Override
    public Optional<Enrollment> findById(long enrollmentId) {
        return Optional.ofNullable(enrollmentsById.get(enrollmentId));
    }

    @Override
    public Optional<Enrollment> findActiveByCourseAndUser(long courseId, long userId) {
        Long id = activeByUserCourse.get(new UserCourseKey(courseId, userId));
        return id == null ? Optional.empty() : findById(id);
    }

    @Override
    public Optional<Enrollment> findActiveByCourseAndIdentity(long courseId, String identityNumber) {
        Long id = activeByCourseIdentity.get(new CourseIdentityKey(courseId, identityNumber));
        return id == null ? Optional.empty() : findById(id);
    }

    @Override
    public Optional<Enrollment> findLatestByCourseAndUser(long courseId, long userId) {
        Long id = latestByUserCourse.get(new UserCourseKey(courseId, userId));
        return id == null ? Optional.empty() : findById(id);
    }

    @Override
    public void removeFromActiveIndexes(Enrollment enrollment) {
        UserCourseKey userCourseKey = new UserCourseKey(enrollment.getCourseId(), enrollment.getUserId());
        activeByUserCourse.remove(userCourseKey, enrollment.getEnrollmentId());
        CourseIdentityKey identityKey = activeIdentityKeyByEnrollmentId.remove(enrollment.getEnrollmentId());
        if (identityKey != null) {
            activeByCourseIdentity.remove(identityKey, enrollment.getEnrollmentId());
        }
    }

    @Override
    public List<Enrollment> findAllByCourseId(long courseId) {
        List<Enrollment> result = new ArrayList<>();
        for (Enrollment enrollment : enrollmentsById.values()) {
            if (enrollment.getCourseId() == courseId) {
                result.add(enrollment);
            }
        }
        return result;
    }

    private record UserCourseKey(long courseId, long userId) { }
    private record CourseIdentityKey(long courseId, String identityNumber) { }
}
