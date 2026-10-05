/**
 * The backend answers every failure as RFC 7807 `application/problem+json`
 * carrying a stable `code`. The code is the contract: the screen translates it
 * into Portuguese, never the server.
 */
export interface ProblemDetail {
  readonly type?: string;
  readonly title?: string;
  readonly status?: number;
  readonly detail?: string;
  readonly code?: string;
}

/** Raised for anything the server answered; carries the code when there is one. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string | null;

  constructor(status: number, code: string | null) {
    super(`API answered ${status}${code ? ` (${code})` : ''}`);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

/** Raised when the request never reached the server: offline, DNS, timeout. */
export class NetworkError extends Error {
  constructor(cause: unknown) {
    super('the request did not reach the server', { cause });
    this.name = 'NetworkError';
  }
}
