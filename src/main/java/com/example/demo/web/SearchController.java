package com.example.demo.web;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.demo.dto.LocationQuery;
import com.example.demo.service.PlacesService;

@Controller
public class SearchController {
    private final PlacesService placesService;

    public SearchController(PlacesService placesService) {
        this.placesService = placesService;
    }

    @GetMapping("/search")
    public String search(
            @RequestParam(defaultValue = "") String location,
            @RequestParam(defaultValue = "") String latitude,
            @RequestParam(defaultValue = "") String longitude,
            @RequestParam(defaultValue = "") String radius,
            Authentication authentication, Model model) {
        model.addAttribute("username", authentication.getName());
        model.addAttribute("location", location);
        model.addAttribute("latitude", latitude);
        model.addAttribute("longitude", longitude);
        model.addAttribute("radius", radius);
        try {
            LocationQuery query = LocationQuery.parse(location, latitude, longitude, radius);
            model.addAttribute("result", placesService.search(query));
        } catch (IllegalArgumentException | PlacesService.SearchException ex) {
            model.addAttribute("searchError", ex.getMessage());
        }
        return "home";
    }
}