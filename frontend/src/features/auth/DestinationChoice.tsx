import { DESTINATIONS } from './destinations';
import { ChevronRightIcon } from './icons';
import type { LoginScreen } from './useLogin';

/**
 * Only shown when the profile reaches more than one area. The last choice made
 * on this device is marked, not preselected: marking informs, preselecting
 * decides for the operator.
 */
export function DestinationChoice({ screen }: { screen: LoginScreen }) {
  if (screen.stage.kind !== 'choosing') {
    return null;
  }
  const { user } = screen.stage.session;

  return (
    <>
      <div className="login-heading">
        <div className="login-kicker">Bem-vindo, {user.fullName}</div>
        <h1 className="login-title">Onde deseja entrar?</h1>
        <p className="login-subtitle">
          Seu perfil alcança mais de uma área. Escolha por onde começar.
        </p>
      </div>

      <div className="login-destinations">
        {screen.destinations.map(({ destination, isLast }) => (
          <button
            key={destination}
            type="button"
            className="btn btn-secondary login-destination"
            onClick={() => screen.chooseDestination(destination)}
          >
            <span className="login-destination-label">
              <span className="login-destination-title">
                {DESTINATIONS[destination].title}
                {isLast ? <span className="tag tag-outline">última vez</span> : null}
              </span>
              <span className="login-destination-desc">
                {DESTINATIONS[destination].description}
              </span>
            </span>
            <ChevronRightIcon />
          </button>
        ))}
      </div>

      <button type="button" className="btn btn-ghost login-back" onClick={screen.backToLogin}>
        Não sou {user.username} — voltar
      </button>
    </>
  );
}
