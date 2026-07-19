package com.philipphofmann.backend.repository;

import com.philipphofmann.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Data access for application users.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    /** Finds a user by their unique email address. */
    Optional<User> findByEmail(String email);
}
