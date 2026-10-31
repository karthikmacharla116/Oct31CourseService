package com.courseenrollment.notification;

import com.courseenrollment.domain.Course;
import com.courseenrollment.domain.User;

public interface NotificationService {
    void enrollmentConfirmed(User user, Course course);
    void addedToWaitlist(User user, Course course, int position);
    void withdrawn(User user, Course course);
    void promotedFromWaitlist(User user, Course course);
}
