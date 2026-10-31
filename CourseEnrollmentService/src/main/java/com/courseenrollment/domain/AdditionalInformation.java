package com.courseenrollment.domain;

/** Optional learner profile information used by eligibility policies. */
public class AdditionalInformation {
    private Qualification highestQualification;

    public AdditionalInformation(Qualification highestQualification) {
        this.highestQualification = highestQualification;
    }
    public Qualification getHighestQualification() {
        return highestQualification;
    }

    public void setHighestQualification(Qualification highestQualification) {
        highestQualification = highestQualification;
    }
}
