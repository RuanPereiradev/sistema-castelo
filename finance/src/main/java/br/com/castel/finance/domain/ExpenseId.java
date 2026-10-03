package br.com.castel.finance.domain;

import br.com.castel.sharedkernel.EntityId;
import jakarta.persistence.Embeddable;
import java.util.UUID;

/** Identity of an {@link Expense}. */
@Embeddable
public record ExpenseId(UUID value) implements EntityId {

    public ExpenseId {
        EntityId.requireValid(value);
    }

    public static ExpenseId newId() {
        return EntityId.newId(ExpenseId::new);
    }

    public static ExpenseId of(String value) {
        return EntityId.of(value, ExpenseId::new);
    }
}
