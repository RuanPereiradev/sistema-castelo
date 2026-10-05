/**
 * Error code → what the operator reads. The message says what to do, not what
 * went wrong in the server: 401 asks to check both fields, 429 asks to wait and
 * warns that the right password will not get in either, so nobody "fixes" it by
 * changing the password.
 */
export type MessageIcon = 'alert' | 'wait' | 'info';

export interface AuthMessage {
  readonly code: string;
  readonly kicker: string;
  readonly text: string;
  readonly icon: MessageIcon;
}

/** Why the user was sent back to the login screen. */
const SESSION_ENDED: Readonly<Record<string, string>> = {
  TOKEN_EXPIRED: 'Sua sessão expirou. Entre de novo para continuar de onde parou.',
  INVALID_TOKEN: 'Sua sessão deixou de valer. Entre de novo para continuar.',
  USER_INACTIVE:
    'Seu acesso foi desativado. Para voltar a entrar, fale com o administrador da casa.',
  SESSION_SUPERSEDED:
    'Sua sessão foi encerrada porque alguém saiu com este usuário em outro aparelho. Entre de novo.',
};

export function sessionEndedMessage(code: string): AuthMessage {
  return {
    code: `session:${code}`,
    kicker: 'Sessão encerrada',
    text: SESSION_ENDED[code] ?? SESSION_ENDED['TOKEN_EXPIRED']!,
    icon: 'info',
  };
}

export function loggedOutMessage(username: string): AuthMessage {
  return {
    code: 'loggedOut',
    kicker: 'Você saiu',
    text: `As sessões de ${username} foram encerradas neste e nos outros aparelhos.`,
    icon: 'info',
  };
}

/**
 * The code comes from the `code` field of the problem+json body. A failure that
 * never reached the server arrives here as `null`.
 */
export function failureMessage(code: string | null): AuthMessage {
  switch (code) {
    case 'INVALID_CREDENTIALS':
      return {
        code: 'INVALID_CREDENTIALS',
        kicker: 'Não foi possível entrar',
        text: 'Usuário ou senha não conferem. Confira os dois e tente de novo.',
        icon: 'alert',
      };
    case 'TOO_MANY_LOGIN_ATTEMPTS':
      return {
        code: 'TOO_MANY_LOGIN_ATTEMPTS',
        kicker: 'Aguarde cerca de um minuto',
        text: 'Muitas tentativas seguidas. Até lá, nem a senha certa entra — não a troque.',
        icon: 'wait',
      };
    case 'USER_INACTIVE':
      return {
        code: 'USER_INACTIVE',
        kicker: 'Acesso desativado',
        text: SESSION_ENDED['USER_INACTIVE']!,
        icon: 'alert',
      };
    default:
      return {
        code: code ?? 'UNREACHABLE',
        kicker: 'Algo deu errado',
        text: 'Não foi possível enviar o pedido de entrada. Tente de novo; se continuar, avise o administrador.',
        icon: 'alert',
      };
  }
}
