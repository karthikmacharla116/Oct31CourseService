package com.courseenrollment.service;

import com.courseenrollment.domain.Course;
import com.courseenrollment.domain.CourseRegistrationState;
import com.courseenrollment.domain.Enrollment;
import com.courseenrollment.domain.EnrollmentStatus;
import com.courseenrollment.domain.User;
import com.courseenrollment.dto.CourseAvailability;
import com.courseenrollment.dto.RegistrationOutcome;
import com.courseenrollment.dto.RegistrationResult;
import com.courseenrollment.dto.WaitlistEntry;
import com.courseenrollment.dto.WithdrawalOutcome;
import com.courseenrollment.dto.WithdrawalResult;
import com.courseenrollment.notification.NotificationService;
import com.courseenrollment.repository.CourseRegistrationStateRepository;
import com.courseenrollment.repository.CourseRepository;
import com.courseenrollment.repository.EnrollmentRepository;
import com.courseenrollment.repository.UserRepository;
import com.courseenrollment.validation.EligibilityValidator;
import com.courseenrollment.validation.UserInformationValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Coordinates registration, withdrawal, seat allocation, and FIFO promotion.
 * All capacity and queue mutations for a course are performed while holding that
 * course's CourseRegistrationState monitor. Different courses can progress independently.
 */
public final class EnrollmentService {
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseRegistrationStateRepository stateRepository;
    private final UserInformationValidator informationValidator;
    private final EligibilityValidator eligibilityValidator;
    private final NotificationService notificationService;
    private final AtomicLong enrollmentIdSequence;

    public EnrollmentService(
            UserRepository userRepository,
            CourseRepository courseRepository,
            EnrollmentRepository enrollmentRepository,
            CourseRegistrationStateRepository stateRepository,
            UserInformationValidator informationValidator,
            EligibilityValidator eligibilityValidator,
            NotificationService notificationService,
            long firstEnrollmentId) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.stateRepository = stateRepository;
        this.informationValidator = informationValidator;
        this.eligibilityValidator = eligibilityValidator;
        this.notificationService = notificationService;
        this.enrollmentIdSequence = new AtomicLong(firstEnrollmentId);
    }

    /**
     * Registers an eligible learner immediately when a seat is available; otherwise
     * appends the learner to this course's FIFO waitlist, subject to its configured limit.
     */
    public RegistrationResult register(long userId, long courseId) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            return new RegistrationResult(RegistrationOutcome.USER_NOT_FOUND, "User " + userId + " was not found", null, null);
        }
        Optional<Course> courseOptional = courseRepository.findById(courseId);
        if (courseOptional.isEmpty()) {
            return new RegistrationResult(RegistrationOutcome.COURSE_NOT_FOUND, "Course " + courseId + " was not found", null, null);
        }

        User user = userOptional.get();
        Course course = courseOptional.get();

        List<String> validationErrors = informationValidator.validate(user);
        if (!validationErrors.isEmpty()) {
            return new RegistrationResult(RegistrationOutcome.INVALID_USER_INFORMATION,
                    String.join("; ", validationErrors), null, null);
        }
        if (!eligibilityValidator.isEligible(user)) {
            return new RegistrationResult(RegistrationOutcome.NOT_QUALIFIED,
                    eligibilityValidator.rejectionReason(user), null, null);
        }

        CourseRegistrationState state = stateRepository.findByCourseId(courseId)
                .orElseThrow(() -> new IllegalStateException("Registration state missing for course " + courseId));

        RegistrationResult result;
        synchronized (state) {
            Optional<Enrollment> existingByUser = enrollmentRepository.findActiveByCourseAndUser(courseId, userId);
            if (existingByUser.isPresent()) {
                Enrollment existing = existingByUser.get();
                if (existing.getStatus() == EnrollmentStatus.ENROLLED) {
                    return new RegistrationResult(RegistrationOutcome.ALREADY_ENROLLED,
                            "User is already enrolled in this course", existing.getEnrollmentId(), null);
                }
                return new RegistrationResult(RegistrationOutcome.ALREADY_WAITLISTED,
                        "User is already on this course's waitlist", existing.getEnrollmentId(), null);
            }

            Optional<Enrollment> existingByIdentity = enrollmentRepository
                    .findActiveByCourseAndIdentity(courseId, user.identityNumber());
            if (existingByIdentity.isPresent()) {
                return new RegistrationResult(RegistrationOutcome.DUPLICATE_IDENTITY,
                        "This identity number already has an active registration for the course",
                        existingByIdentity.get().getEnrollmentId(), null);
            }

            if (state.getAvailableSeats() > 0) {
                Enrollment enrollment = newEnrollment(userId, courseId, EnrollmentStatus.ENROLLED);
                saveEnrollmentWithIdentity(enrollment, user.identityNumber());
                state.allocateSeat();
                result = new RegistrationResult(RegistrationOutcome.ENROLLED,
                        "Registration successful; seat allocated", enrollment.getEnrollmentId(), null);
            } else {
                if (!state.hasWaitlistSpace()) {
                    return new RegistrationResult(RegistrationOutcome.WAITLIST_FULL,
                            "Course is full and its waitlist limit has been reached", null, null);
                }
                Enrollment enrollment = newEnrollment(userId, courseId, EnrollmentStatus.WAITLISTED);
                boolean added = state.addToWaitlist(enrollment.getEnrollmentId());
                if (!added) {
                    return new RegistrationResult(RegistrationOutcome.WAITLIST_FULL,
                            "Course is full and its waitlist limit has been reached", null, null);
                }
                try {
                    saveEnrollmentWithIdentity(enrollment, user.identityNumber());
                } catch (RuntimeException exception) {
                    state.removeFromWaitlist(enrollment.getEnrollmentId());
                    throw exception;
                }
                result = new RegistrationResult(RegistrationOutcome.WAITLISTED,
                        "Course is full; learner added to the waitlist", enrollment.getEnrollmentId(), state.getWaitlistSize());
            }
        }

        if (result.outcome() == RegistrationOutcome.ENROLLED) {
            notificationService.enrollmentConfirmed(user, course);
        } else if (result.outcome() == RegistrationOutcome.WAITLISTED) {
            notificationService.addedToWaitlist(user, course, result.waitlistPosition());
        }
        return result;
    }

    /**
     * Withdraws an active enrolled or waitlisted learner. An enrolled withdrawal releases
     * one seat and promotes the earliest waitlisted learner before the course lock is released.
     */
    public WithdrawalResult withdraw(long userId, long courseId) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            return new WithdrawalResult(WithdrawalOutcome.USER_NOT_FOUND, "User " + userId + " was not found", null, List.of());
        }
        Optional<Course> courseOptional = courseRepository.findById(courseId);
        if (courseOptional.isEmpty()) {
            return new WithdrawalResult(WithdrawalOutcome.COURSE_NOT_FOUND, "Course " + courseId + " was not found", null, List.of());
        }

        User user = userOptional.get();
        Course course = courseOptional.get();
        CourseRegistrationState state = stateRepository.findByCourseId(courseId)
                .orElseThrow(() -> new IllegalStateException("Registration state missing for course " + courseId));

        List<User> promotedUsers = new ArrayList<>();
        Enrollment withdrawnEnrollment;
        synchronized (state) {
            Optional<Enrollment> activeOptional = enrollmentRepository.findActiveByCourseAndUser(courseId, userId);
            if (activeOptional.isEmpty()) {
                Optional<Enrollment> latest = enrollmentRepository.findLatestByCourseAndUser(courseId, userId);
                if (latest.isPresent() && latest.get().getStatus() == EnrollmentStatus.WITHDRAWN) {
                    return new WithdrawalResult(WithdrawalOutcome.ALREADY_WITHDRAWN,
                            "This learner has already withdrawn from the course", latest.get().getEnrollmentId(), List.of());
                }
                return new WithdrawalResult(WithdrawalOutcome.ENROLLMENT_NOT_FOUND,
                        "No active enrollment or waitlist entry was found for this learner and course", null, List.of());
            }

            withdrawnEnrollment = activeOptional.get();
            EnrollmentStatus previousStatus = withdrawnEnrollment.getStatus();
            if (previousStatus == EnrollmentStatus.WAITLISTED) {
                if (!state.removeFromWaitlist(withdrawnEnrollment.getEnrollmentId())) {
                    throw new IllegalStateException("Waitlisted enrollment was missing from its course queue");
                }
                withdrawnEnrollment.withdraw();
                enrollmentRepository.removeFromActiveIndexes(withdrawnEnrollment);
            } else if (previousStatus == EnrollmentStatus.ENROLLED) {
                withdrawnEnrollment.withdraw();
                enrollmentRepository.removeFromActiveIndexes(withdrawnEnrollment);
                state.releaseSeat();
                promoteWhileSeatsAvailable(state, promotedUsers);
            } else {
                return new WithdrawalResult(WithdrawalOutcome.ALREADY_WITHDRAWN,
                        "This learner has already withdrawn from the course", withdrawnEnrollment.getEnrollmentId(), List.of());
            }
        }

        notificationService.withdrawn(user, course);
        for (User promotedUser : promotedUsers) {
            notificationService.promotedFromWaitlist(promotedUser, course);
        }

        List<Long> promotedUserIds = new ArrayList<>();
        for (User promotedUser : promotedUsers) promotedUserIds.add(promotedUser.userId());
        String message = promotedUsers.isEmpty()
                ? "Withdrawal successful; no waitlisted learner was promoted"
                : "Withdrawal successful; promoted " + promotedUsers.size() + " waitlisted learner(s)";
        return new WithdrawalResult(WithdrawalOutcome.WITHDRAWN, message,
                withdrawnEnrollment.getEnrollmentId(), promotedUserIds);
    }

    /** Returns the latest attempt for a learner/course pair, including a withdrawn attempt. */
    public Optional<Enrollment> findLatestEnrollment(long userId, long courseId) {
        CourseRegistrationState state = stateRepository.findByCourseId(courseId).orElse(null);
        if (state == null) {
            return enrollmentRepository.findLatestByCourseAndUser(courseId, userId);
        }
        synchronized (state) {
            return enrollmentRepository.findLatestByCourseAndUser(courseId, userId);
        }
    }

    /** Returns a snapshot of capacity and waitlist counts for display and regression checks. */
    public CourseAvailability getCourseAvailability(long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course " + courseId + " was not found"));
        CourseRegistrationState state = stateRepository.findByCourseId(courseId)
                .orElseThrow(() -> new IllegalStateException("Registration state missing for course " + courseId));

        synchronized (state) {
            int enrolledCount = 0;
            for (Enrollment enrollment : enrollmentRepository.findAllByCourseId(courseId)) {
                if (enrollment.getStatus() == EnrollmentStatus.ENROLLED) enrolledCount++;
            }
            return new CourseAvailability(courseId, course.courseName(), course.capacity(), enrolledCount,
                    state.getAvailableSeats(), state.getWaitlistSize(), state.getWaitlistLimit());
        }
    }

    /** Returns waitlist members in their current FIFO order. */
    public List<WaitlistEntry> getWaitlist(long courseId) {
        CourseRegistrationState state = stateRepository.findByCourseId(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course registration state was not found"));
        synchronized (state) {
            List<WaitlistEntry> entries = new ArrayList<>();
            int position = 1;
            for (Long enrollmentId : state.getWaitlistSnapshot()) {
                Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                        .orElseThrow(() -> new IllegalStateException("Waitlist refers to missing enrollment " + enrollmentId));
                User user = userRepository.findById(enrollment.getUserId())
                        .orElseThrow(() -> new IllegalStateException("Waitlist refers to missing user " + enrollment.getUserId()));
                entries.add(new WaitlistEntry(position++, enrollmentId, user.userId(), user.username()));
            }
            return entries;
        }
    }

    private Enrollment newEnrollment(long userId, long courseId, EnrollmentStatus status) {
        return new Enrollment(enrollmentIdSequence.incrementAndGet(), userId, courseId, status);
    }

    private void saveEnrollmentWithIdentity(Enrollment enrollment, String identityNumber) {
        enrollmentRepository.saveNew(enrollment, identityNumber);
    }

    private void promoteWhileSeatsAvailable(CourseRegistrationState state, List<User> promotedUsers) {
        while (state.getAvailableSeats() > 0 && state.getWaitlistSize() > 0) {
            Long nextEnrollmentId = state.pollNextWaitlisted();
            if (nextEnrollmentId == null) return;

            Enrollment nextEnrollment = enrollmentRepository.findById(nextEnrollmentId)
                    .orElseThrow(() -> new IllegalStateException("Waitlist refers to missing enrollment " + nextEnrollmentId));
            if (nextEnrollment.getStatus() != EnrollmentStatus.WAITLISTED) {
                // Defensive cleanup; normal service operations cannot leave stale queue entries.
                continue;
            }
            User promotedUser = userRepository.findById(nextEnrollment.getUserId())
                    .orElseThrow(() -> new IllegalStateException("Waitlisted user was not found"));
            nextEnrollment.promote();
            state.allocateSeat();
            promotedUsers.add(promotedUser);
        }
    }
}
