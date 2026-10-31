package com.courseenrollment;

import com.courseenrollment.cli.CommandLineRunner;
import com.courseenrollment.notification.ConsoleNotificationService;
import com.courseenrollment.repository.inmemory.InMemoryCourseRegistrationStateRepository;
import com.courseenrollment.repository.inmemory.InMemoryCourseRepository;
import com.courseenrollment.repository.inmemory.InMemoryEnrollmentRepository;
import com.courseenrollment.repository.inmemory.InMemoryUserRepository;
import com.courseenrollment.service.EnrollmentService;
import com.courseenrollment.validation.EligibilityValidator;
import com.courseenrollment.validation.UserInformationValidator;

import java.util.Scanner;

/** Application composition root. */
public final class Main {
    private Main() { }

    public static void main(String[] args) {
        InMemoryUserRepository users = new InMemoryUserRepository();
        InMemoryCourseRepository courses = new InMemoryCourseRepository();
        InMemoryEnrollmentRepository enrollments = new InMemoryEnrollmentRepository();
        InMemoryCourseRegistrationStateRepository courseStates = new InMemoryCourseRegistrationStateRepository();

        AppDataInitializer.load(users, courses, courseStates);

        EnrollmentService enrollmentService = new EnrollmentService(
                users,
                courses,
                enrollments,
                courseStates,
                new UserInformationValidator(),
                new EligibilityValidator(),
                new ConsoleNotificationService(),
                10_000);

        new CommandLineRunner(new Scanner(System.in), users, courses, enrollmentService).run();
    }
}
