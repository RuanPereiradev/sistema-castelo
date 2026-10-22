import { ApiError, NetworkError, type ProblemDetail } from './problemDetail';

/**
 * Requests to the public, login-free routes under `/public` (the customer at the
 * table). No token, no refresh: the caller may add its own headers, such as
 * `X-Customer-Device`. Failures still become `ApiError` with the stable `code`,
 * or `NetworkError` when the request never reached the server.
 */
export interface PublicRequestOptions {
  readonly method?: 'GET' | 'POST';
  readonly body?: unknown;
  readonly headers?: Readonly<Record<string, string>>;
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

export async function publicRequest<T>(path: string, options: PublicRequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json', ...options.headers };
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  let response: Response;
  try {
    response = await fetch(`/public${path}`, {
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
