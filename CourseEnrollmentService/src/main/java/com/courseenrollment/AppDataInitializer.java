package com.courseenrollment;

import com.courseenrollment.domain.AdditionalInformation;
import com.courseenrollment.domain.Course;
import com.courseenrollment.domain.Qualification;
import com.courseenrollment.domain.User;
import com.courseenrollment.repository.CourseRegistrationStateRepository;
import com.courseenrollment.repository.CourseRepository;
import com.courseenrollment.repository.UserRepository;

import java.math.BigDecimal;

/** Loads deterministic sample users and courses for the CLI demonstration. */
public final class AppDataInitializer {
    public static final int DEFAULT_WAITLIST_LIMIT = 1_000;

    private AppDataInitializer() { }

    public static void load(UserRepository users, CourseRepository courses,
                            CourseRegistrationStateRepository states) {
        users.save(new User(1001, "Alice", "ID-A1001", "alice@example.com", "+919876540001",
                new AdditionalInformation(Qualification.BACHELORS)));
        users.save(new User(1002, "Bob", "ID-B1002", "bob@example.com", "+919876540002",
                new AdditionalInformation(Qualification.BACHELORS)));
        users.save(new User(1003, "Charlie", "ID-C1003", "charlie@example.com", "+919876540003",
                new AdditionalInformation(Qualification.DIPLOMA)));
        users.save(new User(1004, "Diana", "ID-D1004", "diana@example.com", "+919876540004",
                new AdditionalInformation(Qualification.BACHELORS)));
        users.save(new User(1005, "Ethan", "ID-E1005", "ethan@example.com", "+919876540005",
                new AdditionalInformation(Qualification.MASTERS)));
        users.save(new User(1006, "Farah", "ID-F1006", "farah@example.com", "+919876540006",
                new AdditionalInformation(Qualification.BACHELORS)));

        addCourse(courses, states, new Course(101, "Java Concurrency", new BigDecimal("2499.00"), 2));
        addCourse(courses, states, new Course(102, "Data Structures", new BigDecimal("1999.00"), 3));
        addCourse(courses, states, new Course(103, "System Design Fundamentals", new BigDecimal("2999.00"), 1));
    }

    private static void addCourse(CourseRepository courses, CourseRegistrationStateRepository states, Course course) {
        courses.save(course);
        states.createForCourse(course.courseId(), course.capacity(), DEFAULT_WAITLIST_LIMIT);
    }
}
