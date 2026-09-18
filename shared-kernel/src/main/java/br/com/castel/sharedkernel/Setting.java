package br.com.castel.sharedkernel;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/**
 * One parameter of operation: a key, the text it was stored as, and the type that text means.
 *
 * <p>Reading is typed and strict. Each reader asks {@link SettingValueType} to confirm the stored
 * type first, so no conversion ever happens by accident, and only then parses. Parsing lives here,
 * next to the invariant, instead of in a service.
 */
public final class Setting {

    private static final String TRUE = "true";
    private static final String FALSE = "false";

    private final String settingKey;
    private final String settingValue;
    private final SettingValueType valueType;

    private Setting(String settingKey, String settingValue, SettingValueType valueType) {
        this.settingKey = settingKey;
        this.settingValue = settingValue;
        this.valueType = valueType;
    }

    /** A setting as it stands in the row. */
    public static Setting of(String settingKey, String settingValue, SettingValueType valueType) {
        Objects.requireNonNull(settingKey, "settingKey");
        Objects.requireNonNull(settingValue, "settingValue");
        Objects.requireNonNull(valueType, "valueType");
        return new Setting(settingKey, settingValue, valueType);
    }

    /** The same setting holding another value; the key and the type never change. */
    public Setting withValue(String newValue) {
        return of(settingKey, newValue, valueType);
    }

    public String asText() {
        requireType(SettingValueType.STRING);
        return settingValue;
    }

    public int asInteger() {
        requireType(SettingValueType.INTEGER);
        try {
            return Integer.parseInt(settingValue.trim());
        } catch (RuntimeException notAnInteger) {
            throw malformed();
        }
    }

    public Money asMoney() {
        requireType(SettingValueType.DECIMAL);
        return Money.of(settingValue.trim());
    }

    /**
     * A percentage stored in percent points: {@code "10.00"} means ten percent, not one thousand.
     * Stored as {@code DECIMAL}, the same type a monetary setting uses.
     */
    public Percentage asPercentage() {
        requireType(SettingValueType.DECIMAL);
        return Percentage.ofPercent(settingValue.trim());
    }

    /** Only the exact texts {@code true} and {@code false}, in any letter case. */
    public boolean asBoolean() {
        requireType(SettingValueType.BOOLEAN);
        String value = settingValue.trim().toLowerCase(java.util.Locale.ROOT);
        if (TRUE.equals(value)) {
            return true;
        }
        if (FALSE.equals(value)) {
            return false;
        }
        throw malformed();
    }

    /** An ISO local time: {@code 18:30} or {@code 18:30:00}. */
    public LocalTime asLocalTime() {
        requireType(SettingValueType.TIME);
        try {
            return LocalTime.parse(settingValue.trim());
        } catch (DateTimeParseException notATime) {
            throw malformed();
        }
    }

    public String settingKey() {
        return settingKey;
    }

    public SettingValueType valueType() {
        return valueType;
    }

    /** The raw stored text, for whoever persists the row. Never for business reading. */
    public String storedValue() {
        return settingValue;
    }

    private void requireType(SettingValueType requested) {
        valueType.requireReadableAs(requested, settingKey);
    }

    private MalformedSettingValueException malformed() {
        return new MalformedSettingValueException(settingKey, valueType);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Setting setting
                && settingKey.equals(setting.settingKey)
                && settingValue.equals(setting.settingValue)
                && valueType == setting.valueType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(settingKey, settingValue, valueType);
    }

    @Override
    public String toString() {
        return settingKey + "=" + settingValue + " (" + valueType + ")";
    }
}
