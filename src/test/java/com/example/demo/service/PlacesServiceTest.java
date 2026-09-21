package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.example.demo.dto.LocationQuery;

class PlacesServiceTest {
    private MockRestServiceServer server;
    private PlacesService service;

    // Pinned verbatim so a change to the request format fails loudly instead of
    // silently sending Geoapify something it will reject.
    private static final String PLACES_AT_ORIGIN =
            "https://api.geoapify.com/v2/places"
            + "?categories=catering%2Ccommercial%2Centertainment%2Cleisure%2Ctourism%2Caccommodation"
            + "&filter=circle%3A0.0%2C0.0%2C1000"
            + "&limit=20"
            + "&apiKey=test-only-key";

    private static final String PLACES_AT_ATLANTA =
            "https://api.geoapify.com/v2/places"
            + "?categories=catering%2Ccommercial%2Centertainment%2Cleisure%2Ctourism%2Caccommodation"
            + "&filter=circle%3A-84.39%2C33.75%2C1000"
            + "&limit=20"
            + "&apiKey=test-only-key";

    private static final String GEOCODE_ATLANTA =
            "https://api.geoapify.com/v1/geocode/search"
            + "?text=Atlanta&limit=1&format=geojson&apiKey=test-only-key";

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new PlacesService("test-only-key", builder.build());
    }

    private LocationQuery coordinates() {
        return LocationQuery.parse("", "0", "0", "1000");
    }

    @Test
    void sendsCorrectSearchAndRemovesOutOfRangeResults() {
        server.expect(requestTo(PLACES_AT_ORIGIN))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                    {"type":"FeatureCollection","features":[
                      {"properties":{"name":"Farther","formatted":"Far address","lat":0.006,"lon":0.0}},
                      {"properties":{"name":"Outside","formatted":"Way out","lat":0.02,"lon":0.0}},
                      {"properties":{"name":"No coordinates","formatted":"Broken entry"}},
                      {"properties":{"name":"Nearer","formatted":"Near address","lat":0.001,"lon":0.0,
                                     "place_id":"abc","categories":["catering.cafe"],"unknownField":true}}
                    ]}
                    """, MediaType.APPLICATION_JSON));

        var result = service.search(coordinates());

        assertThat(result.places()).extracting(PlacesService.Place::name)
                .containsExactly("Nearer", "Farther");
        assertThat(result.places().get(0).distanceMeters()).isBetween(111.0, 112.0);
        assertThat(result.places().get(0).address()).isEqualTo("Near address");
        assertThat(result.places().get(0).mapsUrl()).startsWith("https://www.google.com/maps/search/");
        assertThat(result.centerLabel()).isEqualTo("0.0, 0.0");
        server.verify();
    }

    @Test
    void geocodesANamedLocationBeforePlacesSearch() {
        server.expect(requestTo(GEOCODE_ATLANTA))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                    {"type":"FeatureCollection","features":[
                      {"properties":{"formatted":"Atlanta, GA, United States of America",
                                     "lat":33.75,"lon":-84.39,"country":"United States","extra":true}}
                    ]}
                    """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(PLACES_AT_ATLANTA))
                .andRespond(withSuccess("{\"features\":[]}", MediaType.APPLICATION_JSON));

        var result = service.search(LocationQuery.parse("Atlanta", "", "", "1000"));

        assertThat(result.centerLabel()).isEqualTo("Atlanta, GA, United States of America");
        assertThat(result.places()).isEmpty();
        server.verify();
    }

    @Test
    void fallsBackToAddressLineWhenAPlaceHasNoName() {
        server.expect(requestTo(PLACES_AT_ORIGIN))
                .andRespond(withSuccess("""
                    {"features":[
                      {"properties":{"address_line1":"Unnamed Cafe on Main","address_line2":"Atlanta, GA",
                                     "lat":0.001,"lon":0.0}},
                      {"properties":{"lat":0.002,"lon":0.0}}
                    ]}
                    """, MediaType.APPLICATION_JSON));

        var result = service.search(coordinates());

        assertThat(result.places()).extracting(PlacesService.Place::name)
                .containsExactly("Unnamed Cafe on Main", "Unnamed place");
        assertThat(result.places().get(0).address()).isEqualTo("Atlanta, GA");
        server.verify();
    }

    @Test
    void handlesUnknownLocationsWithoutCallingPlacesSearch() {
        server.expect(requestTo(GEOCODE_ATLANTA))
                .andRespond(withSuccess("{\"features\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.search(LocationQuery.parse("Atlanta", "", "", "1000")))
                .isInstanceOf(PlacesService.SearchException.class).hasMessageContaining("not found");
        server.verify();
    }

    @Test
    void handlesAnEmptyFeatureArray() {
        server.expect(requestTo(PLACES_AT_ORIGIN))
                .andRespond(withSuccess("{\"features\":[]}", MediaType.APPLICATION_JSON));
        assertThat(service.search(coordinates()).places()).isEmpty();
        server.verify();
    }

    @Test
    void handlesQuotaErrorsWithoutLeakingTheUpstreamBody() {
        server.expect(requestTo(PLACES_AT_ORIGIN)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .body("Sensitive upstream details test-only-key"));
        assertThatThrownBy(() -> service.search(coordinates()))
                .isInstanceOf(PlacesService.SearchException.class)
                .hasMessageContaining("limit").hasMessageNotContaining("test-only-key");
        server.verify();
    }

    @Test
    void handlesAnInvalidKeyWithoutLeakingTheUpstreamBody() {
        server.expect(requestTo(PLACES_AT_ORIGIN)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .body("Invalid apiKey test-only-key"));
        assertThatThrownBy(() -> service.search(coordinates()))
                .isInstanceOf(PlacesService.SearchException.class)
                .hasMessageContaining("unavailable").hasMessageNotContaining("test-only-key");
        server.verify();
    }

    @Test
    void handlesMalformedProviderJson() {
        server.expect(requestTo(PLACES_AT_ORIGIN))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> service.search(coordinates()))
                .isInstanceOf(PlacesService.SearchException.class).hasMessageContaining("unavailable");
        server.verify();
    }

    @Test
    void rejectsMissingKeyBeforeCallingTheProvider() {
        service = new PlacesService("", RestClient.builder().build());
        assertThatThrownBy(() -> service.search(coordinates()))
                .isInstanceOf(PlacesService.SearchException.class).hasMessageContaining("not configured");
        server.verify();
    }
}