import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';

export interface AdminDiningTable {
  readonly id: string;
  readonly label: string;
  readonly seats: number | null;
  readonly area: string | null;
  readonly isActive: boolean;
}

export const ADMIN_DINING_TABLES_KEY = ['admin-dining-tables'] as const;

export function useAdminDiningTables() {
  const apiCall = useAuthorizedRequest();

  return useQuery({
    queryKey: ADMIN_DINING_TABLES_KEY,
    queryFn: () => apiCall<readonly AdminDiningTable[]>('/restaurant/dining-tables'),
    staleTime: 30000,
  });
}

export interface CreateDiningTableRequest {
  readonly label: string;
  readonly seats?: number | null;
  readonly area?: string | null;
}

export function useCreateDiningTable() {
  const apiCall = useAuthorizedRequest();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: CreateDiningTableRequest) =>
      apiCall<AdminDiningTable>('/restaurant/dining-tables', {
        method: 'POST',
        body: request,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ADMIN_DINING_TABLES_KEY });
    },
  });
}

export interface UpdateDiningTableRequest {
  readonly label: string;
  readonly seats?: number | null;
  readonly area?: string | null;
}

export function useUpdateDiningTable(tableId: string) {
  const apiCall = useAuthorizedRequest();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: UpdateDiningTableRequest) =>
      apiCall<AdminDiningTable>(`/restaurant/dining-tables/${tableId}`, {
        method: 'PUT',
        body: request,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ADMIN_DINING_TABLES_KEY });
    },
  });
}

export function useDeactivateDiningTable(tableId: string) {
  const apiCall = useAuthorizedRequest();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () =>
      apiCall<AdminDiningTable>(`/restaurant/dining-tables/${tableId}/deactivate`, {
        method: 'POST',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ADMIN_DINING_TABLES_KEY });
    },
  });
}

export function useActivateDiningTable(tableId: string) {
  const apiCall = useAuthorizedRequest();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () =>
      apiCall<AdminDiningTable>(`/restaurant/dining-tables/${tableId}/activate`, {
        method: 'POST',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ADMIN_DINING_TABLES_KEY });
    },
  });
}
