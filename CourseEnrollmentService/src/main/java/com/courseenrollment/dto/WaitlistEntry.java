package com.courseenrollment.dto;

public record WaitlistEntry(int position, long enrollmentId, long userId, String username) {
}
