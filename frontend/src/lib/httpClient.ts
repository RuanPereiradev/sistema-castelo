import { ApiError, NetworkError, type ProblemDetail } from './problemDetail';
import { refresh } from '../features/auth/authApi';
import { currentSession, clearSession } from '../features/auth/session';

/**
 * Same-origin by design: the Vite dev server proxies `/api` to the backend, and
 * in production the bundle is served next to it.
 */
const BASE_PATH = '/api';

export interface RequestOptions {
  readonly method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  readonly body?: unknown;
  readonly accessToken?: string;
  readonly skipRefresh?: boolean;
  readonly skipAuth?: boolean;
}

async function readCode(response: Response): Promise<string | null> {
  try {
    const problem = (await response.json()) as ProblemDetail;
    return problem.code ?? null;
  } catch {
    // A proxy or a container that died answers HTML, not problem+json.
    return null;
  }
}

async function makeRequest<T>(
  path: string,
  options: RequestOptions & { readonly accessToken?: string },
): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (options.accessToken) {
    headers['Authorization'] = `Bearer ${options.accessToken}`;
  }

  let response: Response;
  try {
    response = await fetch(`${BASE_PATH}${path}`, {
      method: options.method ?? 'GET',
      headers,
      ...(options.body === undefined ? {} : { body: JSON.stringify(options.body) }),
    });
  } catch (cause) {
    throw new NetworkError(cause);
  }

  if (!response.ok) {
    throw new ApiError(response.status, await readCode(response));
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  // Requisições públicas (login) não precisam de token
  if (options.skipAuth) {
    return makeRequest<T>(path, options);
  }

  const session = currentSession();
  const token = options.accessToken ?? session?.accessToken;

  if (!token) {
    throw new ApiError(401, 'INVALID_TOKEN');
  }

  try {
    return await makeRequest<T>(path, { ...options, accessToken: token });
  } catch (error) {
    if (
      error instanceof ApiError &&
      error.status === 401 &&
      !options.skipRefresh &&
      session?.refreshToken
    ) {
      try {
        const refreshed = await refresh(session.refreshToken);
        // Retry com novo token
        return await makeRequest<T>(path, { ...options, accessToken: refreshed.accessToken });
      } catch {
        // Refresh falhou; sessão encerrou mesmo
        clearSession();
        throw error;
      }
    }
    throw error;
  }
}
