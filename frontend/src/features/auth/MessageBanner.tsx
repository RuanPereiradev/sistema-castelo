import type { AuthMessage } from './authMessages';
import { AlertIcon, HistoryIcon, HourglassIcon } from './icons';

const ICONS = {
  alert: AlertIcon,
  wait: HourglassIcon,
  info: HistoryIcon,
} as const;

/**
 * `key` is the message code, so a different message replays the 360 ms reveal
 * and the same message standing still does not blink.
 */
export function MessageBanner({ message }: { message: AuthMessage }) {
  const Icon = ICONS[message.icon];
  return (
    <div className="login-message" role="alert" key={message.code}>
      <span className="login-message-icon">
        <Icon />
      </span>
      <div className="login-message-body">
        <div className="login-message-kicker">{message.kicker}</div>
        <div className="login-message-text">{message.text}</div>
      </div>
    </div>
  );
}
