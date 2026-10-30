# Kaksha frontend

React + Vite + CesiumJS. The globe uses the Natural Earth map bundled with Cesium, so no
Cesium account or token is needed.

```bash
npm install
npm run dev
```

The dev server proxies `/api` to the Spring Boot backend on port 8080. With the backend
running, the page shows real data:

- `GET /api/satellites/indian`, `GET /api/events`, `GET /api/stats/last-run` fill the dashboard
- `GET /api/globe/objects` gives the positions drawn on the live globe, refreshed every 30 s
- `GET /api/events/{id}/geometry` gives the two orbits of an event when "View on globe" is used
- `POST /api/screen/{id}` and `POST /api/watchlist` back the two forms

When the backend is not running, the page falls back to demo data (`src/data/mock.js`) and
shows a "Demo data" badge.

## Layout

- `src/components/Hero.jsx` poster that fades into the black globe section on scroll
- `src/components/GlobeSection.jsx` live orbit view, event orbits and closest-approach marker
- `src/components/Dashboard.jsx` event table, filters, on-demand screening
- `src/components/Analytics.jsx` pipeline funnel and event charts
- `src/lib/orbit.js` simple circular-orbit maths, used only for the demo data
- `src/lib/tracked.js` turns live or demo data into one list of objects the globe draws
