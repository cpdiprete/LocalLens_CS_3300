package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.demo.model.AppUser;
import com.example.demo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** End-to-end cover for the landing page and the sign-up / log-in / log-out flow. */
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clearUsers() {
        userRepository.deleteAll();
    }

    @Test
    void landingPageIsReachableWithoutLoggingIn() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("landing"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("LocalLens")));
    }

    @Test
    void loginAndSignupPagesAreReachableWithoutLoggingIn() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isOk()).andExpect(view().name("login"));
        mockMvc.perform(get("/signup")).andExpect(status().isOk()).andExpect(view().name("signup"));
    }

    @Test
    void homeRedirectsAnonymousVisitorsToLogin() throws Exception {
        mockMvc.perform(get("/home"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void signupCreatesAnAccountAndSendsTheUserToLogin() throws Exception {
        mockMvc.perform(post("/signup")
                        .with(csrf())
                        .param("username", "mapfan")
                        .param("password", "atlanta-2026")
                        .param("confirmPassword", "atlanta-2026"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        AppUser created = userRepository.findByUsername("mapfan").orElseThrow();
        assertThat(created.getPasswordHash()).isNotEqualTo("atlanta-2026");
        assertThat(passwordEncoder.matches("atlanta-2026", created.getPasswordHash())).isTrue();
    }

    @Test
    void signupRejectsMismatchedPasswords() throws Exception {
        mockMvc.perform(post("/signup")
                        .with(csrf())
                        .param("username", "mapfan")
                        .param("password", "atlanta-2026")
                        .param("confirmPassword", "atlanta-2027"))
                .andExpect(status().isOk())
                .andExpect(view().name("signup"))
                .andExpect(model().attributeHasFieldErrors("signupForm", "confirmPassword"));

        assertThat(userRepository.findByUsername("mapfan")).isEmpty();
    }

    @Test
    void signupRejectsAShortPassword() throws Exception {
        mockMvc.perform(post("/signup")
                        .with(csrf())
                        .param("username", "mapfan")
                        .param("password", "short")
                        .param("confirmPassword", "short"))
                .andExpect(status().isOk())
                .andExpect(view().name("signup"))
                .andExpect(model().attributeHasFieldErrors("signupForm", "password"));

        assertThat(userRepository.findByUsername("mapfan")).isEmpty();
    }

    @Test
    void signupRejectsADuplicateUsername() throws Exception {
        registerMapfan();

        mockMvc.perform(post("/signup")
                        .with(csrf())
                        .param("username", "mapfan")
                        .param("password", "another-password")
                        .param("confirmPassword", "another-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("signup"))
                .andExpect(model().attributeHasFieldErrors("signupForm", "username"));

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void loginSucceedsWithTheCorrectPassword() throws Exception {
        registerMapfan();

        mockMvc.perform(formLogin("/login").user("mapfan").password("atlanta-2026"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/home"))
                .andExpect(authenticated().withUsername("mapfan"));
    }

    @Test
    void loginFailsWithAWrongPassword() throws Exception {
        registerMapfan();

        mockMvc.perform(formLogin("/login").user("mapfan").password("not-the-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void loginFailsForAnUnknownUser() throws Exception {
        mockMvc.perform(formLogin("/login").user("nobody").password("atlanta-2026"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        registerMapfan();

        mockMvc.perform(logout())
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?loggedOut"))
                .andExpect(unauthenticated());
    }

    private void registerMapfan() throws Exception {
        mockMvc.perform(post("/signup")
                .with(csrf())
                .param("username", "mapfan")
                .param("password", "atlanta-2026")
                .param("confirmPassword", "atlanta-2026"));
    }
}
