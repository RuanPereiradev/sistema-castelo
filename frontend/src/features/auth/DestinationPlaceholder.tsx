import { DESTINATIONS } from './destinations';
import type { LoginScreen } from './useLogin';

/**
 * The marker that stands where the area itself will be. It exists so the two
 * things that follow the login — leaving, and coming back — can be exercised;
 * the rooms arrive with their own tasks.
 */
export function DestinationPlaceholder({ screen }: { screen: LoginScreen }) {
  if (screen.stage.kind !== 'inside') {
    return null;
  }
  const { session, destination } = screen.stage;

  return (
    <>
      <div className="login-heading">
        <div className="login-kicker">Bem-vindo, {session.user.fullName}</div>
        <h1 className="login-title">{DESTINATIONS[destination].title}</h1>
        <p className="login-subtitle">Aqui o sistema abre essa área.</p>
      </div>
      <div className="login-actions">
        <button
          type="button"
          className="btn btn-secondary login-action"
          onClick={screen.askToLeave}
        >
          Sair
        </button>
        <button type="button" className="btn btn-ghost login-back" onClick={screen.backToLogin}>
          Voltar ao login
        </button>
      </div>
    </>
  );
}
