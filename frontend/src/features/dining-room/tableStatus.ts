import type { TabSummary } from './useActiveTabs';
import { DINING_ROOM } from './diningRoomMessages';

/**
 * What the map can tell from the API today. "Reserved" and "dish ready" exist in
 * the design, but neither the table nor the active-tab list carries them, so
 * they are not derived here: a status the screen invents is a status that lies.
 */
export type TableStatus = 'free' | 'occupied' | 'billRequested';

export const TABLE_STATUSES: readonly TableStatus[] = ['billRequested', 'occupied', 'free'];

export function statusOf(tab: TabSummary | undefined): TableStatus {
  if (!tab) {
    return 'free';
  }
  return tab.status === 'CLOSING' ? 'billRequested' : 'occupied';
}

export function statusLabel(status: TableStatus): string {
  return DINING_ROOM.statusLabel[status];
}

/** CSS modifier of `.dr-table-btn` and `.dr-chip-indicator`. */
export function statusClass(status: TableStatus): string {
  return status === 'billRequested' ? 'bill-requested' : status;
}

/**
 * Whole minutes since an ISO instant, by the device clock. Good enough for
 * "open for 42 min" on a phone that syncs its clock; the kitchen screen is the
 * one that must compare with the server's time, and it does not use this.
 */
export function minutesSince(isoInstant: string, now: number): number {
  return Math.max(0, Math.floor((now - Date.parse(isoInstant)) / 60_000));
}
