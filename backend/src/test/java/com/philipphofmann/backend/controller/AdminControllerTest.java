package com.philipphofmann.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.philipphofmann.backend.dto.AdminDtos.OpenRouterKeyInfo;
import com.philipphofmann.backend.dto.AdminDtos.StatsResponse;
import com.philipphofmann.backend.dto.AdminDtos.UserListItem;
import com.philipphofmann.backend.entity.User;
import com.philipphofmann.backend.exception.AuthException;
import com.philipphofmann.backend.exception.GlobalExceptionHandler;
import com.philipphofmann.backend.service.AdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private AdminService adminService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminController(adminService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getStats_returns200WithCounts() throws Exception {
        OpenRouterKeyInfo keyInfo = new OpenRouterKeyInfo("key", 1.0, 10.0, false, 60, "1m", null);
        when(adminService.getStats()).thenReturn(new StatsResponse(5, 20, 80, 10, 5, keyInfo));

        mockMvc.perform(get("/admin/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userCount").value(5))
                .andExpect(jsonPath("$.recipeCount").value(20));
    }

    @Test
    void listUsers_returns200WithList() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        UserListItem item = new UserListItem(UUID.randomUUID(), "a@b.com", "USER", now, null);
        when(adminService.listUsers()).thenReturn(List.of(item));

        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("a@b.com"))
                .andExpect(jsonPath("$[0].role").value("USER"));
    }

    @Test
    void deleteUser_returns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/admin/users/" + id))
                .andExpect(status().isNoContent());

        verify(adminService).deleteUser(id);
    }

    @Test
    void deleteUser_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new AuthException("nicht gefunden")).when(adminService).deleteUser(id);

        mockMvc.perform(delete("/admin/users/" + id))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateRole_returns200WithUpdatedUser() throws Exception {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        UserListItem updated = new UserListItem(id, "a@b.com", "ADMIN", now, null);
        when(adminService.updateRole(any(), any())).thenReturn(updated);

        mockMvc.perform(put("/admin/users/" + id + "/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }
}
