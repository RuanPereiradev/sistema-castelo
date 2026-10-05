import { useId } from 'react';
import type { LoginScreen } from './useLogin';
import { MessageBanner } from './MessageBanner';
import { EyeIcon, EyeOffIcon } from './icons';

interface LoginFormProps {
  readonly screen: LoginScreen;
  readonly passwordField: React.RefObject<HTMLInputElement>;
}

/**
 * One door only: no sign-up, no "forgot my password". The footnote points at
 * the administrator and links nowhere, because there is nowhere to go.
 */
export function LoginForm({ screen, passwordField }: LoginFormProps) {
  const usernameId = useId();
  const passwordId = useId();
  const returning = screen.message?.code.startsWith('session:') ?? false;
  // The promise of a filled-in username is only made when there is one: a
  // device that has never been used has nothing to remember.
  const prefilled = returning && screen.username !== '';

  return (
    <>
      <div className="login-heading">
        <h1 className="login-title">{returning ? 'Entre de novo' : 'Identifique-se'}</h1>
        <p className="login-subtitle">
          {prefilled
            ? 'Seu usuário já está preenchido; falta só a senha.'
            : 'Use o usuário e a senha que o administrador lhe entregou.'}
        </p>
      </div>

      {screen.message ? <MessageBanner message={screen.message} /> : null}

      <form
        className="login-form"
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          screen.submit();
        }}
      >
        <div className="field">
          <label htmlFor={usernameId}>Usuário</label>
          <input
            id={usernameId}
            className="input login-input"
            type="text"
            name="username"
            value={screen.username}
            onChange={(event) => screen.setUsername(event.target.value)}
            autoComplete="username"
            autoCapitalize="none"
            autoCorrect="off"
            spellCheck={false}
            enterKeyHint="next"
            placeholder="ex.: garcom"
          />
        </div>

        <div className="field">
          <label htmlFor={passwordId}>Senha</label>
          <div className="login-password">
            <input
              id={passwordId}
              ref={passwordField}
              className="input login-input"
              type={screen.passwordVisible ? 'text' : 'password'}
              name="password"
              value={screen.password}
              onChange={(event) => screen.setPassword(event.target.value)}
              autoComplete="current-password"
              autoCapitalize="none"
              autoCorrect="off"
              spellCheck={false}
              enterKeyHint="go"
            />
            <button
              type="button"
              className="btn btn-ghost btn-icon login-password-toggle"
              onClick={screen.togglePasswordVisible}
              aria-label={screen.passwordVisible ? 'Ocultar senha' : 'Mostrar senha'}
            >
              {screen.passwordVisible ? <EyeOffIcon /> : <EyeIcon />}
            </button>
          </div>
        </div>

        <button
          type="submit"
          className="btn btn-primary btn-block login-submit"
          disabled={screen.submitting || !screen.username.trim() || !screen.password}
        >
          {screen.submitting ? 'Verificando…' : 'Entrar'}
        </button>
      </form>

      <div className="login-footnote">
        Perdeu a senha ou o acesso? Fale com o administrador da casa — só ele cria usuários e
        define senhas.
      </div>
    </>
  );
}
