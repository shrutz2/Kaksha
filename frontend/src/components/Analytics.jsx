const RISK_COLOR = { HIGH: '#ff4b3e', MEDIUM: '#ffb020', LOW: '#7fa6c9' };

export default function Analytics({ data }) {
  return (
    <section className="analytics" id="pipeline">
      <div className="section-head">
        <p className="eyebrow">How the numbers come out</p>
        <h2>The screening pipeline</h2>
        {data.stats?.isSample && <span className="badge">Sample numbers</span>}
      </div>

      <div className="charts">
        <Funnel stages={data.stats?.stages ?? []} runAt={data.stats?.runAt} horizon={data.stats?.horizonHours} />
        <Hourly events={data.events} />
        <MissStrip events={data.events} />
      </div>
    </section>
  );
}

// Each bar is the number of pairs still alive after a stage. The scale is logarithmic
// because the counts drop from hundreds of thousands to single digits.
function Funnel({ stages, runAt, horizon }) {
  const top = Math.log10(Math.max(2, stages[0]?.pairs ?? 2));
  return (
    <figure className="chart chart-wide">
      <figcaption>
        <strong>Pairs left after each stage</strong>
        {runAt && (
          <span className="muted">
            Last run {runAt.slice(0, 16).replace('T', ' ')} UTC{horizon ? `, screened ${horizon} h ahead` : ''}
          </span>
        )}
      </figcaption>
      <ul className="funnel">
        {stages.map((stage) => {
          const width = Math.max(1.5, (Math.log10(Math.max(1, stage.pairs)) / top) * 100);
          return (
            <li key={stage.name}>
              <span className="funnel-name">{stage.name}</span>
              <span className="funnel-track">
                <span className="funnel-bar" style={{ width: `${width}%` }} />
              </span>
              <span className="mono funnel-count">{stage.pairs.toLocaleString()}</span>
              <span className="mono muted funnel-time">{stage.ms ? `${(stage.ms / 1000).toFixed(2)} s` : ''}</span>
            </li>
          );
        })}
      </ul>
    </figure>
  );
}

// Events in each of the next 24 hours, stacked by risk.
function Hourly({ events }) {
  const hours = Array.from({ length: 24 }, () => ({ HIGH: 0, MEDIUM: 0, LOW: 0 }));
  const now = Date.now();
  events.forEach((e) => {
    const h = Math.floor((new Date(e.tca).getTime() - now) / 3600000);
    if (h >= 0 && h < 24) hours[h][e.risk] += 1;
  });
  const tallest = Math.max(1, ...hours.map((h) => h.HIGH + h.MEDIUM + h.LOW));
  const barW = 22;

  return (
    <figure className="chart">
      <figcaption><strong>Events by hour from now</strong></figcaption>
      <svg viewBox={`0 0 ${24 * barW} 130`} role="img" aria-label="Events per hour">
        {hours.map((h, i) => {
          let y = 110;
          return ['HIGH', 'MEDIUM', 'LOW'].map((risk) => {
            const height = (h[risk] / tallest) * 90;
            y -= height;
            return h[risk] ? (
              <rect key={`${i}-${risk}`} x={i * barW + 3} y={y} width={barW - 6} height={height - 1.5} fill={RISK_COLOR[risk]} rx="1.5" />
            ) : null;
          });
        })}
        <line x1="0" x2={24 * barW} y1="110.5" y2="110.5" stroke="#333" />
        {[0, 6, 12, 18, 23].map((h) => (
          <text key={h} x={h * barW + barW / 2} y="125" textAnchor="middle" className="axis">+{h}h</text>
        ))}
      </svg>
    </figure>
  );
}

// Every event as one dot on a 0 to 5 km line, with the risk limits marked.
function MissStrip({ events }) {
  const width = 480;
  const x = (km) => 20 + (km / 5) * (width - 40);
  return (
    <figure className="chart">
      <figcaption><strong>Miss distance</strong></figcaption>
      <svg viewBox={`0 0 ${width} 130`} role="img" aria-label="Miss distances">
        <line x1={x(0)} x2={x(5)} y1="70" y2="70" stroke="#333" />
        {[1, 3].map((km) => (
          <g key={km}>
            <line x1={x(km)} x2={x(km)} y1="30" y2="100" stroke="#444" strokeDasharray="3 4" />
            <text x={x(km)} y="118" textAnchor="middle" className="axis">{km} km</text>
          </g>
        ))}
        <text x={x(0)} y="118" textAnchor="middle" className="axis">0</text>
        <text x={x(5)} y="118" textAnchor="middle" className="axis">5 km</text>
        {events.map((e, i) => (
          <circle key={e.id} cx={x(e.missKm)} cy={70 + ((i % 3) - 1) * 16} r="6" fill={RISK_COLOR[e.risk]} fillOpacity="0.9" />
        ))}
      </svg>
    </figure>
  );
}
