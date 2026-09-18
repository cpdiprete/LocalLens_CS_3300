package com.example.demo.web;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LandingController {

    /** Public landing page. Signed-in users skip straight to their dashboard. */
    @GetMapping("/")
    public String landing(Authentication authentication) {
        return isSignedIn(authentication) ? "redirect:/home" : "landing";
    }

    @GetMapping("/login")
    public String login(Authentication authentication) {
        return isSignedIn(authentication) ? "redirect:/home" : "login";
    }

    /**
     * Placeholder for the signed-in experience. The Search / Explore / Filter
     * features drop in here once those are built.
     */
    @GetMapping("/home")
    public String home(Authentication authentication, Model model) {
        model.addAttribute("username", authentication.getName());
        return "home";
    }

    private boolean isSignedIn(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
