package br.com.castel.restaurant.application;

import br.com.castel.restaurant.domain.DuplicateModifierNameException;
import br.com.castel.restaurant.domain.Modifier;
import br.com.castel.restaurant.domain.ModifierId;
import br.com.castel.restaurant.domain.ModifierNotFoundException;
import br.com.castel.restaurant.domain.ModifierRepository;
import br.com.castel.sharedkernel.CurrentProperty;
import br.com.castel.sharedkernel.Money;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Maintaining the catalog of modifiers of the property. */
@Service
public class ModifierService {

    private final ModifierRepository modifiers;
    private final CurrentProperty currentProperty;

    public ModifierService(ModifierRepository modifiers, CurrentProperty currentProperty) {
        this.modifiers = modifiers;
        this.currentProperty = currentProperty;
    }

    /**
     * @throws DuplicateModifierNameException if the property already has a modifier under this name
     */
    @Transactional
    public Modifier create(String name, Money price) {
        if (name != null && modifiers.existsByName(currentProperty.id(), name.trim())) {
            throw duplicate(name);
        }
        return modifiers.save(Modifier.create(currentProperty.id(), name, price));
    }

    /**
     * Changes what the request carries: a null name or price stays as it is. Both changes happen in
     * one transaction, so a refused price does not leave a renamed modifier behind.
     *
     * @throws ModifierNotFoundException if the modifier does not exist
     * @throws DuplicateModifierNameException if another modifier of the property has this name
     */
    @Transactional
    public Modifier change(ModifierId id, String newName, Money newPrice) {
        Modifier modifier = load(id);
        if (newName != null) {
            rejectDuplicateExcluding(newName, id);
            modifier.rename(newName);
        }
        if (newPrice != null) {
            modifier.changePriceTo(newPrice);
        }
        return modifiers.save(modifier);
    }

    @Transactional
    public Modifier deactivate(ModifierId id) {
        Modifier modifier = load(id);
        modifier.deactivate();
        return modifiers.save(modifier);
    }

    @Transactional
    public Modifier activate(ModifierId id) {
        Modifier modifier = load(id);
        modifier.activate();
        return modifiers.save(modifier);
    }

    /** The whole catalog, active or not: the administration screen shows it all. */
    @Transactional(readOnly = true)
    public List<Modifier> listAll() {
        return modifiers.findAllOrdered(currentProperty.id());
    }

    private Modifier load(ModifierId id) {
        return modifiers.findById(id).orElseThrow(() -> new ModifierNotFoundException("No modifier " + id.value()));
    }

    /**
     * Uniqueness is a rule about the set of modifiers, not about one of them, so it cannot live
     * inside the aggregate. The database holds the same rule in {@code uk_modifier_name}; this check
     * exists to answer a readable code instead of a constraint violation. The modifier being renamed
     * does not compete with itself, so renaming it to its own name, or changing only its letter case,
     * is accepted.
     */
    private void rejectDuplicateExcluding(String name, ModifierId excludedId) {
        if (modifiers.existsByNameExcluding(currentProperty.id(), name.trim(), excludedId)) {
            throw duplicate(name);
        }
    }

    private static DuplicateModifierNameException duplicate(String name) {
        return new DuplicateModifierNameException("A modifier named '" + name.trim() + "' already exists");
    }
}
