package br.com.castel.restaurant.application;

import br.com.castel.restaurant.domain.DiningTable;
import br.com.castel.restaurant.domain.DiningTableHasOpenTabException;
import br.com.castel.restaurant.domain.DiningTableId;
import br.com.castel.restaurant.domain.DiningTableLabelAlreadyUsedException;
import br.com.castel.restaurant.domain.DiningTableNotFoundException;
import br.com.castel.restaurant.domain.DiningTableRepository;
import br.com.castel.restaurant.domain.TabRepository;
import br.com.castel.sharedkernel.CurrentProperty;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registering the dining tables of the property and reading them. */
@Service
public class DiningTableService {

    private final DiningTableRepository diningTables;
    private final TabRepository tabs;
    private final CurrentProperty currentProperty;

    public DiningTableService(
            DiningTableRepository diningTables, TabRepository tabs, CurrentProperty currentProperty) {
        this.diningTables = diningTables;
        this.tabs = tabs;
        this.currentProperty = currentProperty;
    }

    /**
     * @throws DiningTableLabelAlreadyUsedException if the property already has a table under this
     *     label, ignoring letter case
     */
    @Transactional
    public DiningTable create(String label, Integer seats, String area) {
        if (label != null && diningTables.existsByLabel(currentProperty.id(), label.trim())) {
            throw labelAlreadyUsed(label);
        }
        return diningTables.save(DiningTable.create(currentProperty.id(), label, seats, area));
    }

    /**
     * Replaces label, seats and area (decision #7). The label check runs before the table changes:
     * the query would otherwise flush the new label and meet the unique index first.
     *
     * @throws DiningTableNotFoundException if the table does not exist
     * @throws DiningTableLabelAlreadyUsedException if another table of the property has this label
     */
    @Transactional
    public DiningTable redescribe(DiningTableId id, String label, Integer seats, String area) {
        DiningTable diningTable = load(id);
        rejectLabelUsedByAnother(label, id);
        diningTable.redescribe(label, seats, area);
        return diningTables.save(diningTable);
    }

    /**
     * Takes the table off the floor.
     *
     * <p>A table with an {@code OPEN} or {@code CLOSING} tab is refused (decision #8 of task 2.2).
     * Whether a table has an active tab is a rule about the set of tabs, not about the table, so it
     * is checked here and not by the aggregate.
     *
     * @throws DiningTableNotFoundException if the table does not exist
     * @throws DiningTableHasOpenTabException if the table has an active tab
     */
    @Transactional
    public DiningTable deactivate(DiningTableId id) {
        DiningTable diningTable = load(id);
        if (tabs.existsActiveOnDiningTable(id)) {
            throw new DiningTableHasOpenTabException("Dining table " + id.value() + " has an open tab");
        }
        diningTable.deactivate();
        return diningTables.save(diningTable);
    }

    @Transactional
    public DiningTable activate(DiningTableId id) {
        DiningTable diningTable = load(id);
        diningTable.activate();
        return diningTables.save(diningTable);
    }

    /** @throws DiningTableNotFoundException if the table does not exist */
    @Transactional(readOnly = true)
    public DiningTable find(DiningTableId id) {
        return load(id);
    }

    /** The active tables, or all of them when asked, in the order of decision #8. */
    @Transactional(readOnly = true)
    public List<DiningTable> list(boolean includeInactive) {
        return diningTables.findAll(currentProperty.id(), includeInactive).stream()
                .sorted(DiningTable.listingOrder())
                .toList();
    }

    private DiningTable load(DiningTableId id) {
        return diningTables.findById(id)
                .orElseThrow(() -> new DiningTableNotFoundException("No dining table " + id.value()));
    }

    /**
     * Uniqueness is a rule about the set of tables, not about one of them, so it cannot live inside
     * the aggregate. The database holds the same rule in {@code uk_dining_table_label}; this check
     * exists to answer a readable code instead of a constraint violation. The table being
     * redescribed does not compete with itself, so keeping its label, or changing only its letter
     * case, is accepted. A missing label is left for the aggregate, which refuses it with its code.
     */
    private void rejectLabelUsedByAnother(String label, DiningTableId excludedId) {
        if (label != null && diningTables.existsByLabelExcluding(currentProperty.id(), label.trim(), excludedId)) {
            throw labelAlreadyUsed(label);
        }
    }

    private static DiningTableLabelAlreadyUsedException labelAlreadyUsed(String label) {
        return new DiningTableLabelAlreadyUsedException(
                "A dining table labelled '" + label.trim() + "' already exists");
    }
}
