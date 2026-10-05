import { useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { request, type RequestOptions } from './httpClient';
import { recordSessionEnd } from '../features/auth/sessionEnd';

/**
 * Hook que encapsula requisições autenticadas e trata erros de sessão.
 * Em 401, registra o motivo e redireciona pra login.
 */
export function useAuthorizedRequest() {
  const navigate = useNavigate();

  const apiCall = useCallback(
    async function <T>(path: string, options?: RequestOptions): Promise<T> {
      try {
        return await request<T>(path, options);
      } catch (error: unknown) {
        if (error instanceof Error && 'status' in error && error.status === 401) {
          const code = 'code' in error ? (error.code as string) : 'INVALID_TOKEN';
          recordSessionEnd(code);
          navigate('/?sessionEnd=' + encodeURIComponent(code));
          throw error;
        }
        throw error;
      }
    },
    [navigate],
  );

  return apiCall;
}
