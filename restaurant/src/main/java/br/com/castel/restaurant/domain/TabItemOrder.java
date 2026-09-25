package br.com.castel.restaurant.domain;

import java.util.List;

/**
 * What the waiter asks for when ordering one item on a tab (decision #11): the variant, how many,
 * the weight of a plate sold by weight, the modifiers and the special instructions.
 *
 * <p>A plain carrier. Every rule — which fields an item sold by weight or by unit accepts, the range
 * of each number, the length of the instructions — is checked by {@link Tab#addItem}, so the refusal
 * comes with the code of the rule and in the order the rules are checked.
 *
 * @param variantId the variant ordered, or null
 * @param quantity how many units, or null for 1
 * @param weightGrams the weight of a plate sold by weight, or null
 * @param modifiers the modifiers chosen; null is read as none
 * @param specialInstructions free text for the kitchen, or null
 */
public record TabItemOrder(
        MenuItemVariantId variantId,
        Integer quantity,
        Integer weightGrams,
        List<ModifierChoice> modifiers,
        String specialInstructions) {

    public TabItemOrder {
        modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
    }
}
