import type { PublicMenuItem, PublicMenuModifier, PublicMenuVariant, SubmitTableOrderRequest } from './customerApi';

/**
 * The cart lives only on this phone until it is sent (decision #8). It keeps the
 * menu entries it was built from, so a line shows names and unit prices without
 * reading the menu again, and it never adds money: no line total, no total.
 */
export interface CartModifier {
  readonly modifier: PublicMenuModifier;
  readonly quantity: number;
}

export interface CartLine {
  /** Same item, variant and modifiers make the same line. */
  readonly key: string;
  readonly item: PublicMenuItem;
  readonly variant: PublicMenuVariant | null;
  readonly modifiers: readonly CartModifier[];
  readonly quantity: number;
  readonly specialInstructions: string;
}

export type CartAction =
  | {
      readonly type: 'add';
      readonly item: PublicMenuItem;
      readonly variant: PublicMenuVariant | null;
      readonly modifiers: readonly CartModifier[];
      readonly quantity: number;
    }
  | { readonly type: 'change-quantity'; readonly key: string; readonly delta: number }
  | { readonly type: 'remove'; readonly key: string }
  | { readonly type: 'note'; readonly key: string; readonly specialInstructions: string }
  | { readonly type: 'clear' };

/** The backend refuses longer observations at confirmation. */
export const MAXIMUM_SPECIAL_INSTRUCTIONS_LENGTH = 200;

function lineKey(item: PublicMenuItem, variant: PublicMenuVariant | null, modifiers: readonly CartModifier[]) {
  const chosen = modifiers
    .filter((choice) => choice.quantity > 0)
    .map((choice) => `${choice.modifier.id}x${choice.quantity}`)
    .sort()
    .join(',');
  return `${item.id}|${variant?.id ?? ''}|${chosen}`;
}

export function cartReducer(lines: readonly CartLine[], action: CartAction): readonly CartLine[] {
  switch (action.type) {
    case 'add': {
      const modifiers = action.modifiers.filter((choice) => choice.quantity > 0);
      const key = lineKey(action.item, action.variant, modifiers);
      if (lines.some((line) => line.key === key)) {
        return lines.map((line) => (line.key === key ? { ...line, quantity: line.quantity + action.quantity } : line));
      }
      return [
        ...lines,
        { key, item: action.item, variant: action.variant, modifiers, quantity: action.quantity, specialInstructions: '' },
      ];
    }
    case 'change-quantity':
      return lines
        .map((line) => (line.key === action.key ? { ...line, quantity: line.quantity + action.delta } : line))
        .filter((line) => line.quantity > 0);
    case 'remove':
      return lines.filter((line) => line.key !== action.key);
    case 'note':
      return lines.map((line) =>
        line.key === action.key
          ? { ...line, specialInstructions: action.specialInstructions.slice(0, MAXIMUM_SPECIAL_INSTRUCTIONS_LENGTH) }
          : line,
      );
    case 'clear':
      return [];
  }
}

/** Units in the cart, for the "Ver pedido" bar. A count, not money. */
export function cartUnitCount(lines: readonly CartLine[]): number {
  return lines.reduce((count, line) => count + line.quantity, 0);
}

export function unitsOfItem(lines: readonly CartLine[], itemId: string): number {
  return cartUnitCount(lines.filter((line) => line.item.id === itemId));
}

/** The line with no variant and no modifier, the one the menu's +/− edits directly. */
export function plainLineKey(item: PublicMenuItem): string {
  return lineKey(item, null, []);
}

export function toOrderLines(lines: readonly CartLine[]): SubmitTableOrderRequest['lines'] {
  return lines.map((line) => {
    const note = line.specialInstructions.trim();
    return {
      menuItemId: line.item.id,
      ...(line.variant ? { variantId: line.variant.id } : {}),
      quantity: line.quantity,
      modifiers: line.modifiers.map((choice) => ({ modifierId: choice.modifier.id, quantity: choice.quantity })),
      ...(note ? { specialInstructions: note } : {}),
    };
  });
}

/** An item needs the sheet when there is something to choose before it enters the cart. */
export function needsChoice(item: PublicMenuItem): boolean {
  return item.variants.length > 0 || item.modifiers.length > 0;
}
