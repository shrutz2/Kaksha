import { mockData } from './mock';

async function getJson(path, options) {
  const response = await fetch(path, options);
  if (!response.ok) {
    let reason = `${path} returned ${response.status}`;
    try {
      reason = (await response.json()).error ?? reason;
    } catch {
      // the body was not json, keep the generic message
    }
    throw new Error(reason);
  }
  return response.json();
}

/**
 * Loads everything the page needs. If the backend cannot be reached the page falls back to
 * demo data and says so, instead of showing an empty screen.
 */
export async function loadData() {
  try {
    const [satellites, events] = await Promise.all([
      getJson('/api/satellites/indian'),
      getJson('/api/events'),
    ]);
    // The benchmark panel is optional: before the first run there are no stats yet.
    const stats = await getJson('/api/stats/last-run').catch(() => null);
    return { isDemo: false, satellites, events, backdrop: [], stats };
  } catch (error) {
    console.info('Backend not reachable, using demo data:', error.message);
    return mockData;
  }
}

/** Current positions of the objects drawn on the globe. */
export async function loadLiveObjects() {
  const snapshot = await getJson('/api/globe/objects');
  return { generatedAtMs: Date.parse(snapshot.generatedAt), objects: snapshot.objects };
}

/** The two orbits and the meeting point of an event, in the shape the globe draws. */
export async function loadGeometry(eventId) {
  const g = await getJson(`/api/events/${eventId}/geometry`);
  return {
    tcaMs: Date.parse(g.tca),
    primaryRing: g.primaryRing,
    secondaryRing: g.secondaryRing,
    meeting: g.meeting,
  };
}

/**
 * What each screening run predicted for one approach, oldest first. The demo data has no server,
 * so it builds the earlier points from the numbers already stored on the event.
 */
export async function loadHistory(event, isDemo) {
  if (!isDemo) {
    return getJson(`/api/events/${event.id}/history`);
  }
  const hour = 3600 * 1000;
  const count = Math.min(event.timesSeen ?? 1, 4);
  const now = Date.now();
  return Array.from({ length: count }, (_, i) => {
    const age = count - 1 - i;
    // The newest point is the event itself, the one before it is the stored earlier prediction.
    const missKm = age === 0 ? event.missKm : event.previousMissKm + (age - 1) * 0.15;
    return { madeAt: new Date(now - age * 6 * hour).toISOString(), missKm, tca: event.tca };
  });
}

/** Asks the backend to screen one satellite against the whole catalog. */
export async function screenNow(noradId, isDemo) {
  try {
    return { ...(await getJson(`/api/screen/${noradId}`, { method: 'POST' })), simulated: false };
  } catch (error) {
    if (!isDemo) {
      throw error;
    }
    // Demo only: pretend to work for a moment so the button can be tried.
    await new Promise((resolve) => setTimeout(resolve, 1400));
    return { noradId, objectsChecked: 14212, eventsFound: 2, runtimeMs: 2310, simulated: true };
  }
}

export function subscribeToSatellite(email, noradId) {
  return getJson('/api/watchlist', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, noradId: Number(noradId) }),
  });
}
