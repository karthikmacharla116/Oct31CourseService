package com.courseenrollment.validation;

import com.courseenrollment.domain.Qualification;
import com.courseenrollment.domain.User;

/** Current business policy: a learner must have exactly a BACHELORS qualification. */
public final class EligibilityValidator {
    public boolean isEligible(User user) {
        return user != null
                && user.additionalInformation() != null
                && user.additionalInformation().highestQualification() == Qualification.BACHELORS;
    }

    public String rejectionReason(User user) {
        if (user == null || user.additionalInformation() == null
                || user.additionalInformation().highestQualification() == null) {
            return "Highest qualification is missing";
        }
        return "Required qualification: BACHELORS; supplied qualification: "
                + user.additionalInformation().highestQualification();
    }
}
