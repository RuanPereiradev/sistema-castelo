package br.com.castel.restaurant.domain;

import br.com.castel.restaurant.api.MenuCategoryId;
import br.com.castel.sharedkernel.AuditedEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

/** A heading of the menu: "Pizzas", "Bebidas", "Sobremesas". */
@Entity
@Table(name = "menu_category")
public class MenuCategory extends AuditedEntity {

    /** Maximum length of a category name, matching the column. */
    public static final int MAXIMUM_NAME_LENGTH = 100;

    @EmbeddedId
    @AttributeOverride(name = "value", column = @Column(name = "id"))
    private MenuCategoryId id;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "name", nullable = false, length = MAXIMUM_NAME_LENGTH)
    private String name;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected MenuCategory() {
        // for JPA
    }

    private MenuCategory(MenuCategoryId id, UUID propertyId, String name, short displayOrder) {
        this.id = id;
        this.propertyId = propertyId;
        this.name = name;
        this.displayOrder = displayOrder;
        this.active = true;
    }

    /**
     * @throws InvalidMenuCategoryNameException if the name is blank or too long for the column
     */
    public static MenuCategory create(UUID propertyId, String name, int displayOrder) {
        Objects.requireNonNull(propertyId, "propertyId");
        return new MenuCategory(MenuCategoryId.newId(), propertyId, requireValidName(name), (short) displayOrder);
    }

    public void rename(String newName) {
        this.name = requireValidName(newName);
    }

    public void moveTo(int newDisplayOrder) {
        this.displayOrder = (short) newDisplayOrder;
    }

    /** A category out of the menu. Its items stay in the database, and stop being listed with it. */
    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    private static String requireValidName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidMenuCategoryNameException("A menu category needs a name");
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAXIMUM_NAME_LENGTH) {
            throw new InvalidMenuCategoryNameException(
                    "A menu category name takes at most " + MAXIMUM_NAME_LENGTH + " characters");
        }
        return trimmed;
    }

    public MenuCategoryId id() {
        return id;
    }

    public UUID propertyId() {
        return propertyId;
    }

    public String name() {
        return name;
    }

    public int displayOrder() {
        return displayOrder;
    }

    public boolean isActive() {
        return active;
    }
}
