package com.example.demo.service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.example.demo.dto.LocationQuery;
import com.example.demo.dto.LocationQuery.Point;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Location search backed by Geoapify (OpenStreetMap data).
 *
 * Geoapify was chosen over Google Maps Platform because its free tier requires no
 * billing account and no card on file: 3,000 credits/day, one credit per request.
 * Both endpoints return GeoJSON, so a single FeatureCollection shape parses both.
 */
@Service
public class PlacesService {
    private static final String PLACES = "https://api.geoapify.com/v2/places";
    private static final String GEOCODE = "https://api.geoapify.com/v1/geocode/search";

    /**
     * Geoapify requires at least one category. This is a broad "places worth visiting"
     * set for local discovery; narrow it to change what LocalLens surfaces.
     * Full list: https://apidocs.geoapify.com/docs/places/#categories
     */
    static final String CATEGORIES =
            "catering,commercial,entertainment,leisure,tourism,accommodation";

    private static final int MAX_RESULTS = 20;

    private final String apiKey;
    private final RestClient client;

    @Autowired
    public PlacesService(@Value("${GEOAPIFY_API_KEY:}") String apiKey) {
        this(apiKey, createClient());
    }

    // Package-private constructor lets tests supply a mock HTTP client.
    PlacesService(String apiKey, RestClient client) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.client = client;
    }

    private static RestClient createClient() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        return RestClient.builder().requestFactory(factory).build();
    }

    public SearchResult search(LocationQuery query) {
        if (apiKey.isBlank()) {
            throw new SearchException("Location search is not configured yet. Please contact the project team.");
        }
        try {
            Point center = query.coordinates();
            String label;

            if (center == null) {
                FeatureCollection geo = client.get().uri(geocodeUri(query.address()))
                        .retrieve().body(FeatureCollection.class);
                Properties match = firstProperties(geo);
                if (match == null) {
                    throw new SearchException("That location was not found. Try a more specific address.");
                }
                center = checkedPoint(match.lat(), match.lon());
                label = match.formatted() == null || match.formatted().isBlank()
                        ? query.address() : match.formatted();
            } else {
                label = center.latitude() + ", " + center.longitude();
            }

            FeatureCollection response = client.get().uri(placesUri(center, query.radiusMeters()))
                    .retrieve().body(FeatureCollection.class);
            if (response == null) throw unavailable();

            List<Place> places = new ArrayList<>();
            for (Feature feature : response.features() == null ? List.<Feature>of() : response.features()) {
                if (feature == null || feature.properties() == null) continue;
                Properties props = feature.properties();
                if (props.lat() == null || props.lon() == null) continue;

                Point position;
                try {
                    position = new Point(props.lat(), props.lon());
                } catch (IllegalArgumentException ex) {
                    continue;
                }

                // Geoapify's circle filter already bounds results, but the radius is
                // re-checked locally so the displayed distance and the filter agree.
                double distance = center.distanceTo(position);
                if (distance > query.radiusMeters()) continue;

                String name = firstNonBlank(props.name(), props.address_line1(), "Unnamed place");
                String address = firstNonBlank(props.formatted(), props.address_line2(), "");

                String mapsUrl = "https://www.google.com/maps/search/?api=1&query="
                        + position.latitude() + "," + position.longitude();

                places.add(new Place(name, address, distance, mapsUrl, List.of()));
            }
            places.sort(Comparator.comparingDouble(Place::distanceMeters));
            return new SearchResult(label, List.copyOf(places));

        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 429) {
                throw new SearchException("The search service limit was reached. Please try again later.");
            }
            // Do not expose upstream messages or URLs, which carry the API key.
            throw unavailable();
        } catch (RestClientException ex) {
            throw unavailable();
        }
    }

    // Built as complete URIs rather than templates so the API key and the
    // punctuation-heavy circle filter are encoded exactly once, predictably.
    private URI geocodeUri(String address) {
        return URI.create(GEOCODE
                + "?text=" + enc(address)
                + "&limit=1"
                + "&format=geojson"
                + "&apiKey=" + enc(apiKey));
    }

    private URI placesUri(Point center, int radiusMeters) {
        String circle = "circle:" + center.longitude() + "," + center.latitude() + "," + radiusMeters;
        return URI.create(PLACES
                + "?categories=" + enc(CATEGORIES)
                + "&filter=" + enc(circle)
                + "&limit=" + MAX_RESULTS
                + "&apiKey=" + enc(apiKey));
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static Properties firstProperties(FeatureCollection collection) {
        if (collection == null || collection.features() == null || collection.features().isEmpty()) {
            return null;
        }
        Feature first = collection.features().get(0);
        return first == null ? null : first.properties();
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) return candidate;
        }
        return "";
    }

    private static Point checkedPoint(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) throw unavailable();
        try {
            return new Point(latitude, longitude);
        } catch (IllegalArgumentException ex) {
            throw unavailable();
        }
    }

    private static SearchException unavailable() {
        return new SearchException("Location search is temporarily unavailable. Try again or contact the project team.");
    }

    public static class SearchException extends RuntimeException {
        public SearchException(String message) { super(message); }
    }

    public record SearchResult(String centerLabel, List<Place> places) {}

    public record Place(String name, String address, double distanceMeters, String mapsUrl,
                        List<Attribution> attributions) {}

    public record Attribution(String provider, String providerUri) {}

    /** Geoapify returns GeoJSON from both the geocoding and the places endpoint. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FeatureCollection(List<Feature> features) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Feature(Properties properties) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Properties(String name, String formatted, String address_line1, String address_line2,
                             Double lat, Double lon, String place_id) {}
}