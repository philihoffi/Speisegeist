package com.philipphofmann.backend.service;

import com.philipphofmann.backend.dto.AdminDtos.OpenRouterKeyInfo;
import com.philipphofmann.backend.dto.AdminDtos.StatsResponse;
import com.philipphofmann.backend.dto.AdminDtos.UserListItem;
import com.philipphofmann.backend.entity.User;
import com.philipphofmann.backend.exception.AuthException;
import com.philipphofmann.backend.repository.IngredientImageRepository;
import com.philipphofmann.backend.repository.IngredientRepository;
import com.philipphofmann.backend.repository.RecipeImageRepository;
import com.philipphofmann.backend.repository.RecipeRepository;
import com.philipphofmann.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RecipeRepository recipeRepository;
    @Mock private IngredientRepository ingredientRepository;
    @Mock private IngredientImageRepository ingredientImageRepository;
    @Mock private RecipeImageRepository recipeImageRepository;
    @Mock private OpenRouterService openRouterService;

    @InjectMocks
    private AdminService adminService;

    @Test
    void getStats_returnsCounts() {
        when(userRepository.count()).thenReturn(3L);
        when(recipeRepository.count()).thenReturn(12L);
        when(ingredientRepository.count()).thenReturn(50L);
        when(ingredientImageRepository.count()).thenReturn(8L);
        when(recipeImageRepository.count()).thenReturn(4L);
        when(openRouterService.getKeyInfo()).thenReturn(new OpenRouterKeyInfo("test", 1.0, 5.0, false, 100, "1m", null));

        StatsResponse stats = adminService.getStats();

        assertThat(stats.userCount()).isEqualTo(3L);
        assertThat(stats.recipeCount()).isEqualTo(12L);
        assertThat(stats.ingredientCount()).isEqualTo(50L);
    }

    @Test
    void listUsers_mapsAllFields() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        User user = User.builder()
                .id(id)
                .email("test@example.com")
                .passwordHash("hash")
                .role(User.Role.USER)
                .createdAt(now)
                .lastLogin(now)
                .build();
        when(userRepository.findAll()).thenReturn(List.of(user));

        List<UserListItem> result = adminService.listUsers();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).email()).isEqualTo("test@example.com");
        assertThat(result.get(0).role()).isEqualTo("USER");
        assertThat(result.get(0).id()).isEqualTo(id);
    }

    @Test
    void deleteUser_deletesWhenFound() {
        UUID id = UUID.randomUUID();
        User user = User.builder().id(id).email("a@b.com").passwordHash("h").build();
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        adminService.deleteUser(id);

        verify(userRepository).delete(user);
    }

    @Test
    void deleteUser_throwsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.deleteUser(id))
                .isInstanceOf(AuthException.class);

        verify(userRepository, never()).delete(any());
    }

    @Test
    void updateRole_changesRoleAndReturnsUpdated() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        User user = User.builder()
                .id(id)
                .email("a@b.com")
                .passwordHash("h")
                .role(User.Role.USER)
                .createdAt(now)
                .build();
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserListItem result = adminService.updateRole(id, User.Role.ADMIN);

        assertThat(result.role()).isEqualTo("ADMIN");
        verify(userRepository).save(user);
    }

    @Test
    void updateRole_throwsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.updateRole(id, User.Role.ADMIN))
                .isInstanceOf(AuthException.class);
    }
}
