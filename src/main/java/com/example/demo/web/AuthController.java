package com.example.demo.web;

import com.example.demo.dto.SignupForm;
import com.example.demo.service.DuplicateUsernameException;
import com.example.demo.service.UserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/signup")
    public String showSignupForm(Model model) {
        model.addAttribute("signupForm", new SignupForm());
        return "signup";
    }

    @PostMapping("/signup")
    public String register(@Valid @ModelAttribute("signupForm") SignupForm signupForm,
            BindingResult bindingResult) {
        if (!signupForm.passwordsMatch()) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch",
                    "Passwords do not match");
        }
        if (bindingResult.hasErrors()) {
            return "signup";
        }
        try {
            userService.register(signupForm.getUsername(), signupForm.getPassword());
        } catch (DuplicateUsernameException ex) {
            bindingResult.rejectValue("username", "username.taken",
                    "That username is already taken");
            return "signup";
        }
        return "redirect:/login?registered";
    }
}
