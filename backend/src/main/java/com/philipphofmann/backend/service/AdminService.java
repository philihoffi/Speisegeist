package com.philipphofmann.backend.service;

import com.philipphofmann.backend.dto.AdminDtos.UserListItem;
import com.philipphofmann.backend.entity.User;
import com.philipphofmann.backend.exception.AuthException;
import com.philipphofmann.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Administrative operations: listing users, deleting users, and changing roles.
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<UserListItem> listUsers() {
        return userRepository.findAll().stream()
                .map(u -> new UserListItem(u.getId(), u.getEmail(), u.getRole().name(),
                        u.getCreatedAt(), u.getLastLogin()))
                .toList();
    }

    @Transactional
    public void deleteUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("Benutzer nicht gefunden"));
        userRepository.delete(user);
    }

    @Transactional
    public UserListItem updateRole(UUID userId, User.Role role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("Benutzer nicht gefunden"));
        user.setRole(role);
        user = userRepository.save(user);
        return new UserListItem(user.getId(), user.getEmail(), user.getRole().name(),
                user.getCreatedAt(), user.getLastLogin());
    }
}