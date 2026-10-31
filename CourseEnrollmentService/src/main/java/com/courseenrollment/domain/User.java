package com.courseenrollment.domain;

/** Immutable learner profile used during registration and eligibility checks. */
public record User(
        long userId,
        String username,
        String identityNumber,
        String emailAddress,
        String phoneNumber,
        AdditionalInformation additionalInformation) {

    public User {
        username = trimToNull(username);
        identityNumber = trimToNull(identityNumber);
        emailAddress = trimToNull(emailAddress);
        phoneNumber = trimToNull(phoneNumber);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
