package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.AuditedEntity;
import br.com.castel.sharedkernel.Money;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

/**
 * Something added to an item on request: "borda recheada", "bacon extra", "ponto da carne".
 *
 * <p>A catalog of the property, registered once and offered on many items, each with its own
 * maximum quantity (decision #6). The price lives here, so a modifier costs the same whatever the
 * item or the variant it goes with (decision #7). A price of zero is valid: it is a choice at no
 * cost, like how the meat is cooked.
 *
 * <p>Deactivating a modifier takes it off every item without undoing the links, so activating it
 * again puts it back where it was.
 */
@Entity
@Table(name = "modifier")
public class Modifier extends AuditedEntity {

    /** Maximum length of a modifier name, matching the column. */
    public static final int MAXIMUM_NAME_LENGTH = 100;

    @EmbeddedId
    @AttributeOverride(name = "value", column = @Column(name = "id"))
    private ModifierId id;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "name", nullable = false, length = MAXIMUM_NAME_LENGTH)
    private String name;

    @Column(name = "price", nullable = false)
    private Money price;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected Modifier() {
        // for JPA
    }

    private Modifier(ModifierId id, UUID propertyId, String name, Money price) {
        this.id = id;
        this.propertyId = propertyId;
        this.name = name;
        this.price = price;
        this.active = true;
    }

    /**
     * @throws InvalidModifierNameException if the name is blank or too long for the column
     * @throws InvalidModifierPriceException if the price is missing or negative
     */
    public static Modifier create(UUID propertyId, String name, Money price) {
        Objects.requireNonNull(propertyId, "propertyId");
        return new Modifier(ModifierId.newId(), propertyId, requireValidName(name), requireValidPrice(price));
    }

    public void rename(String newName) {
        this.name = requireValidName(newName);
    }

    /**
     * @throws InvalidModifierPriceException if the price is missing or negative
     */
    public void changePriceTo(Money newPrice) {
        this.price = requireValidPrice(newPrice);
    }

    /** Off every item that offers it, keeping the links for when it comes back. */
    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    private static String requireValidName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidModifierNameException("A modifier needs a name");
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAXIMUM_NAME_LENGTH) {
            throw new InvalidModifierNameException(
                    "A modifier name takes at most " + MAXIMUM_NAME_LENGTH + " characters");
        }
        return trimmed;
    }

    private static Money requireValidPrice(Money price) {
        if (price == null) {
            throw new InvalidModifierPriceException("A modifier needs a price");
        }
        if (price.isNegative()) {
            throw new InvalidModifierPriceException("A modifier price cannot be negative");
        }
        return price;
    }

    public ModifierId id() {
        return id;
    }

    public UUID propertyId() {
        return propertyId;
    }

    public String name() {
        return name;
    }

    public Money price() {
        return price;
    }

    public boolean isActive() {
        return active;
    }
}
