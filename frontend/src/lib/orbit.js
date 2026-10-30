// Small orbit toolkit used only for the demo data and the globe.
// Orbits here are circles: good enough to draw and move objects around, not for screening.
// The real screening (SGP4) happens in the Java backend.

const MU = 398600.4418; // Earth's gravitational parameter, km^3/s^2
const EARTH_RADIUS_KM = 6371;
const toRad = (deg) => (deg * Math.PI) / 180;

// Plain 3-element array vector helpers, all in kilometres.
export const add = (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]];
export const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
export const scale = (a, k) => [a[0] * k, a[1] * k, a[2] * k];
export const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
export const cross = (a, b) => [
  a[1] * b[2] - a[2] * b[1],
  a[2] * b[0] - a[0] * b[2],
  a[0] * b[1] - a[1] * b[0],
];
export const norm = (a) => Math.sqrt(dot(a, a));
export const unit = (a) => scale(a, 1 / norm(a));

/**
 * Builds a circular orbit. An orbit is described by two perpendicular unit vectors
 * p and q that lie in the orbit plane; a point on the orbit is a * (cos(u) p + sin(u) q),
 * where u is the angle travelled along the orbit.
 */
export function makeOrbit({ altKm, incDeg, raanDeg, u0Deg, epochMs }) {
  const a = EARTH_RADIUS_KM + altKm;
  const raan = toRad(raanDeg);
  const inc = toRad(incDeg);
  return {
    a,
    p: [Math.cos(raan), Math.sin(raan), 0],
    q: [-Math.sin(raan) * Math.cos(inc), Math.cos(raan) * Math.cos(inc), Math.sin(inc)],
    u0: toRad(u0Deg),
    n: Math.sqrt(MU / a ** 3), // angular speed, radians per second
    epochMs,
  };
}

function angleAt(orbit, tMs) {
  return orbit.u0 + (orbit.n * (tMs - orbit.epochMs)) / 1000;
}

function pointOnOrbit(orbit, u) {
  return scale(add(scale(orbit.p, Math.cos(u)), scale(orbit.q, Math.sin(u))), orbit.a);
}

export function position(orbit, tMs) {
  return pointOnOrbit(orbit, angleAt(orbit, tMs));
}

export function velocity(orbit, tMs) {
  const u = angleAt(orbit, tMs);
  const direction = add(scale(orbit.p, -Math.sin(u)), scale(orbit.q, Math.cos(u)));
  return scale(direction, orbit.a * orbit.n);
}

/** Points along one full revolution, used to draw the orbit as a closed ring. */
export function ringPoints(orbit, steps = 180) {
  const points = [];
  for (let i = 0; i <= steps; i++) {
    points.push(pointOnOrbit(orbit, (2 * Math.PI * i) / steps));
  }
  return points;
}

// Rodrigues' formula: rotate vector v around a unit axis by an angle.
function rotateAbout(v, axis, angle) {
  const c = Math.cos(angle);
  const s = Math.sin(angle);
  return add(add(scale(v, c), scale(cross(axis, v), s)), scale(axis, dot(axis, v) * (1 - c)));
}

/**
 * Makes a second orbit that crosses the first one at time tMs.
 * The new plane is the old plane turned around the line from Earth's centre to the
 * object, so both orbits pass through the same point at that moment. A small change in
 * radius then leaves the two objects exactly missKm apart there.
 */
export function crossingOrbit(orbit, tMs, crossingAngleDeg, missKm) {
  const axis = unit(position(orbit, tMs));
  const angle = toRad(crossingAngleDeg);
  return {
    ...orbit,
    a: orbit.a + missKm,
    p: rotateAbout(orbit.p, axis, angle),
    q: rotateAbout(orbit.q, axis, angle),
  };
}
