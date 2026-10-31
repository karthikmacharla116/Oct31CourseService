package com.courseenrollment.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Mutable registration state for one course. EnrollmentService synchronizes on this object
 * when performing a multi-step registration, withdrawal, or promotion operation.
 */
public final class CourseRegistrationState {
    private final long courseId;
    private final int totalCapacity;
    private final int waitlistLimit;
    private int availableSeats;
    private final Deque<Long> waitlistEnrollmentIds = new ArrayDeque<>();

    public CourseRegistrationState(long courseId, int totalCapacity, int waitlistLimit) {
        if (courseId <= 0 || totalCapacity <= 0 || waitlistLimit < 0) {
            throw new IllegalArgumentException("Invalid course registration configuration");
        }
        this.courseId = courseId;
        this.totalCapacity = totalCapacity;
        this.waitlistLimit = waitlistLimit;
        this.availableSeats = totalCapacity;
    }

    public long getCourseId() { return courseId; }
    public int getTotalCapacity() { return totalCapacity; }
    public int getWaitlistLimit() { return waitlistLimit; }

    public synchronized int getAvailableSeats() { return availableSeats; }
    public synchronized int getWaitlistSize() { return waitlistEnrollmentIds.size(); }
    public synchronized boolean hasWaitlistSpace() { return waitlistEnrollmentIds.size() < waitlistLimit; }

    public synchronized void allocateSeat() {
        if (availableSeats <= 0) {
            throw new IllegalStateException("No available seats for course " + courseId);
        }
        availableSeats--;
    }

    public synchronized void releaseSeat() {
        if (availableSeats >= totalCapacity) {
            throw new IllegalStateException("Cannot release more seats than course capacity");
        }
        availableSeats++;
    }

    public synchronized boolean addToWaitlist(long enrollmentId) {
        if (waitlistEnrollmentIds.size() >= waitlistLimit) {
            return false;
        }
        return waitlistEnrollmentIds.offerLast(enrollmentId);
    }

    public synchronized Long pollNextWaitlisted() {
        return waitlistEnrollmentIds.pollFirst();
    }

    public synchronized boolean removeFromWaitlist(long enrollmentId) {
        return waitlistEnrollmentIds.removeFirstOccurrence(enrollmentId);
    }

    public synchronized List<Long> getWaitlistSnapshot() {
        return new ArrayList<>(waitlistEnrollmentIds);
    }
}
