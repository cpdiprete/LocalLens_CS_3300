package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.model.AppUser;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.DuplicateUsernameException;
import com.example.demo.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Test
    void registerStoresASaltedHashRatherThanThePlaintextPassword() {
        when(userRepository.existsByUsername("mapfan")).thenReturn(false);
        when(userRepository.save(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

        AppUser registered = userService.register("mapfan", "atlanta-2026");

        assertThat(registered.getPasswordHash()).isNotEqualTo("atlanta-2026");
        assertThat(registered.getPasswordHash()).startsWith("$2a$");
        assertThat(passwordEncoder.matches("atlanta-2026", registered.getPasswordHash())).isTrue();
    }

    @Test
    void registerGivesTwoUsersWithTheSamePasswordDifferentHashes() {
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(userRepository.save(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

        AppUser first = userService.register("ada", "same-password");
        AppUser second = userService.register("grace", "same-password");

        assertThat(first.getPasswordHash()).isNotEqualTo(second.getPasswordHash());
    }

    @Test
    void registerRejectsAUsernameThatIsAlreadyTaken() {
        when(userRepository.existsByUsername("mapfan")).thenReturn(true);

        assertThatThrownBy(() -> userService.register("mapfan", "atlanta-2026"))
                .isInstanceOf(DuplicateUsernameException.class);

        verify(userRepository, never()).save(any(AppUser.class));
    }

    @Test
    void registerTrimsSurroundingWhitespaceFromTheUsername() {
        when(userRepository.existsByUsername("mapfan")).thenReturn(false);
        when(userRepository.save(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

        AppUser registered = userService.register("  mapfan  ", "atlanta-2026");

        assertThat(registered.getUsername()).isEqualTo("mapfan");
    }
}
