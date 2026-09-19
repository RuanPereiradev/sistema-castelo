package br.com.castel.sharedkernel;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.math.BigDecimal;

/**
 * Maps {@link Money} to the {@code NUMERIC(12,2)} the convention asks for, and back.
 *
 * <p>{@code autoApply} is on, so every {@code Money} field of every module persists the same way
 * without each entity declaring it. Declared here, with {@code jakarta.persistence} and nothing
 * else, which is what rule A4 allows in the shared kernel.
 */
@Converter(autoApply = true)
public class MoneyConverter implements AttributeConverter<Money, BigDecimal> {

    @Override
    public BigDecimal convertToDatabaseColumn(Money money) {
        return money == null ? null : money.amount();
    }

    @Override
    public Money convertToEntityAttribute(BigDecimal amount) {
        return amount == null ? null : Money.of(amount);
    }
}
