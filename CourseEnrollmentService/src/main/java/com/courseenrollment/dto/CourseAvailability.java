package com.courseenrollment.dto;

public record CourseAvailability(
        long courseId,
        String courseName,
        int totalCapacity,
        int enrolledCount,
        int availableSeats,
        int waitlistCount,
        int waitlistLimit) {
}
