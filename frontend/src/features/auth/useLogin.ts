import { useCallback, useMemo, useRef, useState } from 'react';
import { ApiError } from '../../lib/problemDetail';
import * as authApi from './authApi';
import {
  failureMessage,
  loggedOutMessage,
  sessionEndedMessage,
  type AuthMessage,
} from './authMessages';
import { destinationsFor, type Destination } from './destinations';
import { takeSessionEnd } from './sessionEnd';
import {
  clearSession,
  lastDestination,
  lastUsername,
  rememberDestination,
  storeSession,
  type Session,
} from './session';

/**
 * One screen, four stages. `form` covers every variant the operator still has
 * to type in — first try, wrong password, rate limited, session ended — because
 * they differ only by the message above the fields.
 */
export type Stage =
  | { readonly kind: 'form' }
  | { readonly kind: 'choosing'; readonly session: Session }
  | { readonly kind: 'inside'; readonly session: Session; readonly destination: Destination }
  | { readonly kind: 'leaving'; readonly session: Session; readonly destination: Destination };

export interface LoginScreen {
  readonly stage: Stage;
  readonly username: string;
  readonly password: string;
  readonly passwordVisible: boolean;
  readonly submitting: boolean;
  readonly message: AuthMessage | null;
  readonly destinations: readonly { destination: Destination; isLast: boolean }[];
  readonly setUsername: (value: string) => void;
  readonly setPassword: (value: string) => void;
  readonly togglePasswordVisible: () => void;
  readonly submit: () => void;
  readonly chooseDestination: (destination: Destination) => void;
  readonly askToLeave: () => void;
  readonly stay: () => void;
  readonly confirmLeave: () => void;
  readonly backToLogin: () => void;
}

/** The field keeps focus logic out of the components: 401 sends it to the password. */
export function useLogin(passwordField: React.RefObject<HTMLInputElement>): LoginScreen {
  const endReason = useRef<string | null>(takeSessionEnd());

  const [stage, setStage] = useState<Stage>({ kind: 'form' });
  const [username, setUsername] = useState(() => (endReason.current ? lastUsername() : ''));
  const [password, setPassword] = useState('');
  const [passwordVisible, setPasswordVisible] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState<AuthMessage | null>(() =>
    endReason.current ? sessionEndedMessage(endReason.current) : null,
  );

  /** A single reachable area skips the question entirely. */
  const enter = useCallback((session: Session) => {
    const reachable = destinationsFor(session.user.roles);
    const only = reachable.length === 1 ? reachable[0] : undefined;
    setPassword('');
    setStage(only ? { kind: 'inside', session, destination: only } : { kind: 'choosing', session });
  }, []);

  const submit = useCallback(() => {
    const trimmed = username.trim().toLowerCase();
    if (submitting || !trimmed || !password) {
      return;
    }
    setSubmitting(true);
    authApi
      .login(trimmed, password)
      .then((response) => {
        setMessage(null);
        enter(storeSession(response));
      })
      .catch((error: unknown) => {
        setMessage(failureMessage(error instanceof ApiError ? error.code : null));
        // The username stays: it is almost never the wrong half.
        setPassword('');
        passwordField.current?.focus({ preventScroll: true });
      })
      .finally(() => setSubmitting(false));
  }, [enter, password, passwordField, submitting, username]);

  const chooseDestination = useCallback(
    (destination: Destination) => {
      if (stage.kind !== 'choosing') {
        return;
      }
      rememberDestination(destination);
      setStage({ kind: 'inside', session: stage.session, destination });
    },
    [stage],
  );

  const backToLogin = useCallback(() => {
    clearSession();
    setStage({ kind: 'form' });
    setPassword('');
    setMessage(null);
  }, []);

  const confirmLeave = useCallback(() => {
    if (stage.kind !== 'leaving') {
      return;
    }
    const who = stage.session.user.username;
    const token = stage.session.accessToken;
    setSubmitting(true);
    authApi
      .logout(token)
      .then(() => setMessage(loggedOutMessage(who)))
      .catch((error: unknown) => setMessage(failureMessage(error instanceof ApiError ? error.code : null)))
      .finally(() => {
        setSubmitting(false);
        clearSession();
        setStage({ kind: 'form' });
        setUsername(who);
        setPassword('');
      });
  }, [stage]);

  const destinations = useMemo(() => {
    if (stage.kind !== 'choosing') {
      return [];
    }
    const last = lastDestination();
    return destinationsFor(stage.session.user.roles).map((destination) => ({
      destination,
      isLast: destination === last,
    }));
  }, [stage]);

  return {
    stage,
    username,
    password,
    passwordVisible,
    submitting,
    message,
    destinations,
    setUsername,
    setPassword,
    togglePasswordVisible: () => setPasswordVisible((visible) => !visible),
    submit,
    chooseDestination,
    askToLeave: () =>
      setStage((current) =>
        current.kind === 'inside'
          ? { kind: 'leaving', session: current.session, destination: current.destination }
          : current,
      ),
    stay: () =>
      setStage((current) =>
        current.kind === 'leaving'
          ? { kind: 'inside', session: current.session, destination: current.destination }
          : current,
      ),
    confirmLeave,
    backToLogin,
  };
}
