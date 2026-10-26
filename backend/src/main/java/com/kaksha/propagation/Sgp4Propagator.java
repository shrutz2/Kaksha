package com.kaksha.propagation;

import com.kaksha.domain.OrbitalElements;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.propagation.analytical.tle.TLE;
import org.orekit.propagation.analytical.tle.TLEPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

/**
 * Predicts the position of one object with SGP4, the standard model for public orbital data.
 *
 * The stored mean elements are first packed into an Orekit TLE object, then Orekit's SGP4
 * (or SDP4 for high orbits, chosen automatically) does the maths. Orekit must already have
 * its data loaded, see {@link OrekitData}.
 */
public class Sgp4Propagator {

    private static final double SECONDS_PER_DAY = Constants.JULIAN_DAY;

    // The satellite number and launch details are only labels inside the TLE text. They have no
    // effect on the prediction, and catalog numbers above 99999 do not fit the TLE text format,
    // so fixed placeholder values are used.
    private static final int PLACEHOLDER_NUMBER = 99999;
    private static final int PLACEHOLDER_LAUNCH_YEAR = 2000;

    private final TLEPropagator propagator;

    public Sgp4Propagator(OrbitalElements e) {
        this.propagator = TLEPropagator.selectExtrapolator(toTle(e));
    }

    /** Position and velocity at the given time. */
    public StateVector propagate(Instant time) {
        PVCoordinates pv = propagator.getPVCoordinates(toAbsoluteDate(time), propagator.getFrame());
        Vector3D p = pv.getPosition();
        Vector3D v = pv.getVelocity();
        // Orekit works in metres; the rest of the program uses kilometres.
        return new StateVector(
                p.getX() / 1000, p.getY() / 1000, p.getZ() / 1000,
                v.getX() / 1000, v.getY() / 1000, v.getZ() / 1000);
    }

    /**
     * Positions at regular times, packed as x0, y0, z0, x1, y1, z1, ... in kilometres.
     * Faster than calling {@link #propagate} in a loop because the start date is converted once.
     */
    public double[] track(Instant start, int stepSeconds, int count) {
        AbsoluteDate first = toAbsoluteDate(start);
        double[] positions = new double[3 * count];
        for (int i = 0; i < count; i++) {
            Vector3D p = propagator.getPVCoordinates(first.shiftedBy((double) i * stepSeconds),
                    propagator.getFrame()).getPosition();
            positions[3 * i] = p.getX() / 1000;
            positions[3 * i + 1] = p.getY() / 1000;
            positions[3 * i + 2] = p.getZ() / 1000;
        }
        return positions;
    }

    private static TLE toTle(OrbitalElements e) {
        // The stored values use days and degrees; Orekit wants seconds and radians.
        double meanMotion = e.getMeanMotion() * 2 * FastMath.PI / SECONDS_PER_DAY;
        double meanMotionDot = e.getMeanMotionDot() * 2 * FastMath.PI / (SECONDS_PER_DAY * SECONDS_PER_DAY);
        double meanMotionDdot = e.getMeanMotionDdot() * 2 * FastMath.PI
                / (SECONDS_PER_DAY * SECONDS_PER_DAY * SECONDS_PER_DAY);

        return new TLE(
                PLACEHOLDER_NUMBER,
                'U',
                PLACEHOLDER_LAUNCH_YEAR, 1, "A",
                e.getEphemerisType(),
                e.getElementSetNo(),
                toAbsoluteDate(e.getEpoch().toInstant(ZoneOffset.UTC)),
                meanMotion,
                meanMotionDot,
                meanMotionDdot,
                e.getEccentricity(),
                FastMath.toRadians(e.getInclination()),
                FastMath.toRadians(e.getArgOfPericenter()),
                FastMath.toRadians(e.getRaan()),
                FastMath.toRadians(e.getMeanAnomaly()),
                e.getRevAtEpoch(),
                e.getBstar(),
                TimeScalesFactory.getUTC());
    }

    private static AbsoluteDate toAbsoluteDate(Instant time) {
        ZonedDateTime utc = time.atZone(ZoneOffset.UTC);
        double seconds = utc.getSecond() + utc.getNano() / 1e9;
        return new AbsoluteDate(utc.getYear(), utc.getMonthValue(), utc.getDayOfMonth(),
                utc.getHour(), utc.getMinute(), seconds, TimeScalesFactory.getUTC());
    }
}
