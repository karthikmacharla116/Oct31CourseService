package com.courseenrollment.validation;

import com.courseenrollment.domain.User;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Performs basic structural validation only; it does not verify external identity documents. */
public final class UserInformationValidator {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9]{10,13}$");


    public List<String> validate(User user) {
        List<String> errors = new ArrayList<>();
        if (user == null) {
            errors.add("User profile was not found");
            return errors;
        }
        if (user.userId() <= 0) errors.add("User ID must be positive");
        if (isBlank(user.username())) errors.add("Username is required");
        else if (user.username().length() > 40) errors.add("Username must be at most 40 characters");
        if (isBlank(user.identityNumber())) errors.add("Identity number is required");
        if (isBlank(user.emailAddress())) errors.add("Email address is required");
        else if (user.emailAddress().length() > 40 || !EMAIL_PATTERN.matcher(user.emailAddress()).matches()) {
            errors.add("Email address must be valid and at most 40 characters");
        }
        if (isBlank(user.phoneNumber()) || !PHONE_PATTERN.matcher(user.phoneNumber()).matches()) {
            errors.add("Phone number must contain 10 to 13 digits, with an optional leading +");
        }
        if (user.additionalInformation() == null || user.additionalInformation().highestQualification() == null) {
            errors.add("Highest qualification is required");
        }
        return errors;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
