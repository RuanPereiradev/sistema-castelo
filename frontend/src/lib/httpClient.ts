import { ApiError, NetworkError, type ProblemDetail } from './problemDetail';

/**
 * Same-origin by design: the Vite dev server proxies `/api` to the backend, and
 * in production the bundle is served next to it.
 */
const BASE_PATH = '/api';

export interface RequestOptions {
  readonly method?: 'GET' | 'POST';
  readonly body?: unknown;
  readonly accessToken?: string;
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

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
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
