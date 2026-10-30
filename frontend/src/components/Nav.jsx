import { useEffect, useState } from 'react';
import { APP_NAME } from '../config';
import Logo from './Logo';

const links = [
  ['Live orbits', '#globe'],
  ['Events', '#events'],
  ['Pipeline', '#pipeline'],
  ['About', '#about'],
];

export default function Nav() {
  // Once the poster has scrolled away the bar gets a dark backdrop so it stays readable.
  const [solid, setSolid] = useState(false);

  useEffect(() => {
    const onScroll = () => setSolid(window.scrollY > window.innerHeight * 0.9);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  return (
    <header className={solid ? 'nav nav-solid' : 'nav'}>
      <a className="nav-brand" href="#top" aria-label={APP_NAME}>
        <Logo />
      </a>
      <nav className="nav-links">
        {links.map(([label, href]) => (
          <a key={href} href={href}>
            {label}
          </a>
        ))}
      </nav>
    </header>
  );
}
