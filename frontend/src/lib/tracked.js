import { position } from './orbit';

// The globe draws a list of "tracked" objects. Each one has an id, a name, an Indian flag and
// positionAt(timeMs), which returns [x, y, z] in kilometres. Live data and demo data are both
// turned into this one shape so the globe does not care where the numbers come from.

/** Live snapshot from the backend: move each object forward from the snapshot time with its velocity. */
export function fromSnapshot(snapshot) {
  const t0 = snapshot.generatedAtMs;
  return snapshot.objects.map((o) => ({
    id: o.id,
    name: o.name,
    indian: o.indian,
    positionAt: (tMs) => {
      const dt = (tMs - t0) / 1000;
      return [o.p[0] + o.v[0] * dt, o.p[1] + o.v[1] * dt, o.p[2] + o.v[2] * dt];
    },
  }));
}

/** Demo data: objects on made-up circular orbits. */
export function fromDemo(data) {
  const others = data.backdrop.map((orbit, i) => ({
    id: -(i + 1),
    name: 'Demo object',
    indian: false,
    positionAt: (tMs) => position(orbit, tMs),
  }));
  const indian = data.satellites
    .filter((s) => s.orbit)
    .map((s) => ({
      id: s.noradId,
      name: s.name,
      indian: true,
      positionAt: (tMs) => position(s.orbit, tMs),
    }));
  return [...others, ...indian];
}
