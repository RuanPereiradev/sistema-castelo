package br.com.castel.sharedkernel;

import java.io.Serial;

/**
 * The stored value does not parse as the type it claims to be: {@code value_type = 'INTEGER'} over
 * {@code "many"}, {@code 'TIME'} over {@code "noon"}.
 *
 * <p>Distinct from {@link SettingTypeMismatchException}, which is the caller asking for the wrong
 * type. This one is bad data in the row.
 */
public class MalformedSettingValueException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String CODE = "MALFORMED_SETTING_VALUE";

    public MalformedSettingValueException(String settingKey, SettingValueType valueType) {
        super(CODE, "Setting '" + settingKey + "' does not hold a valid " + valueType + " value");
    }
}
