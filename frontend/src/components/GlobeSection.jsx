import { useEffect, useRef, useState } from 'react';
import * as Cesium from 'cesium';
import 'cesium/Build/Cesium/Widgets/widgets.css';
import { fixedFrame, toCartesian } from '../lib/cesiumFrame';
import MissTrend from './MissTrend';

const INDIAN_COLOR = Cesium.Color.fromCssColorString('#ff4b3e');
const OTHER_COLOR = Cesium.Color.fromCssColorString('#9aa4b5').withAlpha(0.7);
const SECONDARY_COLOR = Cesium.Color.fromCssColorString('#39e1ff');

// A camera position straight above a point, the given distance further from the Earth's centre.
function above(point, extraMetres) {
  const up = Cesium.Cartesian3.normalize(point, new Cesium.Cartesian3());
  return Cesium.Cartesian3.add(
    point,
    Cesium.Cartesian3.multiplyByScalar(up, extraMetres, new Cesium.Cartesian3()),
    new Cesium.Cartesian3(),
  );
}

export default function GlobeSection({ data, tracked, selectedEvent, onClearEvent }) {
  const containerRef = useRef(null);
  const viewerRef = useRef(null);
  const eventEntitiesRef = useRef([]);

  // The tick loop below reads the latest list through this ref, so a new list of positions
  // does not force the whole globe to be created again.
  const trackedRef = useRef(tracked);
  trackedRef.current = tracked;

  // Create the viewer once and move every object once per second.
  useEffect(() => {
    Cesium.Ion.defaultAccessToken = '';

    const viewer = new Cesium.Viewer(containerRef.current, {
      // The Natural Earth map ships with Cesium, so no account or token is needed.
      baseLayer: Cesium.ImageryLayer.fromProviderAsync(
        Cesium.TileMapServiceImageryProvider.fromUrl(
          Cesium.buildModuleUrl('Assets/Textures/NaturalEarthII'),
        ),
      ),
      baseLayerPicker: false,
      geocoder: false,
      homeButton: false,
      sceneModePicker: false,
      navigationHelpButton: false,
      animation: false,
      timeline: false,
      fullscreenButton: false,
      infoBox: false,
      selectionIndicator: false,
      // Draw only when something changed instead of 60 times a second.
      requestRenderMode: true,
      maximumRenderTimeChange: Infinity,
    });
    viewer.clock.shouldAnimate = false;
    viewerRef.current = viewer;

    const scene = viewer.scene;
    scene.globe.enableLighting = true;
    scene.globe.baseColor = Cesium.Color.fromCssColorString('#05070c');
    scene.backgroundColor = Cesium.Color.BLACK;
    scene.skyAtmosphere.brightnessShift = -0.2;
    viewer.imageryLayers.get(0).brightness = 0.75;

    // Mouse-wheel zoom would trap the page scroll, so it only works while Ctrl is held.
    scene.screenSpaceCameraController.enableZoom = false;
    const setZoom = (e) => {
      scene.screenSpaceCameraController.enableZoom = e.ctrlKey;
    };
    window.addEventListener('keydown', setZoom);
    window.addEventListener('keyup', setZoom);
    // Cesium blocks the page from scrolling while the pointer is over the globe, so plain
    // wheel events are stopped before they reach it. With Ctrl held they pass through to zoom.
    const container = containerRef.current;
    const letPageScroll = (e) => {
      if (!e.ctrlKey) e.stopPropagation();
    };
    container.addEventListener('wheel', letPageScroll, { capture: true });

    viewer.camera.setView({
      destination: Cesium.Cartesian3.fromDegrees(78, 18, 17_000_000),
    });

    // Cap the pixel density. The globe is drawn only when something changes (requestRenderMode
    // above), which off screen means about once a second, so it costs the page very little.
    // Switching the render loop off while the globe is out of view was tried and removed: the
    // Earth then took several seconds to appear when the user scrolled down to it.
    viewer.resolutionScale = Math.min(window.devicePixelRatio || 1, 1.5) / (window.devicePixelRatio || 1);

    // One point per object. Indian satellites are bigger and red.
    const points = scene.primitives.add(new Cesium.PointPrimitiveCollection());
    let items = [];
    let builtFor = null;

    function rebuildPoints() {
      points.removeAll();
      builtFor = trackedRef.current;
      items = builtFor.map((object) => ({
        object,
        point: points.add(
          object.indian
            ? {
                pixelSize: 7,
                color: INDIAN_COLOR,
                outlineColor: Cesium.Color.WHITE.withAlpha(0.8),
                outlineWidth: 1,
                position: Cesium.Cartesian3.ZERO,
              }
            : { pixelSize: 2.5, color: OTHER_COLOR, position: Cesium.Cartesian3.ZERO },
        ),
      }));
    }

    function tick() {
      if (builtFor !== trackedRef.current) rebuildPoints();
      const nowMs = Date.now();
      const matrix = fixedFrame(nowMs);
      items.forEach(({ object, point }) => {
        point.position = toCartesian(matrix, object.positionAt(nowMs));
      });
      viewer.clock.currentTime = Cesium.JulianDate.fromDate(new Date(nowMs));
      scene.requestRender();
    }
    tick();
    const timer = setInterval(tick, 1000);

    return () => {
      clearInterval(timer);
      container.removeEventListener('wheel', letPageScroll, { capture: true });
      window.removeEventListener('keydown', setZoom);
      window.removeEventListener('keyup', setZoom);
      viewer.destroy();
      viewerRef.current = null;
    };
  }, []);

  // Draw the two orbits and the closest-approach point whenever an event is selected.
  useEffect(() => {
    const viewer = viewerRef.current;
    if (!viewer) return;

    eventEntitiesRef.current.forEach((entity) => viewer.entities.remove(entity));
    eventEntitiesRef.current = [];
    if (!selectedEvent?.geometry) return;

    const { tcaMs, primaryRing, secondaryRing, meeting } = selectedEvent.geometry;
    // The rings are positions in a space-fixed frame. They are turned into the Earth-fixed
    // frame as it was at the moment of closest approach.
    const matrix = fixedFrame(tcaMs);
    const ring = (points, color) =>
      viewer.entities.add({
        polyline: {
          positions: points.map((p) => toCartesian(matrix, p)),
          width: 2,
          material: color,
        },
      });

    const meetingPoint = toCartesian(matrix, meeting);
    const marker = viewer.entities.add({
      position: meetingPoint,
      point: { pixelSize: 11, color: Cesium.Color.WHITE, outlineColor: INDIAN_COLOR, outlineWidth: 3 },
      label: {
        text: `${selectedEvent.missKm.toFixed(2)} km`,
        font: '13px JetBrains Mono, monospace',
        fillColor: Cesium.Color.WHITE,
        pixelOffset: new Cesium.Cartesian2(0, -22),
        showBackground: true,
        backgroundColor: Cesium.Color.BLACK.withAlpha(0.7),
      },
    });

    eventEntitiesRef.current = [ring(primaryRing, INDIAN_COLOR), ring(secondaryRing, SECONDARY_COLOR), marker];
    viewer.camera.flyTo({ destination: above(meetingPoint, 13_000_000), duration: 2 });
  }, [selectedEvent]);

  function flyToSatellite(sat) {
    const viewer = viewerRef.current;
    const object = trackedRef.current.find((t) => t.indian && t.id === sat.noradId);
    if (!viewer || !object) return;
    onClearEvent();
    const now = Date.now();
    const spot = toCartesian(fixedFrame(now), object.positionAt(now));
    viewer.camera.flyTo({ destination: above(spot, 4_000_000), duration: 2 });
  }

  const hasPosition = (sat) => tracked.some((t) => t.indian && t.id === sat.noradId);

  return (
    <section className="globe" id="globe">
      <div className="globe-canvas" ref={containerRef} />

      <div className="globe-overlay globe-head">
        <p className="eyebrow">Live orbit view</p>
        <UtcClock />
        <p className="muted">
          {tracked.length.toLocaleString()} objects shown
          {data.isDemo ? ' (demo sample)' : ''}. Hold Ctrl and scroll to zoom.
        </p>
        <ul className="legend">
          <li><i style={{ background: '#ff4b3e' }} /> Indian satellite</li>
          <li><i style={{ background: '#9aa4b5' }} /> Other object</li>
          <li><i style={{ background: '#39e1ff' }} /> Other object's orbit, in a selected event</li>
        </ul>
      </div>

      <aside className="globe-overlay globe-list">
        <p className="eyebrow">Watched satellites</p>
        <ul>
          {data.satellites.map((sat) => (
            <li key={sat.noradId}>
              <button onClick={() => flyToSatellite(sat)} disabled={!hasPosition(sat)}>
                <span>{sat.name}</span>
                <span className="mono muted">{sat.noradId}</span>
              </button>
            </li>
          ))}
        </ul>
      </aside>

      {selectedEvent && (
        <div className="globe-overlay globe-event">
          <p className="eyebrow">Selected event</p>
          <h3>
            {selectedEvent.primaryName} <span className="muted">and</span> {selectedEvent.secondaryName}
          </h3>
          <ul className="legend event-legend">
            <li><i style={{ background: '#ff4b3e' }} /> Red line: orbit of {selectedEvent.primaryName}</li>
            <li><i style={{ background: '#39e1ff' }} /> Blue line: orbit of {selectedEvent.secondaryName}</li>
            <li><i style={{ background: '#ffffff' }} /> White dot: where the two come closest</li>
          </ul>
          <p className="muted event-note">
            The orbits only cross here. They are dangerous only if both objects reach the crossing
            at the same time, and the miss distance below says how close they really get.
          </p>
          <dl>
            {selectedEvent.secondaryOwner && (
              <div><dt>Other object</dt><dd>{selectedEvent.secondaryOwner}, {selectedEvent.secondaryKind?.toLowerCase()}</dd></div>
            )}
            <div><dt>NORAD number</dt><dd>{selectedEvent.secondaryId}</dd></div>
            <div><dt>Miss distance</dt><dd>{selectedEvent.missKm.toFixed(2)} km</dd></div>
            <div><dt>Relative speed</dt><dd>{selectedEvent.relVelKmS.toFixed(1)} km/s</dd></div>
            <div><dt>Closest approach</dt><dd>{selectedEvent.tca.slice(0, 16).replace('T', ' ')} UTC</dd></div>
            {selectedEvent.timesSeen > 1 && selectedEvent.previousMissKm != null && (
              <div><dt>Earlier prediction</dt><dd>{selectedEvent.previousMissKm.toFixed(2)} km</dd></div>
            )}
          </dl>
          <MissTrend event={selectedEvent} isDemo={Boolean(data.isDemo)} />
          <button className="link-button" onClick={onClearEvent}>Clear selection</button>
        </div>
      )}
    </section>
  );
}

function UtcClock() {
  const [now, setNow] = useState(new Date());
  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(timer);
  }, []);
  return <h2>{now.toISOString().slice(11, 19)} UTC</h2>;
}
