import com.example.demo.dto.LocationQuery;
import com.example.demo.dto.LocationQuery.Point;

/** Run with a JDK only; it tests the real production validation and distance code. */
public class LocationQuerySmokeTest {
    private static int checks;

    public static void main(String[] args) {
        var address = LocationQuery.parse(" Atlanta ", "", "", "1000");
        check(address.address().equals("Atlanta") && address.coordinates() == null, "address mode");
        var zero = LocationQuery.parse("", "0", "0", "1");
        check(zero.coordinates().equals(new Point(0, 0)), "zero coordinates");
        check(LocationQuery.parse("", "-90", "-180", "50000").radiusMeters() == 50000, "lower boundaries");
        check(LocationQuery.parse("", "90", "180", "1").radiusMeters() == 1, "upper boundaries");
        reject(() -> LocationQuery.parse("", "", "", "1000"), "empty input");
        reject(() -> LocationQuery.parse("Atlanta", "0", "0", "1000"), "ambiguous input");
        reject(() -> LocationQuery.parse("", "0", "", "1000"), "missing longitude");
        reject(() -> LocationQuery.parse("", "", "0", "1000"), "missing latitude");
        reject(() -> LocationQuery.parse("", "NaN", "0", "1000"), "NaN");
        reject(() -> LocationQuery.parse("", "0", "Infinity", "1000"), "infinity");
        reject(() -> LocationQuery.parse("", "abc", "0", "1000"), "nonnumeric coordinate");
        reject(() -> LocationQuery.parse("", "90.1", "0", "1000"), "latitude range");
        reject(() -> LocationQuery.parse("", "0", "-180.1", "1000"), "longitude range");
        reject(() -> LocationQuery.parse("", "0", "0", "0"), "zero radius");
        reject(() -> LocationQuery.parse("", "0", "0", "50001"), "large radius");
        reject(() -> LocationQuery.parse("", "0", "0", "-1"), "negative radius");
        reject(() -> LocationQuery.parse("", "0", "0", "1.5"), "fractional radius");
        reject(() -> LocationQuery.parse("", "0", "0", ""), "missing radius");
        reject(() -> LocationQuery.parse("x".repeat(201), "", "", "1000"), "long address");
        reject(() -> LocationQuery.parse(null, null, null, null), "null input");
        check(new Point(0, 0).distanceTo(new Point(0, 0)) == 0, "same point distance");
        double oneDegree = new Point(0, 0).distanceTo(new Point(0, 1));
        check(Math.abs(oneDegree - 111195.08) < 0.1, "equator distance");
        double dateline = new Point(0, 179.9).distanceTo(new Point(0, -179.9));
        check(Math.abs(dateline - 22239.016) < 0.1, "dateline crossing");
        double antipodal = new Point(0, 0).distanceTo(new Point(0, 180));
        check(Double.isFinite(antipodal) && Math.abs(antipodal - 20015114.44) < 0.1, "antipodal distance");
        System.out.println(checks + " validation and distance checks passed.");
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }

    private static void reject(Runnable action, String label) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            checks++;
            return;
        }
        throw new AssertionError("Expected rejection: " + label);
    }
}