package com.courseenrollment.repository.inmemory;

import com.courseenrollment.domain.User;
import com.courseenrollment.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** In-memory user store. ConcurrentHashMap supports safe independent reads and writes. */
public final class InMemoryUserRepository implements UserRepository {
    private final ConcurrentMap<Long, User> users = new ConcurrentHashMap<>();

    @Override
    public Optional<User> findById(long userId) {
        return Optional.ofNullable(users.get(userId));
    }

    @Override
    public void save(User user) {
        users.put(user.userId(), user);
    }

    @Override
    public List<User> findAll() {
        return new ArrayList<>(users.values());
    }
}
