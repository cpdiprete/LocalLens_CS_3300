package com.example.demo.dto;

/** Validated input for an address search or a coordinate search. */
public record LocationQuery(String address, Point coordinates, int radiusMeters) {
    public LocationQuery {
        address = address == null ? "" : address.trim();
        
        boolean hasAddress = !address.isEmpty();
        boolean hasCoordinates = coordinates != null;
        if (!hasAddress && !hasCoordinates) {
            throw new IllegalArgumentException("Enter a location, or both latitude and longitude.");
        }
        if (hasAddress && hasCoordinates) {
            throw new IllegalArgumentException("Enter a location OR coordinates, but not both.");
        }
        if (address.length() > 200) {
            throw new IllegalArgumentException("Keep the location under 201 characters.");
        }
        if (radiusMeters < 1 || radiusMeters > 50000) {
            throw new IllegalArgumentException("Radius must be a whole number from 1 to 50000 meters.");
        }
    }

    public static LocationQuery parse(String location, String latitude, String longitude, String radius) {
        latitude = latitude == null ? "" : latitude.trim();
        longitude = longitude == null ? "" : longitude.trim();
        if (latitude.isEmpty() != longitude.isEmpty()) {
            throw new IllegalArgumentException("Enter both latitude and longitude.");
        }
        int meters;
        try {
            meters = Integer.parseInt(radius == null ? "" : radius.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Radius must be a whole number from 1 to 50000 meters.");
        }
        Point point = null;
        if (!latitude.isEmpty()) {
            try {
                point = new Point(Double.parseDouble(latitude), Double.parseDouble(longitude));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Coordinates must be valid numbers.");
            }
        }
        return new LocationQuery(location, point, meters);
    }

    public record Point(double latitude, double longitude) {
        public Point {
            if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
                throw new IllegalArgumentException("Latitude must be between -90 and 90.");
            }
            if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
                throw new IllegalArgumentException("Longitude must be between -180 and 180.");
            }
        }

        /** Straight-line surface distance using the haversine formula, in meters. */
        public double distanceTo(Point other) {
            double lat1 = Math.toRadians(latitude);
            double lat2 = Math.toRadians(other.latitude);
            double dLat = lat2 - lat1;
            double dLon = Math.toRadians(other.longitude - longitude);
            double a = Math.pow(Math.sin(dLat / 2), 2)
                    + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLon / 2), 2);
            return 6371008.8 * 2 * Math.asin(Math.sqrt(Math.max(0, Math.min(1, a))));
        }
    }
}