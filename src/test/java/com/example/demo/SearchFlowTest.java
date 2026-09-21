package com.example.demo;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.example.demo.dto.LocationQuery;
import com.example.demo.service.PlacesService;

@SpringBootTest
class SearchFlowTest {
    @Autowired private WebApplicationContext context;
    @MockitoBean private PlacesService placesService;
    private MockMvc mvc;

    // Spring Boot 4 does not auto-apply Spring Security's MockMvc integration, so
    // @WithMockUser is ignored and every authenticated request 302s to /login.
    // Building MockMvc with springSecurity() wires that integration in explicitly.
    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void anonymousSearchRequiresLogin() throws Exception {
        mvc.perform(get("/search").param("latitude", "0").param("longitude", "0").param("radius", "1000"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        verifyNoInteractions(placesService);
    }

    @Test
    @WithMockUser(username = "youso")
    void homeRendersAnEmptySearchForm() throws Exception {
        mvc.perform(get("/home"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Search nearby")));
        verifyNoInteractions(placesService);
    }

    @Test
    @WithMockUser(username = "youso")
    void invalidInputShowsAnErrorWithoutApiUse() throws Exception {
        mvc.perform(get("/search").param("latitude", "91").param("longitude", "0").param("radius", "1000"))
                .andExpect(status().isOk()).andExpect(model().attributeExists("searchError"))
                .andExpect(content().string(containsString("Latitude must be")));
        verifyNoInteractions(placesService);
    }

    @Test
    @WithMockUser(username = "youso")
    void validSearchRendersEscapedResults() throws Exception {
        var place = new PlacesService.Place("<script>alert(1)</script>", "Test address", 150,
                "https://www.google.com/maps/search/?api=1&query=0,0", List.of());
        when(placesService.search(any(LocationQuery.class)))
                .thenReturn(new PlacesService.SearchResult("0, 0", List.of(place)));
        mvc.perform(get("/search").param("latitude", "0").param("longitude", "0").param("radius", "1000"))
                .andExpect(status().isOk()).andExpect(model().attributeExists("result"))
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(containsString("Test address")));
        verify(placesService).search(LocationQuery.parse("", "0", "0", "1000"));
    }

    @Test
    @WithMockUser(username = "youso")
    void providerFailureRendersAnError() throws Exception {
        when(placesService.search(any(LocationQuery.class)))
                .thenThrow(new PlacesService.SearchException("Location search is temporarily unavailable."));
        mvc.perform(get("/search").param("latitude", "0").param("longitude", "0").param("radius", "1000"))
                .andExpect(status().isOk()).andExpect(model().attributeExists("searchError"))
                .andExpect(model().attributeDoesNotExist("result"));
    }
}