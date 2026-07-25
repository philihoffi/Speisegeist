package com.philipphofmann.backend.controller;

import com.philipphofmann.backend.dto.AdminDtos.StatsResponse;
import com.philipphofmann.backend.dto.AdminDtos.UpdateRoleRequest;
import com.philipphofmann.backend.dto.AdminDtos.UserListItem;
import com.philipphofmann.backend.entity.User;
import com.philipphofmann.backend.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin endpoints for user management. All requests under this controller
 * are only accessible to users with the ADMIN role.
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/stats")
    public ResponseEntity<StatsResponse> getStats() {
        return ResponseEntity.ok(adminService.getStats());
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserListItem>> listUsers() {
        return ResponseEntity.ok(adminService.listUsers());
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        adminService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<UserListItem> updateRole(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRoleRequest request) {
        return ResponseEntity.ok(adminService.updateRole(id, request.role()));
    }
}