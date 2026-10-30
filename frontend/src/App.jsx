import { useEffect, useState } from 'react';
import { loadData, loadGeometry, loadLiveObjects } from './data/api';
import { fromDemo, fromSnapshot } from './lib/tracked';
import Nav from './components/Nav';
import Hero from './components/Hero';
import GlobeSection from './components/GlobeSection';
import Dashboard from './components/Dashboard';
import Analytics from './components/Analytics';
import Footer from './components/Footer';

// How often fresh positions are requested from the backend. Between requests the browser
// moves every object along its velocity.
const LIVE_REFRESH_MS = 30000;

export default function App() {
  const [data, setData] = useState(null);
  const [tracked, setTracked] = useState([]);
  const [selectedEvent, setSelectedEvent] = useState(null);
  const [notice, setNotice] = useState('');

  useEffect(() => {
    loadData().then((loaded) => {
      setData(loaded);
      if (loaded.isDemo) {
        setTracked(fromDemo(loaded));
      }
    });
  }, []);

  // With real data, keep asking the backend for fresh positions.
  useEffect(() => {
    if (!data || data.isDemo) return undefined;
    let cancelled = false;
    async function refresh() {
      try {
        const snapshot = await loadLiveObjects();
        if (!cancelled) setTracked(fromSnapshot(snapshot));
      } catch (error) {
        console.warn('Could not refresh live positions:', error.message);
      }
    }
    refresh();
    const timer = setInterval(refresh, LIVE_REFRESH_MS);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, [data]);

  // Choosing an event in the table sends the user up to the globe and shows that event there.
  async function showEventOnGlobe(event) {
    setNotice('');
    try {
      const geometry = event.geometry ?? (await loadGeometry(event.id));
      setSelectedEvent({ ...event, geometry });
      scrollToGlobe();
    } catch (error) {
      console.error('Could not load the orbits of this event:', error.message);
      setNotice(`The orbits of this event could not be loaded (${error.message}).`);
    }
  }

  // A smooth scroll can be cut short, for example when the page is still changing size. If the
  // globe is not at the top of the screen a moment later, jump there directly.
  function scrollToGlobe() {
    const globe = document.getElementById('globe');
    if (!globe) return;
    globe.scrollIntoView({ behavior: 'smooth' });
    setTimeout(() => {
      if (Math.abs(globe.getBoundingClientRect().top) > 80) {
        globe.scrollIntoView({ behavior: 'auto' });
      }
    }, 1200);
  }

  return (
    <>
      <Nav />
      <Hero />
      {data && (
        <>
          <GlobeSection
            data={data}
            tracked={tracked}
            selectedEvent={selectedEvent}
            onClearEvent={() => setSelectedEvent(null)}
          />
          <Dashboard data={data} onViewEvent={showEventOnGlobe} notice={notice} />
          <Analytics data={data} />
        </>
      )}
      <Footer />
    </>
  );
}
