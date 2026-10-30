import { APP_NAME } from '../config';
import Logo from './Logo';

export default function Footer() {
  return (
    <footer className="footer" id="about">
      <div className="footer-top">
        <div className="footer-brand">
          <Logo size={34} />
          <span>{APP_NAME}</span>
        </div>
        <p className="footer-note">
          Orbit data comes from CelesTrak and is refreshed every six hours. Predictions use public
          element sets, which are good to roughly a kilometre or worse, and carry no uncertainty
          estimate. They are a screening aid, not a flight-safety tool.
        </p>
      </div>
      <div className="footer-giant" aria-hidden="true">{APP_NAME.toLowerCase()}</div>
      <p className="footer-bottom">
        <span>{new Date().getFullYear()} {APP_NAME}</span>
        <span>Orbital data: CelesTrak</span>
      </p>
    </footer>
  );
}
