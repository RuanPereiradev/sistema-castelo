import { request } from '../../lib/httpClient';
import type { Role } from './roles';

export interface UserSummary {
  readonly id: string;
  readonly username: string;
  readonly fullName: string;
  readonly roles: readonly Role[];
}

export interface LoginResponse {
  readonly accessToken: string;
  readonly refreshToken: string;
  /** Seconds, not milliseconds — the backend answers 900. */
  readonly expiresIn: number;
  readonly user: UserSummary;
}

export function login(username: string, password: string): Promise<LoginResponse> {
  return request<LoginResponse>('/auth/login', {
    method: 'POST',
    body: { username, password },
    skipAuth: true,
  });
}

export interface RefreshResponse {
  readonly accessToken: string;
  readonly expiresIn: number;
}

export function refresh(refreshToken: string): Promise<RefreshResponse> {
  return request<RefreshResponse>('/auth/refresh', {
    method: 'POST',
    body: { refreshToken },
    skipAuth: true,
  });
}

/** Answers 204. Ends every session of the user, on every device. */
export function logout(accessToken: string): Promise<void> {
  return request<void>('/auth/logout', { method: 'POST', accessToken });
}
