package service;

import com.courseenrollment.domain.AdditionalInformation;
import com.courseenrollment.domain.Course;
import com.courseenrollment.domain.Enrollment;
import com.courseenrollment.domain.EnrollmentStatus;
import com.courseenrollment.domain.Qualification;
import com.courseenrollment.domain.User;
import com.courseenrollment.dto.RegistrationOutcome;
import com.courseenrollment.dto.RegistrationResult;
import com.courseenrollment.dto.WithdrawalOutcome;
import com.courseenrollment.dto.WithdrawalResult;
import com.courseenrollment.notification.NotificationService;
import com.courseenrollment.repository.inmemory.InMemoryCourseRegistrationStateRepository;
import com.courseenrollment.repository.inmemory.InMemoryCourseRepository;
import com.courseenrollment.repository.inmemory.InMemoryEnrollmentRepository;
import com.courseenrollment.repository.inmemory.InMemoryUserRepository;
import com.courseenrollment.service.EnrollmentService;
import com.courseenrollment.validation.EligibilityValidator;
import com.courseenrollment.validation.UserInformationValidator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.*;

class EnrollmentServiceTest {

    @Test
    void enrollsEligibleLearnerWhenSeatIsAvailable() {
        Fixture fixture = new Fixture(2, 1000);

        RegistrationResult result = fixture.service.register(1, 101);

        assertEquals(RegistrationOutcome.ENROLLED, result.outcome());
        assertEquals(1, fixture.service.getCourseAvailability(101).availableSeats());
        assertEquals(1, fixture.service.getCourseAvailability(101).enrolledCount());
        assertEquals(EnrollmentStatus.ENROLLED,
                fixture.service.findLatestEnrollment(1, 101).orElseThrow().getStatus());
    }

    @Test
    void placesEligibleLearnerOnWaitlistWhenCourseIsFull() {
        Fixture fixture = new Fixture(1, 1000);
        fixture.service.register(1, 101);

        RegistrationResult result = fixture.service.register(2, 101);

        assertEquals(RegistrationOutcome.WAITLISTED, result.outcome());
        assertEquals(1, result.waitlistPosition().intValue());
        assertEquals(1, fixture.service.getCourseAvailability(101).waitlistCount());
        assertEquals(0, fixture.service.getCourseAvailability(101).availableSeats());
    }

    @Test
    void rejectsDuplicateUserRegistrationWithoutConsumingAnotherSeat() {
        Fixture fixture = new Fixture(2, 1000);
        fixture.service.register(1, 101);

        RegistrationResult duplicate = fixture.service.register(1, 101);

        assertEquals(RegistrationOutcome.ALREADY_ENROLLED, duplicate.outcome());
        assertEquals(1, fixture.service.getCourseAvailability(101).enrolledCount());
        assertEquals(1, fixture.service.getCourseAvailability(101).availableSeats());
    }

    @Test
    void rejectsDuplicateWaitlistRequestWithoutAddingAnotherQueueEntry() {
        Fixture fixture = new Fixture(1, 1000);
        fixture.service.register(1, 101);
        fixture.service.register(2, 101);

        RegistrationResult duplicate = fixture.service.register(2, 101);

        assertEquals(RegistrationOutcome.ALREADY_WAITLISTED, duplicate.outcome());
        assertEquals(1, fixture.service.getWaitlist(101).size());
        assertEquals(2L, fixture.service.getWaitlist(101).get(0).userId());
    }

    @Test
    void rejectsSameIdentityUnderDifferentUserIdForSameCourse() {
        Fixture fixture = new Fixture(2, 1000);
        fixture.users.save(user(2, "Other Name", "IDENTITY-1", "other@com", "+919876543212", Qualification.BACHELORS));
        fixture.service.register(1, 101);

        RegistrationResult result = fixture.service.register(2, 101);

        assertEquals(RegistrationOutcome.DUPLICATE_IDENTITY, result.outcome());
        assertEquals(1, fixture.service.getCourseAvailability(101).enrolledCount());
    }

    @Test
    void rejectsLearnerWithoutBachelorsQualificationAndDoesNotWaitlist() {
        Fixture fixture = new Fixture(1, 1000);
        fixture.users.save(user(3, "Diploma Learner", "IDENTITY-3", "nq@com", "+919876543213", Qualification.DIPLOMA));
        fixture.users.save(user(4, "Masters Learner", "IDENTITY-4", "masters@com", "+919876543214", Qualification.MASTERS));

        RegistrationResult diplomaResult = fixture.service.register(3, 101);
        RegistrationResult mastersResult = fixture.service.register(4, 101);

        assertEquals(RegistrationOutcome.NOT_QUALIFIED, diplomaResult.outcome());
        assertEquals(RegistrationOutcome.NOT_QUALIFIED, mastersResult.outcome());
        assertTrue(fixture.service.getWaitlist(101).isEmpty());
        assertEquals(1, fixture.service.getCourseAvailability(101).availableSeats());
        assertTrue(fixture.service.findLatestEnrollment(3, 101).isEmpty());
        assertTrue(fixture.service.findLatestEnrollment(4, 101).isEmpty());
    }

    @Test
    void rejectsInvalidContactInformation() {
        Fixture fixture = new Fixture(1, 1000);
        fixture.users.save(user(3, "Bad Contact", "IDENTITY-3", "not-an-email", "123", Qualification.BACHELORS));

        RegistrationResult result = fixture.service.register(3, 101);

        assertEquals(RegistrationOutcome.INVALID_USER_INFORMATION, result.outcome());
        assertTrue(result.message().contains("Email address"));
        assertTrue(result.message().contains("Phone number"));
    }

    @Test
    void rejectsRegistrationWhenWaitlistLimitHasBeenReached() {
        Fixture fixture = new Fixture(1, 1);
        fixture.service.register(1, 101);
        fixture.service.register(2, 101);

        RegistrationResult result = fixture.service.register(3, 101);

        assertEquals(RegistrationOutcome.WAITLIST_FULL, result.outcome());
        assertEquals(1, fixture.service.getCourseAvailability(101).waitlistCount());
        assertTrue(fixture.service.findLatestEnrollment(3, 101).isEmpty());
    }

    @Test
    void promotesWaitlistedLearnersInFifoOrderAfterWithdrawals() {
        Fixture fixture = new Fixture(1, 1000);
        fixture.service.register(1, 101); // enrolled
        fixture.service.register(2, 101); // first in waitlist
        fixture.service.register(3, 101); // second in waitlist

        WithdrawalResult firstDrop = fixture.service.withdraw(1, 101);
        assertEquals(WithdrawalOutcome.WITHDRAWN, firstDrop.outcome());
        assertEquals(List.of(2L), firstDrop.promotedUserIds());
        assertEquals(EnrollmentStatus.ENROLLED,
                fixture.service.findLatestEnrollment(2, 101).orElseThrow().getStatus());
        assertEquals(3L, fixture.service.getWaitlist(101).get(0).userId());
        assertCourseInvariant(fixture, 101);

        WithdrawalResult secondDrop = fixture.service.withdraw(2, 101);
        assertEquals(List.of(3L), secondDrop.promotedUserIds());
        assertEquals(EnrollmentStatus.ENROLLED,
                fixture.service.findLatestEnrollment(3, 101).orElseThrow().getStatus());
        assertTrue(fixture.service.getWaitlist(101).isEmpty());
        assertCourseInvariant(fixture, 101);
    }

    @Test
    void withdrawingWaitlistedLearnerRemovesThemWithoutReleasingASeat() {
        Fixture fixture = new Fixture(1, 1000);
        fixture.service.register(1, 101);
        fixture.service.register(2, 101);
        fixture.service.register(3, 101);

        WithdrawalResult result = fixture.service.withdraw(2, 101);

        assertEquals(WithdrawalOutcome.WITHDRAWN, result.outcome());
        assertTrue(result.promotedUserIds().isEmpty());
        assertEquals(1, fixture.service.getWaitlist(101).size());
        assertEquals(3L, fixture.service.getWaitlist(101).get(0).userId());
        assertEquals(0, fixture.service.getCourseAvailability(101).availableSeats());
        assertEquals(1, fixture.service.getCourseAvailability(101).enrolledCount());
    }

    @Test
    void rejectsRepeatedWithdrawalWithAlreadyWithdrawnOutcome() {
        Fixture fixture = new Fixture(1, 1000);
        fixture.service.register(1, 101);
        assertEquals(WithdrawalOutcome.WITHDRAWN, fixture.service.withdraw(1, 101).outcome());

        WithdrawalResult repeated = fixture.service.withdraw(1, 101);

        assertEquals(WithdrawalOutcome.ALREADY_WITHDRAWN, repeated.outcome());
        assertEquals(1, fixture.service.getCourseAvailability(101).availableSeats());
    }

    @Test
    void returnsNotFoundForInvalidWithdrawalRequest() {
        Fixture fixture = new Fixture(1, 1000);

        assertEquals(WithdrawalOutcome.ENROLLMENT_NOT_FOUND, fixture.service.withdraw(1, 101).outcome());
        assertEquals(WithdrawalOutcome.USER_NOT_FOUND, fixture.service.withdraw(999, 101).outcome());
        assertEquals(WithdrawalOutcome.COURSE_NOT_FOUND, fixture.service.withdraw(1, 999).outcome());
    }

    @Test
    void allowsReregistrationAfterWithdrawalAndPreservesOldAttempt() {
        Fixture fixture = new Fixture(1, 1000);
        RegistrationResult first = fixture.service.register(1, 101);
        fixture.service.withdraw(1, 101);

        RegistrationResult second = fixture.service.register(1, 101);

        assertEquals(RegistrationOutcome.ENROLLED, second.outcome());
        assertNotEquals(first.enrollmentId(), second.enrollmentId());
        assertEquals(EnrollmentStatus.WITHDRAWN,
                fixture.enrollments.findById(first.enrollmentId()).orElseThrow().getStatus());
        assertEquals(EnrollmentStatus.ENROLLED,
                fixture.service.findLatestEnrollment(1, 101).orElseThrow().getStatus());
    }

    @Test
    void sameLearnerCanEnrollInDifferentCourses() {
        Fixture fixture = new Fixture(1, 1000);
        fixture.courses.save(new Course(102, "Second Course", new BigDecimal("200.00"), 1));
        fixture.states.createForCourse(102, 1, 1000);

        RegistrationResult first = fixture.service.register(1, 101);
        RegistrationResult second = fixture.service.register(1, 102);

        assertEquals(RegistrationOutcome.ENROLLED, first.outcome());
        assertEquals(RegistrationOutcome.ENROLLED, second.outcome());
        assertEquals(1, fixture.service.getCourseAvailability(101).enrolledCount());
        assertEquals(1, fixture.service.getCourseAvailability(102).enrolledCount());
    }

    @Test
    void returnsNotFoundOutcomesForInvalidRegistrationRequest() {
        Fixture fixture = new Fixture(1, 1000);

        assertEquals(RegistrationOutcome.USER_NOT_FOUND, fixture.service.register(999, 101).outcome());
        assertEquals(RegistrationOutcome.COURSE_NOT_FOUND, fixture.service.register(1, 999).outcome());
    }

    @Test
    void serializesConcurrentRegistrationsForTheLastSeat() throws Exception {
        Fixture fixture = new Fixture(1, 1000);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<RegistrationResult> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return fixture.service.register(1, 101);
            });
            Future<RegistrationResult> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return fixture.service.register(2, 101);
            });
            ready.await();
            start.countDown();

            List<RegistrationOutcome> outcomes = List.of(first.get().outcome(), second.get().outcome());
            assertEquals(1, outcomes.stream().filter(value -> value == RegistrationOutcome.ENROLLED).count());
            assertEquals(1, outcomes.stream().filter(value -> value == RegistrationOutcome.WAITLISTED).count());
            assertCourseInvariant(fixture, 101);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void assertCourseInvariant(Fixture fixture, long courseId) {
        var availability = fixture.service.getCourseAvailability(courseId);
        assertTrue(availability.availableSeats() >= 0);
        assertTrue(availability.availableSeats() <= availability.totalCapacity());
        assertEquals(availability.totalCapacity(), availability.enrolledCount() + availability.availableSeats());
    }

    private static User user(long id, String name, String identity, String email, String phone, Qualification qualification) {
        return new User(id, name, identity, email, phone, new AdditionalInformation(qualification));
    }

    private static final class Fixture {
        private final InMemoryUserRepository users = new InMemoryUserRepository();
        private final InMemoryCourseRepository courses = new InMemoryCourseRepository();
        private final InMemoryEnrollmentRepository enrollments = new InMemoryEnrollmentRepository();
        private final InMemoryCourseRegistrationStateRepository states = new InMemoryCourseRegistrationStateRepository();
        private final EnrollmentService service;

        private Fixture(int capacity, int waitlistLimit) {
            users.save(user(1, "Learner One", "IDENTITY-1", "one@com", "+919876543211", Qualification.BACHELORS));
            users.save(user(2, "Learner Two", "IDENTITY-2", "two@com", "+919876543212", Qualification.BACHELORS));
            users.save(user(3, "Learner Three", "IDENTITY-3", "three@com", "+919876543213", Qualification.BACHELORS));
            courses.save(new Course(101, "Test Course", new BigDecimal("100.00"), capacity));
            states.createForCourse(101, capacity, waitlistLimit);
            service = new EnrollmentService(users, courses, enrollments, states,
                    new UserInformationValidator(), new EligibilityValidator(), NoOpNotificationService.INSTANCE, 50_000);
        }
    }

    private enum NoOpNotificationService implements NotificationService {
        INSTANCE;
        @Override public void enrollmentConfirmed(User user, Course course) { }
        @Override public void addedToWaitlist(User user, Course course, int position) { }
        @Override public void withdrawn(User user, Course course) { }
        @Override public void promotedFromWaitlist(User user, Course course) { }
    }
}
