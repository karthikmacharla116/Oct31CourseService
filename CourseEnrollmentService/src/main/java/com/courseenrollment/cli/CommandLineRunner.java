package com.courseenrollment.cli;

import com.courseenrollment.domain.Course;
import com.courseenrollment.domain.Enrollment;
import com.courseenrollment.domain.User;
import com.courseenrollment.dto.CourseAvailability;
import com.courseenrollment.dto.RegistrationResult;
import com.courseenrollment.dto.WaitlistEntry;
import com.courseenrollment.dto.WithdrawalResult;
import com.courseenrollment.repository.CourseRepository;
import com.courseenrollment.repository.UserRepository;
import com.courseenrollment.service.EnrollmentService;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Scanner;

/** Interactive CLI adapter. It contains input/output only; business rules live in EnrollmentService. */
public final class CommandLineRunner {
    private final Scanner scanner;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentService enrollmentService;

    public CommandLineRunner(Scanner scanner, UserRepository userRepository,
                             CourseRepository courseRepository, EnrollmentService enrollmentService) {
        this.scanner = scanner;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.enrollmentService = enrollmentService;
    }

    public void run() {
        printWelcome();
        boolean running = true;
        while (running) {
            printMenu();
            if (!scanner.hasNextLine()) break;
            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1" -> listUsers();
                case "2" -> listCourses();
                case "3" -> running = register();
                case "4" -> running = withdraw();
                case "5" -> running = viewEnrollment();
                case "6" -> running = viewAvailability();
                case "7" -> running = viewWaitlist();
                case "0" -> running = false;
                default -> System.out.println("Invalid choice. Please select an option from 0 to 7.");
            }
        }
        System.out.println("Exiting Course Enrollment System. Goodbye!");
    }

    private void printWelcome() {
        System.out.println("\n============================================");
        System.out.println("          COURSE ENROLLMENT SYSTEM");
        System.out.println("============================================");
        System.out.println("Sample users and courses are preloaded. Select '1' and '2' to view their IDs.");
    }

    private void printMenu() {
        System.out.println("\n--------------- MENU ----------------");
        System.out.println("1. List users");
        System.out.println("2. List courses");
        System.out.println("3. Register for a course");
        System.out.println("4. Withdraw from a course");
        System.out.println("5. View latest enrollment status");
        System.out.println("6. View course availability");
        System.out.println("7. View course waitlist");
        System.out.println("0. Exit");
        System.out.print("Enter choice: ");
    }

    private void listUsers() {
        System.out.println("\nUsers:");
        userRepository.findAll().stream().sorted(Comparator.comparingLong(User::userId)).forEach(user ->
                System.out.printf("  ID: %d | Name: %s | Qualification: %s | Identity: %s%n",
                        user.userId(), user.username(), user.additionalInformation().highestQualification(), user.identityNumber()));
    }

    private void listCourses() {
        System.out.println("\nCourses:");
        courseRepository.findAll().stream().sorted(Comparator.comparingLong(Course::courseId)).forEach(course -> {
            CourseAvailability availability = enrollmentService.getCourseAvailability(course.courseId());
            System.out.printf("  ID: %d | %s | Price: %s | Capacity: %d | Available: %d%n",
                    course.courseId(), course.courseName(), course.price(), course.capacity(), availability.availableSeats());
        });
    }

    private boolean register() {
        OptionalLong userId = readLong("Enter User ID: ");
        if (userId.isEmpty()) return false;
        OptionalLong courseId = readLong("Enter Course ID: ");
        if (courseId.isEmpty()) return false;
        System.out.println("\nValidating learner information and qualification...");
        RegistrationResult result = enrollmentService.register(userId.getAsLong(), courseId.getAsLong());
        System.out.printf("Registration result: %s%nDetails: %s%n", result.outcome(), result.message());
        if (result.enrollmentId() != null) System.out.println("Enrollment ID: " + result.enrollmentId());
        if (result.waitlistPosition() != null) System.out.println("Waitlist position: " + result.waitlistPosition());
        return true;
    }

    private boolean withdraw() {
        OptionalLong userId = readLong("Enter User ID: ");
        if (userId.isEmpty()) return false;
        OptionalLong courseId = readLong("Enter Course ID: ");
        if (courseId.isEmpty()) return false;
        System.out.println("\nValidating enrollment and processing withdrawal...");
        WithdrawalResult result = enrollmentService.withdraw(userId.getAsLong(), courseId.getAsLong());
        System.out.printf("Withdrawal result: %s%nDetails: %s%n", result.outcome(), result.message());
        if (result.enrollmentId() != null) System.out.println("Enrollment ID: " + result.enrollmentId());
        if (!result.promotedUserIds().isEmpty()) System.out.println("Promoted user IDs: " + result.promotedUserIds());
        return true;
    }

    private boolean viewEnrollment() {
        OptionalLong userId = readLong("Enter User ID: ");
        if (userId.isEmpty()) return false;
        OptionalLong courseId = readLong("Enter Course ID: ");
        if (courseId.isEmpty()) return false;
        Optional<Enrollment> enrollment = enrollmentService.findLatestEnrollment(userId.getAsLong(), courseId.getAsLong());
        if (enrollment.isEmpty()) {
            System.out.println("No enrollment history found for this learner and course.");
        } else {
            System.out.printf("Latest enrollment ID: %d | Status: %s | Created: %s%n",
                    enrollment.get().getEnrollmentId(), enrollment.get().getStatus(), enrollment.get().getCreatedAt());
        }
        return true;
    }

    private boolean viewAvailability() {
        OptionalLong courseId = readLong("Enter Course ID: ");
        if (courseId.isEmpty()) return false;
        try {
            CourseAvailability availability = enrollmentService.getCourseAvailability(courseId.getAsLong());
            System.out.printf("%s | Capacity: %d | Enrolled: %d | Available: %d | Waitlist: %d/%d%n",
                    availability.courseName(), availability.totalCapacity(), availability.enrolledCount(),
                    availability.availableSeats(), availability.waitlistCount(), availability.waitlistLimit());
        } catch (IllegalArgumentException exception) {
            System.out.println(exception.getMessage());
        }
        return true;
    }

    private boolean viewWaitlist() {
        OptionalLong courseId = readLong("Enter Course ID: ");
        if (courseId.isEmpty()) return false;
        try {
            List<WaitlistEntry> entries = enrollmentService.getWaitlist(courseId.getAsLong());
            if (entries.isEmpty()) {
                System.out.println("The waitlist is empty.");
            } else {
                System.out.println("Waitlist (FIFO order):");
                for (WaitlistEntry entry : entries) {
                    System.out.printf("  %d. %s (User ID: %d, Enrollment ID: %d)%n",
                            entry.position(), entry.username(), entry.userId(), entry.enrollmentId());
                }
            }
        } catch (IllegalArgumentException exception) {
            System.out.println(exception.getMessage());
        }
        return true;
    }

    private OptionalLong readLong(String prompt) {
        while (true) {
            System.out.print(prompt);
            if (!scanner.hasNextLine()) return OptionalLong.empty();
            String value = scanner.nextLine().trim();
            try {
                return OptionalLong.of(Long.parseLong(value));
            } catch (NumberFormatException exception) {
                System.out.println("Please enter a valid whole number.");
            }
        }
    }
}
