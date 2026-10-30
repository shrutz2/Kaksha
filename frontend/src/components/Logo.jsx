// A small orbit mark: a ring with a satellite dot on it.
export default function Logo({ size = 30 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" fill="none" aria-hidden="true">
      <ellipse cx="16" cy="16" rx="14" ry="6" transform="rotate(-30 16 16)" stroke="currentColor" strokeWidth="3" />
      <circle cx="16" cy="16" r="5.5" fill="currentColor" />
      <circle cx="26.5" cy="9.7" r="2.6" fill="currentColor" />
    </svg>
  );
}
