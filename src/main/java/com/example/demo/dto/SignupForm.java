package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Backs the sign-up form. Kept separate from AppUser so the raw password never reaches the entity. */
public class SignupForm {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be 3 to 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9._-]*$",
            message = "Username may only contain letters, numbers, dots, underscores and hyphens")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Please confirm your password")
    private String confirmPassword;

    public boolean passwordsMatch() {
        return password != null && password.equals(confirmPassword);
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
