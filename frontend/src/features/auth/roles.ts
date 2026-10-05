/** Mirrors `br.com.castel.identity.api.Role`. */
export const ROLES = ['ADMIN', 'FRONT_DESK', 'WAITER', 'KITCHEN'] as const;

export type Role = (typeof ROLES)[number];
