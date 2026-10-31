package com.courseenrollment.notification;

import com.courseenrollment.domain.Course;
import com.courseenrollment.domain.User;

/** Console-backed stand-in for a future email or messaging notification adapter. */
public final class ConsoleNotificationService implements NotificationService {
    @Override
    public void enrollmentConfirmed(User user, Course course) {
        System.out.printf("[NOTIFICATION] Enrollment confirmed for %s in %s.%n", user.username(), course.courseName());
    }

    @Override
    public void addedToWaitlist(User user, Course course, int position) {
        System.out.printf("[NOTIFICATION] %s added to the waitlist for %s at position %d.%n",
                user.username(), course.courseName(), position);
    }

    @Override
    public void withdrawn(User user, Course course) {
        System.out.printf("[NOTIFICATION] %s withdrawn from %s.%n", user.username(), course.courseName());
    }

    @Override
    public void promotedFromWaitlist(User user, Course course) {
        System.out.printf("[NOTIFICATION] %s has been promoted from the waitlist into %s.%n",
                user.username(), course.courseName());
    }
}
