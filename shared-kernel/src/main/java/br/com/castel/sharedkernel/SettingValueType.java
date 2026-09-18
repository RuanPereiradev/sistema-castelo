package br.com.castel.sharedkernel;

/**
 * The type a {@code setting} row was stored as, and the only type it can be read back as.
 *
 * <p>Mirrors the {@code value_type} check constraint of the table. A read asking for another type
 * fails through {@link #requireReadableAs(SettingValueType, String)} instead of converting: a rate
 * stored as text and silently read as money is how a wrong price reaches a guest's folio.
 */
public enum SettingValueType {

    STRING,
    INTEGER,
    DECIMAL,
    BOOLEAN,
    TIME;

    /**
     * @throws SettingTypeMismatchException if the stored type is not exactly {@code requested}
     */
    public void requireReadableAs(SettingValueType requested, String settingKey) {
        if (this != requested) {
            throw new SettingTypeMismatchException(settingKey, this, requested);
        }
    }
}
