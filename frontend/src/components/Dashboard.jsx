import { useMemo, useState } from 'react';
import { screenNow, subscribeToSatellite } from '../data/api';

const RISKS = ['HIGH', 'MEDIUM', 'LOW'];

// The table starts with this many rows, and "View more" adds this many each time.
const PAGE_SIZE = 10;

export default function Dashboard({ data, onViewEvent, notice }) {
  const [riskFilter, setRiskFilter] = useState('ALL');
  const [satFilter, setSatFilter] = useState('ALL');
  const [shown, setShown] = useState(PAGE_SIZE);

  // A new filter starts the list from the top again.
  function chooseRisk(risk) {
    setRiskFilter(risk);
    setShown(PAGE_SIZE);
  }
  function chooseSatellite(id) {
    setSatFilter(id);
    setShown(PAGE_SIZE);
  }

  const visible = useMemo(
    () =>
      data.events.filter(
        (e) =>
          (riskFilter === 'ALL' || e.risk === riskFilter) &&
          (satFilter === 'ALL' || String(e.primaryId) === satFilter),
      ),
    [data.events, riskFilter, satFilter],
  );

  const count = (risk) => data.events.filter((e) => e.risk === risk).length;
  const closest = data.events.reduce((min, e) => Math.min(min, e.missKm), Infinity);

  return (
    <section className="dashboard" id="events">
      <div className="section-head">
        <p className="eyebrow">Next 24 hours</p>
        <h2>Conjunction watch</h2>
        {data.isDemo && <span className="badge">Demo data</span>}
      </div>

      <div className="kpis">
        <Kpi label="Events flagged" value={data.events.length} />
        <Kpi label="High risk" value={count('HIGH')} tone="HIGH" />
        <Kpi label="Closest approach" value={Number.isFinite(closest) ? `${closest.toFixed(2)} km` : '-'} />
        <Kpi label="Satellites watched" value={data.satellites.length} />
      </div>

      <div className="toolbar">
        <div className="chips">
          {['ALL', ...RISKS].map((risk) => (
            <button
              key={risk}
              className={riskFilter === risk ? 'chip active' : 'chip'}
              onClick={() => chooseRisk(risk)}
            >
              {risk === 'ALL' ? 'All risks' : risk.toLowerCase()}
            </button>
          ))}
        </div>
        <select value={satFilter} onChange={(e) => chooseSatellite(e.target.value)} aria-label="Filter by satellite">
          <option value="ALL">All satellites</option>
          {data.satellites.map((s) => (
            <option key={s.noradId} value={String(s.noradId)}>
              {s.name}
            </option>
          ))}
        </select>
      </div>

      {notice && <p className="panel-error notice">{notice}</p>}

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Risk</th>
              <th>Indian satellite</th>
              <th>Other object</th>
              <th>Closest approach (UTC)</th>
              <th className="num">Miss</th>
              <th className="num">Speed</th>
              <th>Prediction</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {visible.slice(0, shown).map((e) => (
              <tr key={e.id}>
                <td><span className={`risk risk-${e.risk}`}>{e.risk.toLowerCase()}</span></td>
                <td>{e.primaryName}</td>
                <td>
                  <div>{e.secondaryName}</div>
                  <small className="object-info">
                    {[e.secondaryOwner, e.secondaryKind, `#${e.secondaryId}`].filter(Boolean).join(' · ')}
                  </small>
                </td>
                <td className="mono">{e.tca.slice(0, 16).replace('T', ' ')}</td>
                <td className="num mono">{e.missKm.toFixed(2)} km</td>
                <td className="num mono">{e.relVelKmS.toFixed(1)} km/s</td>
                <td><Prediction event={e} /></td>
                <td className="num">
                  <button className="link-button" onClick={() => onViewEvent(e)}>
                    View on globe
                  </button>
                </td>
              </tr>
            ))}
            {visible.length === 0 && (
              <tr>
                <td colSpan="8" className="empty">No events match these filters.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {visible.length > PAGE_SIZE && (
        <div className="more">
          <span className="muted">Showing {Math.min(shown, visible.length)} of {visible.length}</span>
          <div>
            {shown < visible.length && (
              <button className="button button-quiet" onClick={() => setShown(shown + PAGE_SIZE)}>
                View more
              </button>
            )}
            {shown > PAGE_SIZE && (
              <button className="link-button" onClick={() => setShown(PAGE_SIZE)}>
                Show less
              </button>
            )}
          </div>
        </div>
      )}

      <div className="actions">
        <ScreenNow satellites={data.satellites} isDemo={data.isDemo} />
        <WatchForm satellites={data.satellites} />
      </div>
    </section>
  );
}

// Says whether this approach was also predicted by an earlier daily run, and if so how much the
// result moved. A result that stays the same after the element sets were updated is more
// believable than one that jumps around.
function Prediction({ event }) {
  if (!event.timesSeen || event.timesSeen < 2 || event.previousMissKm == null) {
    return <span className="muted">first time</span>;
  }
  const note = `seen ${event.timesSeen} times, was ${event.previousMissKm.toFixed(2)} km`;
  return (
    <span className={`history history-${event.stability}`} title={note}>
      {event.stability === 'STABLE' ? 'stable' : 'changed'}
      <small>{note}</small>
    </span>
  );
}

function Kpi({ label, value, tone }) {
  return (
    <div className="kpi">
      <span className="kpi-label">{label}</span>
      <span className={tone ? `kpi-value risk-text-${tone}` : 'kpi-value'}>{value}</span>
    </div>
  );
}

// Runs the on-demand screening for one satellite and shows the summary it returns.
function ScreenNow({ satellites, isDemo }) {
  const [noradId, setNoradId] = useState(satellites[0]?.noradId ?? '');
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');

  async function run() {
    setRunning(true);
    setResult(null);
    setError('');
    try {
      setResult(await screenNow(noradId, isDemo));
    } catch (e) {
      setError(e.message);
    }
    setRunning(false);
  }

  return (
    <div className="panel">
      <h3>Screen a satellite now</h3>
      <p className="muted">Checks one satellite against the whole catalog, without waiting for the daily run.</p>
      <div className="panel-controls">
        <select value={noradId} onChange={(e) => setNoradId(e.target.value)} aria-label="Satellite to screen">
          {satellites.map((s) => (
            <option key={s.noradId} value={s.noradId}>{s.name}</option>
          ))}
        </select>
        <button className="button" onClick={run} disabled={running}>
          {running ? 'Screening...' : 'Screen now'}
        </button>
      </div>
      {result && (
        <p className="panel-result mono">
          {result.objectsChecked.toLocaleString()} objects checked, {result.eventsFound} events, {(result.runtimeMs / 1000).toFixed(1)} s
          {result.simulated && ' (simulated, backend not connected)'}
        </p>
      )}
      {error && <p className="panel-error">{error}</p>}
    </div>
  );
}

// Signs an email address up for alerts about one satellite.
function WatchForm({ satellites }) {
  const [email, setEmail] = useState('');
  const [noradId, setNoradId] = useState(satellites[0]?.noradId ?? '');
  const [message, setMessage] = useState(null);

  async function submit(e) {
    e.preventDefault();
    setMessage(null);
    try {
      const response = await subscribeToSatellite(email, noradId);
      setMessage({
        ok: true,
        text: response.alreadySubscribed ? 'You were already subscribed to this satellite.' : 'Subscribed. You will get an email if a high risk event is found.',
      });
    } catch (error) {
      setMessage({ ok: false, text: error.message });
    }
  }

  return (
    <form className="panel" onSubmit={submit}>
      <h3>Get an alert</h3>
      <p className="muted">Enter your email to be told when a high risk event is found for a satellite.</p>
      <div className="panel-controls">
        <input
          type="email"
          required
          placeholder="you@example.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          aria-label="Email address"
        />
        <select value={noradId} onChange={(e) => setNoradId(e.target.value)} aria-label="Satellite to watch">
          {satellites.map((s) => (
            <option key={s.noradId} value={s.noradId}>{s.name}</option>
          ))}
        </select>
        <button className="button" type="submit">Subscribe</button>
      </div>
      {message && <p className={message.ok ? 'panel-result' : 'panel-error'}>{message.text}</p>}
    </form>
  );
}
