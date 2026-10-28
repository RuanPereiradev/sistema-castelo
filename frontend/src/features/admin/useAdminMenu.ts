import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';

export interface MenuItemVariant {
  readonly id: string;
  readonly name: string;
  readonly price: string | null;
  readonly isActive: boolean;
  readonly isAvailable: boolean;
}

export interface MenuItemModifierOffer {
  readonly modifierId: string;
  readonly modifierName: string;
  readonly maxQuantity: number;
}

export interface AvailabilityWindow {
  readonly dayOfWeek: string | null;
  readonly startTime: string;
  readonly endTime: string;
}

export interface MenuItem {
  readonly id: string;
  readonly categoryId: string;
  readonly name: string;
  readonly description: string | null;
  readonly price: string;
  readonly prepStation: string;
  readonly isActive: boolean;
  readonly isAvailable: boolean;
  readonly soldByWeight: boolean;
  readonly pricePerKilo: string | null;
  readonly serviceChargeEligible: boolean;
  readonly variants: readonly MenuItemVariant[];
  readonly modifiers: readonly MenuItemModifierOffer[];
  readonly availabilityWindows: readonly AvailabilityWindow[];
}

export interface MenuCategory {
  readonly id: string;
  readonly name: string;
  readonly displayOrder: number;
}

export const MENU_ITEMS_KEY = ['menu-items'] as const;
export const MENU_CATEGORIES_KEY = ['menu-categories'] as const;

export function useMenuCategories() {
  const apiCall = useAuthorizedRequest();

  return useQuery({
    queryKey: MENU_CATEGORIES_KEY,
    queryFn: () => apiCall<readonly MenuCategory[]>('/restaurant/menu-categories'),
    staleTime: 30000,
  });
}

export function useMenuItems() {
  const apiCall = useAuthorizedRequest();

  return useQuery({
    queryKey: MENU_ITEMS_KEY,
    queryFn: () => apiCall<readonly MenuItem[]>('/restaurant/menu-items'),
    staleTime: 30000,
  });
}

export function useMenuItemDetail(itemId: string) {
  const apiCall = useAuthorizedRequest();

  return useQuery({
    queryKey: [MENU_ITEMS_KEY, itemId],
    queryFn: () => apiCall<MenuItem>(`/restaurant/menu-items/${itemId}`),
    enabled: !!itemId,
    staleTime: 30000,
  });
}

export interface CreateMenuItemRequest {
  readonly categoryId: string;
  readonly name: string;
  readonly description?: string | null;
  readonly prepStation: string;
  readonly isSoldByWeight: boolean;
  readonly price: string;
  readonly serviceChargeEligible: boolean;
}

export function useCreateMenuItem() {
  const apiCall = useAuthorizedRequest();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: CreateMenuItemRequest) =>
      apiCall<MenuItem>('/restaurant/menu-items', {
        method: 'POST',
        body: request,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: MENU_ITEMS_KEY });
    },
  });
}

export interface UpdateMenuItemRequest {
  readonly price?: string;
}

export function useUpdateMenuItem(itemId: string) {
  const apiCall = useAuthorizedRequest();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: UpdateMenuItemRequest) =>
      apiCall<MenuItem>(`/restaurant/menu-items/${itemId}/price`, {
        method: 'PATCH',
        body: request,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: MENU_ITEMS_KEY });
      queryClient.invalidateQueries({ queryKey: [MENU_ITEMS_KEY, itemId] });
    },
  });
}

export function useMarkItemUnavailable(itemId: string) {
  const apiCall = useAuthorizedRequest();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () =>
      apiCall<MenuItem>(`/restaurant/menu-items/${itemId}/unavailable`, {
        method: 'POST',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: MENU_ITEMS_KEY });
      queryClient.invalidateQueries({ queryKey: [MENU_ITEMS_KEY, itemId] });
    },
  });
}

export function useMarkItemAvailable(itemId: string) {
  const apiCall = useAuthorizedRequest();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () =>
      apiCall<MenuItem>(`/restaurant/menu-items/${itemId}/available`, {
        method: 'POST',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: MENU_ITEMS_KEY });
      queryClient.invalidateQueries({ queryKey: [MENU_ITEMS_KEY, itemId] });
    },
  });
}

export interface CreateMenuCategoryRequest {
  readonly name: string;
  readonly displayOrder: number;
}

export function useCreateMenuCategory() {
  const apiCall = useAuthorizedRequest();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: CreateMenuCategoryRequest) =>
      apiCall<MenuCategory>('/restaurant/menu-categories', {
        method: 'POST',
        body: request,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: MENU_CATEGORIES_KEY });
    },
  });
}
