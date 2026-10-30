import { useEffect, useRef } from 'react';
import { APP_NAME } from '../config';
import earth from '../assets/earth-bw.webp';

export default function Hero() {
  const heroRef = useRef(null);

  // Scroll progress (0 at the top, 1 after about one screen) is written into a CSS variable.
  // The stylesheet reads it to shrink the red frame, fade the text and so on, which keeps
  // React out of the scroll loop.
  useEffect(() => {
    let frame = 0;
    function update() {
      frame = 0;
      const progress = Math.min(1, Math.max(0, window.scrollY / (window.innerHeight * 0.9)));
      heroRef.current?.style.setProperty('--p', progress.toFixed(4));
    }
    function onScroll() {
      if (!frame) frame = requestAnimationFrame(update);
    }
    update();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => {
      window.removeEventListener('scroll', onScroll);
      cancelAnimationFrame(frame);
    };
  }, []);

  return (
    <section className="hero" id="top" ref={heroRef}>
      <div className="hero-sticky">
        <div className="hero-bg" />
        <div className="hero-card" />
        <div className="hero-stage">
            <SpaceBackdrop />
            <img className="hero-earth" src={earth} alt="" aria-hidden="true" />

            <h1 className="hero-title">
              Track.
              <br />
              Screen.
              <br />
              Warn.
            </h1>

            <div className="hero-wordmark" aria-label={APP_NAME}>
              {APP_NAME.toLowerCase()}
            </div>

            <p className="hero-copy">
              Every day, {APP_NAME} checks India's satellites against every other object tracked in
              orbit and lists the close approaches expected in the next 24 hours.
            </p>

            <div className="hero-actions">
              <a className="pill" href="#globe">
                <small>Open the</small>
                Live globe
              </a>
              <a className="pill" href="#events">
                <small>See today's</small>
                Events
              </a>
            </div>
        </div>
        <div className="hero-handoff">
          <span>Every object. Every orbit.</span>
          <span>Right now.</span>
        </div>
      </div>
    </section>
  );
}

// Star positions are worked out from a formula, so the sky looks the same on every load.
const STARS = Array.from({ length: 120 }, (_, i) => {
  const a = Math.sin(i * 12.9898) * 43758.5453;
  const b = Math.sin(i * 78.233) * 12345.6789;
  return {
    x: (a - Math.floor(a)) * 1200,
    y: (b - Math.floor(b)) * 540,
    r: 0.4 + ((i * 37) % 10) / 10,
    opacity: 0.2 + ((i * 53) % 10) / 14,
  };
});

// The background of the poster: a night sky with stars. It sits behind all the text.
function SpaceBackdrop() {
  return (
    <svg className="hero-space" viewBox="0 0 1200 600" preserveAspectRatio="xMaxYMid slice" aria-hidden="true">
      <defs>
        <radialGradient id="sky" cx="80%" cy="20%" r="75%">
          <stop offset="0" stopColor="#0c0c0e" />
          <stop offset="0.55" stopColor="#070708" />
          <stop offset="1" stopColor="#040405" />
        </radialGradient>
      </defs>

      <rect width="1200" height="600" fill="url(#sky)" />
      {STARS.map((s, i) => (
        <circle key={i} cx={s.x} cy={s.y} r={s.r} fill="#ffffff" opacity={s.opacity} />
      ))}

    </svg>
  );
}
