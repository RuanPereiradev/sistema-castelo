import type { LoginScreen } from './useLogin';

/**
 * `POST /api/auth/logout` ends every session of the user, so the confirmation
 * has to say so. It does not say how many fell: the API does not count them.
 */
export function LeaveDialog({ screen }: { screen: LoginScreen }) {
  if (screen.stage.kind !== 'leaving') {
    return null;
  }
  const { username } = screen.stage.session.user;

  return (
    <div className="card elev-lg login-dialog" role="dialog" aria-modal="true">
      <div className="login-kicker">❧ Sair</div>
      <h2 className="login-dialog-title">Sair em todos os aparelhos?</h2>
      <p className="login-dialog-text">
        A sessão de <strong>{username}</strong> será encerrada aqui e em todo aparelho onde
        estiver aberta — o tablet do balcão, a tela da cozinha. Quem estiver usando lá precisará
        entrar de novo.
      </p>
      <div className="login-dialog-actions">
        <button type="button" className="btn btn-secondary" onClick={screen.stay}>
          Ficar
        </button>
        <button
          type="button"
          className="btn btn-primary"
          onClick={screen.confirmLeave}
          disabled={screen.submitting}
        >
          {screen.submitting ? 'Saindo…' : 'Sair de todos'}
        </button>
      </div>
    </div>
  );
}
