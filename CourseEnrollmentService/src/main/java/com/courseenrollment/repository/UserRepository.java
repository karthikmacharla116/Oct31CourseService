package com.courseenrollment.repository;

import com.courseenrollment.domain.User;

import java.util.List;
import java.util.Optional;

/** Storage contract for learner profiles. */
public interface UserRepository {
    Optional<User> findById(long userId);
    void save(User user);
    List<User> findAll();
}
