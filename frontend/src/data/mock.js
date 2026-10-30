// Demo data so the interface works before the backend exists.
// Everything here is made up for layout and testing. The satellite names are real,
// but the orbits, events and benchmark numbers are samples, not real results.

import { makeOrbit, position, velocity, crossingOrbit, ringPoints, sub, norm } from '../lib/orbit';

const HOUR = 3600 * 1000;
const now = Date.now();

const indianSatellites = [
  { noradId: 44804, name: 'CARTOSAT-3', altKm: 509, incDeg: 97.5, raanDeg: 40, u0Deg: 10 },
  { noradId: 44233, name: 'RISAT-2B', altKm: 555, incDeg: 37, raanDeg: 120, u0Deg: 200 },
  { noradId: 37387, name: 'RESOURCESAT-2', altKm: 817, incDeg: 98.7, raanDeg: 250, u0Deg: 90 },
  { noradId: 51656, name: 'EOS-04', altKm: 529, incDeg: 97.4, raanDeg: 310, u0Deg: 150 },
  { noradId: 40930, name: 'ASTROSAT', altKm: 650, incDeg: 6, raanDeg: 15, u0Deg: 60 },
  { noradId: 54361, name: 'EOS-06', altKm: 740, incDeg: 98.3, raanDeg: 75, u0Deg: 300 },
  { noradId: 43111, name: 'CARTOSAT-2F', altKm: 505, incDeg: 97.5, raanDeg: 200, u0Deg: 250 },
].map((s) => ({ ...s, orbit: makeOrbit({ ...s, epochMs: now }) }));

// Each demo event: which satellite, how many hours from now, how close, and at what angle
// the two orbits cross.
const eventSpecs = [
  { sat: 0, hours: 3.4, missKm: 0.42, angle: 71, other: [48274, 'COSMOS 2251 DEB', 'DEB'] },
  { sat: 3, hours: 9.1, missKm: 0.88, angle: 122, other: [29228, 'FENGYUN 1C DEB', 'DEB'] },
  { sat: 2, hours: 14.6, missKm: 1.7, angle: 48, other: [33791, 'CZ-4B R/B', 'R/B'] },
  { sat: 1, hours: 5.8, missKm: 2.3, angle: 95, other: [24946, 'IRIDIUM 33 DEB', 'DEB'] },
  { sat: 6, hours: 19.2, missKm: 2.9, angle: 160, other: [27386, 'SL-16 R/B', 'R/B'] },
  { sat: 5, hours: 21.7, missKm: 3.6, angle: 33, other: [26702, 'COSMOS 1408 DEB', 'DEB'] },
  { sat: 0, hours: 12.3, missKm: 4.1, angle: 84, other: [31793, 'DELTA 1 DEB', 'DEB'] },
  { sat: 4, hours: 7.5, missKm: 4.6, angle: 12, other: [20580, 'USA 193 DEB', 'DEB'] },
  { sat: 3, hours: 17.0, missKm: 4.8, angle: 105, other: [42982, 'FALCON 9 R/B', 'R/B'] },
];

function riskOf(missKm) {
  if (missKm < 1) return 'HIGH';
  if (missKm < 3) return 'MEDIUM';
  return 'LOW';
}

const events = eventSpecs
  .map((spec, i) => {
    const primary = indianSatellites[spec.sat];
    const tcaMs = now + spec.hours * HOUR;
    const secondaryOrbit = crossingOrbit(primary.orbit, tcaMs, spec.angle, spec.missKm);
    const relVelocity = norm(sub(velocity(primary.orbit, tcaMs), velocity(secondaryOrbit, tcaMs)));

    return {
      id: i + 1,
      primaryId: primary.noradId,
      primaryName: primary.name,
      secondaryId: spec.other[0],
      secondaryName: spec.other[1],
      secondaryType: spec.other[2],
      secondaryOwner: spec.other[2] === 'DEB' ? 'Russia (CIS)' : 'United States',
      secondaryKind: spec.other[2] === 'DEB' ? 'Debris' : 'Rocket body',
      tca: new Date(tcaMs).toISOString(),
      missKm: spec.missKm,
      relVelKmS: relVelocity,
      risk: riskOf(spec.missKm),
      // Demo values. The first approach of the list was predicted before and held steady, the third one moved.
      timesSeen: i === 0 ? 2 : i === 2 ? 3 : 1,
      previousMissKm: i === 0 ? spec.missKm + 0.11 : i === 2 ? spec.missKm + 1.9 : null,
      stability: i === 0 ? 'STABLE' : i === 2 ? 'CHANGED' : 'NEW',
      // Orbit rings for the globe view, in the same shape the real geometry endpoint returns.
      geometry: {
        tcaMs,
        primaryRing: ringPoints(primary.orbit),
        secondaryRing: ringPoints(secondaryOrbit),
        meeting: position(primary.orbit, tcaMs),
      },
    };
  })
  .sort((a, b) => a.missKm - b.missKm);

// A cloud of other objects so the globe looks like a busy orbit. Seeded so it is the same every load.
function seededRandom(seed) {
  let s = seed;
  return () => {
    s = (s + 0x6d2b79f5) | 0;
    let t = Math.imul(s ^ (s >>> 15), 1 | s);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const random = seededRandom(2024);
const inclinations = [52, 53, 65, 70, 82, 86, 97.6, 98.7];
const backdrop = Array.from({ length: 260 }, () =>
  makeOrbit({
    altKm: 450 + random() * 900,
    incDeg: inclinations[Math.floor(random() * inclinations.length)],
    raanDeg: random() * 360,
    u0Deg: random() * 360,
    epochMs: now,
  }),
);

const stats = {
  isSample: true,
  runAt: new Date(now - 3 * HOUR).toISOString(),
  totalRuntimeMs: 41358,
  stages: [
    { name: 'All pairs', pairs: 736216, ms: 0 },
    { name: 'Altitude filter', pairs: 141874, ms: 38 },
    { name: 'Coarse scan, 50 km', pairs: 412, ms: 41200 },
    { name: 'Refined, under 5 km', pairs: 9, ms: 120 },
  ],
};

export const mockData = {
  isDemo: true,
  satellites: indianSatellites,
  events,
  backdrop,
  stats,
};

export { position };
